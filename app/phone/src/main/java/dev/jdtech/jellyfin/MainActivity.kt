package dev.jdtech.jellyfin

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.casting.CastManager
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.databinding.ActivityMainBinding
import dev.jdtech.jellyfin.dialogs.NavigationDialogFragment
import dev.jdtech.jellyfin.utils.HamburgerButtonAnimation
import dev.jdtech.jellyfin.viewmodels.MainViewModel
import dev.jdtech.jellyfin.viewmodels.CastViewModel
import dev.jdtech.jellyfin.work.SyncWorker
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber
import dev.jdtech.jellyfin.core.R as CoreR

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    // ========== CAST FUNCTIONALITY - INJECT ==========
    @Inject
    lateinit var castManager: CastManager

    private val castViewModel: CastViewModel by viewModels()
    // ==================================================

    // Thêm biến cho hamburger animation
    private lateinit var hamburgerAnimation: HamburgerButtonAnimation

    @Inject
    lateinit var database: ServerDatabaseDao

    @Inject
    lateinit var appPreferences: AppPreferences

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleUserDataSync()
        applyTheme()
        setupActivity()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout(),
            )
            v.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }

        // Thiết lập hiệu ứng hamburger menu
        setupHamburgerMenu()

        // ========== CAST SETUP ==========
        setupCastObservers()
        // ================================
    }

    override fun onResume() {
        super.onResume()
        Timber.d("MainActivity onResume")
    }

    override fun onPause() {
        super.onPause()
        Timber.d("MainActivity onPause")
    }

    private fun setupActivity() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController
        val inflater = navController.navInflater
        val graph = inflater.inflate(R.navigation.app_navigation)

        // Giữ trạng thái Fragment
        configureHomeFragmentStateRetention()

        checkServersEmpty(graph) {
            navController.setGraph(graph, intent.extras)
        }
        checkUser(graph) {
            navController.setGraph(graph, intent.extras)
        }

        // Xử lý sự kiện thay đổi destination
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Hiển thị/ẩn nút hamburger dựa trên màn hình
            binding.fabMenu.visibility = View.VISIBLE  // Hiển thị trên tất cả các màn hình

            // Log chuyển đến destination mới
            Timber.d("Navigation to: ${destination.label}")
        }
    }

    // ========== CAST OBSERVERS ==========
    /**
     * Setup observers cho cast functionality
     */
    private fun setupCastObservers() {
        // Observe cast state để update hamburger icon
        lifecycleScope.launch {
            castManager.isCasting.collect { isCasting ->
                updateHamburgerIconForCasting(isCasting)
            }
        }

        // Start device discovery khi app khởi động để tìm cast devices
        castManager.startDeviceDiscovery()
        Timber.d("Cast device discovery started")
    }

    /**
     * Update hamburger icon dựa trên cast state
     */
    private fun updateHamburgerIconForCasting(isCasting: Boolean) {
        // Simple log only - no complex UI changes
        if (isCasting) {
            Timber.d("Cast is active")
        } else {
            Timber.d("Cast inactive")
        }
    }
    // ===================================

    // Cấu hình hiệu ứng hamburger menu
    private fun setupHamburgerMenu() {
        val fabMenu = binding.fabMenu

        // Đặt màu nền phù hợp với theme
        if (appPreferences.amoledTheme) {
            fabMenu.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.black)
        } else {
            fabMenu.backgroundTintList = ContextCompat.getColorStateList(this, R.color.hamburger_background)
        }

        // Khởi tạo animation
        hamburgerAnimation = HamburgerButtonAnimation(fabMenu)

        // Xử lý sự kiện click
        fabMenu.setOnClickListener {
            if (hamburgerAnimation.isMenuOpen()) {
                // Nếu menu đang mở, đóng nó
                hamburgerAnimation.toggleMenu()
            } else {
                // Nếu menu đang đóng, mở nó và hiển thị dialog
                hamburgerAnimation.toggleMenu()
                openNavigationMenu()
            }
        }
    }

    // Mở dialog menu khi nhấn hamburger
    private fun openNavigationMenu() {
        // Hiển thị dialog với các tùy chọn điều hướng chính
        val dialog = NavigationDialogFragment()

        // Thiết lập callback để reset hamburger animation khi dialog đóng
        dialog.onDialogDismissed = {
            // Reset hamburger animation về trạng thái đóng
            if (::hamburgerAnimation.isInitialized && hamburgerAnimation.isMenuOpen()) {
                hamburgerAnimation.toggleMenu()
            }
        }

        dialog.show(supportFragmentManager, "NavigationMenu")
    }

    // Cấu hình để giữ trạng thái Home Fragment
    private fun configureHomeFragmentStateRetention() {
        // Tối ưu hiệu suất Fragment bằng cách không destroy view khi navigate
        supportFragmentManager.addOnBackStackChangedListener {
            // Giúp giữ nguyên trạng thái của fragment khi quay lại
            Timber.d("Back stack changed: count=${supportFragmentManager.backStackEntryCount}")
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp()
    }

    override fun onBackPressed() {
        // Đóng menu nếu đang mở khi nhấn nút back
        if (::hamburgerAnimation.isInitialized && hamburgerAnimation.isMenuOpen()) {
            hamburgerAnimation.toggleMenu()
        } else {
            super.onBackPressed()
        }
    }

    private fun checkServersEmpty(graph: NavGraph, onServersEmpty: () -> Unit = {}) {
        if (!viewModel.startDestinationChanged) {
            val numOfServers = database.getServersCount()
            if (numOfServers < 1) {
                graph.setStartDestination(R.id.addServerFragment)
                viewModel.startDestinationChanged = true
                onServersEmpty()
            }
        }
    }

    private fun checkUser(graph: NavGraph, onNoUser: () -> Unit = {}) {
        if (!viewModel.startDestinationChanged) {
            appPreferences.currentServer?.let {
                val currentUser = database.getServerCurrentUser(it)
                if (currentUser == null) {
                    graph.setStartDestination(R.id.serverSelectFragment)
                    viewModel.startDestinationChanged = true
                    onNoUser()
                }
            }
        }
    }

    private fun scheduleUserDataSync() {
        val syncWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        NetworkType.CONNECTED,
                    )
                    .build(),
            )
            .build()

        val workManager = WorkManager.getInstance(applicationContext)
        workManager.beginUniqueWork("syncUserData", ExistingWorkPolicy.KEEP, syncWorkRequest)
            .enqueue()
    }

    private fun applyTheme() {
        if (appPreferences.amoledTheme) {
            setTheme(CoreR.style.ThemeOverlay_Findroid_Amoled)
        }
    }

    // Helper method để xác định nếu đang ở chế độ tối
    private fun isNightMode(): Boolean {
        return resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    // ========== CAST LIFECYCLE ==========
    override fun onDestroy() {
        super.onDestroy()
        // Simple cleanup only
        castManager.cleanup()
        Timber.d("MainActivity onDestroy - CastManager cleaned up")
    }
    // =====================================
}