package com.faceplugin.facesdk_plugin.engine

object FaceEngineProvider {
  @Volatile private var engine: FaceEngine = NoopFaceEngine()
  fun get(): FaceEngine = engine
  fun set(e: FaceEngine) { engine = e }
}
