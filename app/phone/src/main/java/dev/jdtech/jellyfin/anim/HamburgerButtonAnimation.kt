package dev.jdtech.jellyfin.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.google.android.material.floatingactionbutton.FloatingActionButton
import timber.log.Timber

/**
 * Lớp cung cấp hiệu ứng cho nút hamburger menu
 */
class HamburgerButtonAnimation(private val fabMenu: FloatingActionButton) {

    private var isMenuOpen = false
    private var currentAnimator: AnimatorSet? = null
    
    // Danh sách các FAB phụ (tùy chọn)
    private val subMenuItems = mutableListOf<View>()
    
    /**
     * Thêm item phụ (tùy chọn)
     */
    fun addSubMenuItem(menuItem: View) {
        subMenuItems.add(menuItem)
        // Ẩn ban đầu
        menuItem.visibility = View.GONE
        menuItem.alpha = 0f
        menuItem.scaleX = 0f
        menuItem.scaleY = 0f
    }

    /**
     * Toggle trạng thái menu (mở/đóng)
     */
    fun toggleMenu() {
        if (currentAnimator?.isRunning == true) {
            currentAnimator?.cancel()
        }

        isMenuOpen = !isMenuOpen
        
        if (isMenuOpen) {
            openMenu()
        } else {
            closeMenu()
        }
    }
    
    /**
     * Buộc đóng menu mà không cần toggle
     */
    fun forceClose() {
        if (currentAnimator?.isRunning == true) {
            currentAnimator?.cancel()
        }
        
        if (isMenuOpen) {
            isMenuOpen = false
            closeMenu()
        }
    }

    /**
     * Hiệu ứng mở menu
     */
    private fun openMenu() {
        Timber.d("Opening hamburger menu")
        
        // Hiệu ứng xoay nút hamburger
        val rotateAnimator = ObjectAnimator.ofFloat(fabMenu, View.ROTATION, 0f, 90f)
        rotateAnimator.duration = 300
        rotateAnimator.interpolator = OvershootInterpolator()
        
        val animatorSet = AnimatorSet()
        animatorSet.play(rotateAnimator)
        
        // Hiệu ứng cho các item phụ (nếu có)
        val subAnimators = mutableListOf<Animator>()
        
        for ((index, item) in subMenuItems.withIndex()) {
            item.visibility = View.VISIBLE
            
            val scaleX = ObjectAnimator.ofFloat(item, View.SCALE_X, 0f, 1f)
            val scaleY = ObjectAnimator.ofFloat(item, View.SCALE_Y, 0f, 1f)
            val alpha = ObjectAnimator.ofFloat(item, View.ALPHA, 0f, 1f)
            
            val subAnimatorSet = AnimatorSet()
            subAnimatorSet.playTogether(scaleX, scaleY, alpha)
            subAnimatorSet.startDelay = (index * 50).toLong()
            subAnimatorSet.duration = 300
            subAnimatorSet.interpolator = AccelerateDecelerateInterpolator()
            
            subAnimators.add(subAnimatorSet)
        }
        
        if (subAnimators.isNotEmpty()) {
            animatorSet.playTogether(rotateAnimator, *subAnimators.toTypedArray())
        }
        
        currentAnimator = animatorSet
        animatorSet.start()
    }

    /**
     * Hiệu ứng đóng menu
     */
    private fun closeMenu() {
        Timber.d("Closing hamburger menu")
        
        // Hiệu ứng xoay nút hamburger
        val rotateAnimator = ObjectAnimator.ofFloat(fabMenu, View.ROTATION, 90f, 0f)
        rotateAnimator.duration = 300
        
        val animatorSet = AnimatorSet()
        animatorSet.play(rotateAnimator)
        
        // Hiệu ứng cho các item phụ (nếu có)
        val subAnimators = mutableListOf<Animator>()
        
        for ((index, item) in subMenuItems.withIndex().reversed()) {
            val scaleX = ObjectAnimator.ofFloat(item, View.SCALE_X, 1f, 0f)
            val scaleY = ObjectAnimator.ofFloat(item, View.SCALE_Y, 1f, 0f)
            val alpha = ObjectAnimator.ofFloat(item, View.ALPHA, 1f, 0f)
            
            val subAnimatorSet = AnimatorSet()
            subAnimatorSet.playTogether(scaleX, scaleY, alpha)
            subAnimatorSet.startDelay = (index * 50).toLong()
            subAnimatorSet.duration = 300
            
            subAnimatorSet.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    item.visibility = View.GONE
                }
            })
            
            subAnimators.add(subAnimatorSet)
        }
        
        if (subAnimators.isNotEmpty()) {
            animatorSet.playTogether(rotateAnimator, *subAnimators.toTypedArray())
        }
        
        currentAnimator = animatorSet
        animatorSet.start()
    }
    
    /**
     * Kiểm tra menu có đang mở không
     */
    fun isMenuOpen(): Boolean = isMenuOpen
}