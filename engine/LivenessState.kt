package com.faceplugin.facesdk_plugin.engine

/**
 * Registra eventos en una ventana corta y decide si el usuario pasó liveness activo.
 * Secuencia por defecto (level>=1): blink + left + right + mouth (en cualquier orden, dentro de ~5s).
 */
class LivenessState(
  private val level:Int = 1,
  private val windowMs: Long = 5000
) {
  private val hits = linkedMapOf(
    "blink" to 0L,
    "left" to 0L,
    "right" to 0L,
    "mouth" to 0L
  )

  fun register(event:String, ts:Long = System.currentTimeMillis()) {
    if (event in hits.keys) hits[event] = ts
  }

  fun passed(now:Long = System.currentTimeMillis()): Boolean {
    if (level <= 0) return true
    val ok = hits.values.all { it != 0L && now - it <= windowMs }
    return ok
  }

  fun eventsWithin(now:Long = System.currentTimeMillis()): List<String> =
    hits.filter { it.value != 0L && now - it.value <= windowMs }.keys.toList()

  fun reset() { hits.keys.forEach { hits[it] = 0L } }
}
