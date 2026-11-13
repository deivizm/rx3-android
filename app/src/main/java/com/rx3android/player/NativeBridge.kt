package com.rx3android.player

import android.view.Surface

/**
 * Puente con librx3native.so (C++). En Fase 1 es el "player falso":
 * render del patrón y tono de prueba. En Fase 2+ aquí colgarán el
 * arranque del rbp y los shims.
 */
object NativeBridge {
    init {
        System.loadLibrary("rx3native")
    }

    external fun setSurface(surface: Surface?)
    external fun clearSurface()
    external fun renderFrame(frame: Long)

    external fun startTone()
    external fun stopTone()
}
