package com.sustain.step.ui.base

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import androidx.viewbinding.ViewBinding

abstract class BaseDialogFragment<VB : ViewBinding>() : DialogFragment(){

    private var _binding: VB? = null
    protected val binding get() = _binding!!

    protected abstract val width: Int
    protected abstract val height: Int

    protected open val edgeToEdge: Boolean = false

    abstract fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): VB
    abstract fun onViewCreated()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        dialog?.apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setDimAmount(0.75f)
            }
        }
        setStyle(STYLE_NO_FRAME, android.R.style.Theme)
        _binding = getViewBinding(inflater, container)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (edgeToEdge) {
            applyEdgeToEdgeInsets(view)
        }
        onViewCreated()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(width, height)
            if (edgeToEdge) {
                WindowCompat.setDecorFitsSystemWindows(this, false)
                statusBarColor = Color.TRANSPARENT
                navigationBarColor = Color.TRANSPARENT
                clearFlags(
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
                )
            }
        }
    }

    private fun applyEdgeToEdgeInsets(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}