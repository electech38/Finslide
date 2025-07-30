package dev.jdtech.jellyfin.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.R
import dev.jdtech.jellyfin.casting.CastManager
import dev.jdtech.jellyfin.databinding.DialogNavigationBinding
import dev.jdtech.jellyfin.viewmodels.CastViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Dialog hiển thị menu điều hướng chính
 */
@AndroidEntryPoint
class NavigationDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogNavigationBinding? = null
    private val binding get() = _binding!!

    // ========== CAST FUNCTIONALITY - INJECT ==========
    @Inject
    lateinit var castManager: CastManager

    private val castViewModel: CastViewModel by viewModels()
    // =================================================

    // Callback để thông báo cho MainActivity khi dialog bị đóng
    var onDialogDismissed: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)

                // Thiết lập để dialog luôn mở rộng hoàn toàn
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isDraggable = true

                // Đặt peek height để dialog hiển thị đầy đủ nội dung
                behavior.peekHeight = it.height
            }
        }

        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogNavigationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupNavigationItems()
        // ========== CAST SETUP ==========
        setupCastObserver()
        // ================================
    }

    override fun onStart() {
        super.onStart()

        // Đảm bảo dialog mở rộng hoàn toàn khi start
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun setupNavigationItems() {
        // Thiết lập các mục điều hướng

        // Home
        binding.homeItem.setOnClickListener {
            navigateTo(R.id.homeFragment)
        }

        // Media/Library
        binding.libraryItem.setOnClickListener {
            navigateTo(R.id.mediaFragment)
        }

        // Favorites
        binding.favoritesItem.setOnClickListener {
            navigateTo(R.id.favoriteFragment)
        }

        // ========== CAST FUNCTIONALITY ==========
        // Screen Mirroring/Cast (nếu có trong layout)
        try {
            binding.layoutCast?.setOnClickListener {
                handleCastClick()
            }
        } catch (e: Exception) {
            // Cast layout không tồn tại trong current layout
            Timber.d("Cast layout not found in navigation dialog")
        }

        // Settings (nếu có trong layout)
        try {
            binding.layoutSettings?.setOnClickListener {
                Timber.d("Settings clicked")
                dismiss()
            }
        } catch (e: Exception) {
            // Settings layout không tồn tại
            Timber.d("Settings layout not found in navigation dialog")
        }
        // ========================================
    }

    // ========== CAST OBSERVER ==========
    /**
     * Setup observer cho cast state
     */
    private fun setupCastObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            castManager.isCasting.collect { isCasting ->
                updateCastUI(isCasting)
            }
        }
    }

    /**
     * Update cast UI dựa trên trạng thái
     */
    private fun updateCastUI(isCasting: Boolean) {
        try {
            if (isCasting) {
                binding.textViewCast?.text = "Stop Casting"  // Changed to show disconnect option
                binding.imageViewCast?.setImageResource(R.drawable.ic_cast_connected)

                // Hiển thị connected device name
                val deviceName = castManager.connectedDevice.value?.name
                if (!deviceName.isNullOrEmpty()) {
                    binding.textViewCastSubtitle?.text = "Connected to $deviceName"
                    binding.textViewCastSubtitle?.visibility = View.VISIBLE
                } else {
                    binding.textViewCastSubtitle?.text = "Casting active"
                    binding.textViewCastSubtitle?.visibility = View.VISIBLE
                }
            } else {
                binding.textViewCast?.text = getString(R.string.screen_mirroring)
                binding.imageViewCast?.setImageResource(R.drawable.ic_cast)
                binding.textViewCastSubtitle?.visibility = View.GONE
            }
        } catch (e: Exception) {
            // Cast UI elements không có trong layout hoặc bị ẩn
            Timber.d("Cast UI elements not found or hidden in layout: ${e.message}")
        }
    }

    /**
     * Xử lý cast click - Simple approach
     */
    private fun handleCastClick() {
        if (castManager.isCasting.value) {
            // Nếu đang casting -> disconnect
            Timber.d("Stopping current cast session")
            castManager.disconnect()
            dismiss()
        } else {
            // Nếu không casting -> mở Cast Settings để user chọn device
            Timber.d("Opening Android Cast Settings")
            openCastSettings()
        }
    }

    /**
     * Mở Android Cast Settings
     */
    private fun openCastSettings() {
        try {
            // Mở Android's built-in Cast/Display settings
            val intent = Intent("android.settings.CAST_SETTINGS")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)

            dismiss()

        } catch (e: Exception) {
            // Fallback: mở Display settings
            try {
                val displayIntent = Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS)
                displayIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(displayIntent)
                dismiss()
            } catch (e2: Exception) {
                Timber.e(e2, "Failed to open any cast settings")

                // Last fallback: show simple dialog with instruction
                showCastInstructionDialog()
            }
        }
    }

    /**
     * Hiển thị dialog hướng dẫn cast (fallback khi không mở được settings)
     */
    private fun showCastInstructionDialog() {
        try {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Screen Mirroring")
                .setMessage("To cast your screen:\n\n1. Open device Settings\n2. Go to Display settings\n3. Look for Cast, Screen Mirroring, or Wireless Display\n4. Select your casting device")
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    dismiss()
                }
                .show()
        } catch (e: Exception) {
            Timber.e(e, "Failed to show cast instruction dialog")
            dismiss()
        }
    }

    private fun navigateTo(destinationId: Int) {
        Timber.d("Navigating to $destinationId")

        try {
            // Tìm controller hiện tại
            val navController = findNavController()

            // Đóng dialog trước khi navigate
            dismiss()

            // Kiểm tra nếu đã ở destination đích
            if (navController.currentDestination?.id != destinationId) {
                // Thực hiện navigation
                navController.navigate(destinationId)
            }
        } catch (e: Exception) {
            Timber.e(e, "Navigation error")
            // Vẫn đóng dialog ngay cả khi navigation thất bại
            dismiss()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // Thông báo cho MainActivity để reset hamburger icon
        onDialogDismissed?.invoke()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        // Thông báo cho MainActivity để reset hamburger icon
        onDialogDismissed?.invoke()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}