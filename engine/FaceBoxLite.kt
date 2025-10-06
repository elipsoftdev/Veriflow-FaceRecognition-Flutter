package com.faceplugin.facesdk_plugin.engine

data class FaceBoxLite(
  val x1:Int, val y1:Int, val x2:Int, val y2:Int,
  val yaw:Float = 0f,   // izquierda(-) / derecha(+)
  val roll:Float = 0f,  // inclinación cabeza
  val pitch:Float = 0f, // arriba(+) / abajo(-)
  val livenessScore:Float = 0f,               // 0..1 (pasivo/activo)
  val livenessEvents: List<String> = emptyList() // ["blink","left","right","mouth"]
)
