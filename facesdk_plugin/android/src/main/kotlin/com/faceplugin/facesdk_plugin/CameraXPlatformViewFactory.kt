package com.faceplugin.facesdk_plugin

import android.app.Activity
import android.content.Context
import androidx.lifecycle.LifecycleOwner
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

class CameraXPlatformViewFactory(
    private val messenger: BinaryMessenger,
    private val activityProvider: () -> Activity,
    private val lifecycleProvider: () -> LifecycleOwner,
    private val processorProvider: () -> FrameProcessor
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {

    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        return CameraXPlatformView(
            activityProvider(),
            lifecycleProvider(),
            processorProvider()
        )
    }
}
