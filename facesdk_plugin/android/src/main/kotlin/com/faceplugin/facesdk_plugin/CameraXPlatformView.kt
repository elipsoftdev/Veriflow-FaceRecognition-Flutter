package com.faceplugin.facesdk_plugin

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import io.flutter.plugin.platform.PlatformView
import androidx.lifecycle.LifecycleOwner

class CameraXPlatformView(
    activity: Activity,
    private val lifecycleOwner: LifecycleOwner,
    private val frameProcessor: FrameProcessor
) : PlatformView {

    private val container = FrameLayout(activity)
    private val controller = CameraXController(activity, lifecycleOwner, frameProcessor)

    init {
        val preview = controller.createPreviewView()
        container.addView(
            preview,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        controller.startCamera()
    }

    override fun getView(): View = container

    override fun dispose() {
        controller.stopCamera()
        container.removeAllViews()
    }
}
