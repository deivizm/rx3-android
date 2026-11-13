package com.rx3android.player

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * SurfaceView que delega el pintado al código nativo. Un hilo de render llama
 * a NativeBridge.renderFrame() a ~60 fps. Es el mismo camino que usará fbshim:
 * el nativo escribe un framebuffer 1280x800 y el SurfaceView lo escala al panel.
 */
class FakePlayerSurfaceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    @Volatile private var running = false
    private var renderThread: Thread? = null

    @Volatile var lastFps: Double = 0.0
        private set

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        NativeBridge.setSurface(h.surface)
        running = true
        renderThread = Thread({ renderLoop() }, "rx3-render").also { it.start() }
        Log.i(TAG, "surfaceCreated: hilo de render arrancado")
    }

    override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.i(TAG, "surfaceChanged: panel ${width}x$height (fb nativo 1280x800)")
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        running = false
        try { renderThread?.join(500) } catch (_: InterruptedException) {}
        renderThread = null
        NativeBridge.clearSurface()
        Log.i(TAG, "surfaceDestroyed: render parado")
    }

    private fun renderLoop() {
        var frame = 0L
        var fpsWindowStart = System.nanoTime()
        var framesInWindow = 0
        while (running) {
            val t0 = System.nanoTime()
            NativeBridge.renderFrame(frame)
            frame++
            framesInWindow++

            val now = System.nanoTime()
            if (now - fpsWindowStart >= 1_000_000_000L) {
                lastFps = framesInWindow * 1e9 / (now - fpsWindowStart)
                framesInWindow = 0
                fpsWindowStart = now
            }

            // Cadencia ~60 fps. unlockAndPost ya bloquea con vsync, pero
            // dejamos un respiro para no quemar CPU si el buffer va suelto.
            val elapsedMs = (System.nanoTime() - t0) / 1_000_000
            val sleep = 16 - elapsedMs
            if (sleep > 0) {
                try { Thread.sleep(sleep) } catch (_: InterruptedException) { break }
            }
        }
    }

    companion object {
        private const val TAG = "rx3view"
    }
}
