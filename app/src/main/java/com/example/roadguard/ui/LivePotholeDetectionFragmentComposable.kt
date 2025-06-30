package com.example.roadguard.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun LivePotholeDetectionFragmentComposable() {
    AndroidView(
        factory = { context ->
            androidx.fragment.app.FragmentContainerView(context).apply {
                id = android.R.id.content
                val fragment = LivePotholeDetectionFragment()
                val fragmentManager = (context as androidx.fragment.app.FragmentActivity).supportFragmentManager
                fragmentManager.beginTransaction()
                    .replace(android.R.id.content, fragment)
                    .commitAllowingStateLoss()
            }
        }
    )
}
