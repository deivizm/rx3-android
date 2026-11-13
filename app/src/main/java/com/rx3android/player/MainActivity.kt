package com.rx3android.player

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Fase 1: valida la tubería Android completa sin firmware.
 *  - Patrón de prueba 1280x800 por render nativo (futuro fbshim)
 *  - Tono 440 Hz por Oboe/AAudio (futuro audioshim)
 *  - MIDI USB de la VCI-100 registrado en pantalla y Logcat
 *  - Táctil registrado, mostrando también su mapeo al fb 1280x800
 */
class MainActivity : AppCompatActivity() {

    private lateinit var surfaceView: FakePlayerSurfaceView
    private lateinit var overlay: TextView
    private lateinit var midi: MidiEngine

    private val uiHandler = Handler(Looper.getMainLooper())
    private val logLines = ArrayDeque<String>()
    private val maxLines = 16

    // Resolución del framebuffer nativo (la del RX3).
    private val fbWidth = 1280
    private val fbHeight = 800

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        val root = FrameLayout(this)

        surfaceView = FakePlayerSurfaceView(this)
        root.addView(
            surfaceView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        overlay = TextView(this).apply {
            setBackgroundColor(Color.argb(160, 0, 0, 0))
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(16, 16, 16, 16)
        }
        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { gravity = Gravity.START or Gravity.TOP }
        )

        setContentView(root)

        log("== RX3 Fase 1 — banco de pruebas ==")
        log("Toca la pantalla y mueve controles de la VCI-100.")

        midi = MidiEngine(this) { line -> log(line) }
        midi.start()

        startFpsTicker()
    }

    override fun onResume() {
        super.onResume()
        NativeBridge.startTone()
    }

    override fun onPause() {
        super.onPause()
        NativeBridge.stopTone()
    }

    override fun onDestroy() {
        super.onDestroy()
        midi.stop()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN || ev.action == MotionEvent.ACTION_MOVE) {
            val vw = surfaceView.width.coerceAtLeast(1)
            val vh = surfaceView.height.coerceAtLeast(1)
            val fx = (ev.x / vw * fbWidth).toInt().coerceIn(0, fbWidth - 1)
            val fy = (ev.y / vh * fbHeight).toInt().coerceIn(0, fbHeight - 1)
            // El rbp aplica un reflejo 1279 - x; lo mostramos para tenerlo presente.
            val fxMirror = (fbWidth - 1) - fx
            val tag = if (ev.action == MotionEvent.ACTION_DOWN) "DOWN" else "MOVE"
            log("TOUCH $tag  pantalla=(${ev.x.toInt()},${ev.y.toInt()})  fb=($fx,$fy)  fb_mirror_x=$fxMirror")
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun startFpsTicker() {
        uiHandler.postDelayed(object : Runnable {
            override fun run() {
                render()
                uiHandler.postDelayed(this, 500)
            }
        }, 500)
    }

    private fun log(line: String) {
        Log.i(TAG, line)
        if (logLines.size >= maxLines) logLines.removeFirst()
        logLines.addLast(line)
        render()
    }

    private fun render() {
        val fps = surfaceView.lastFps
        val header = "FPS render ≈ %.1f   (fb 1280x800 -> panel %dx%d)\n".format(
            fps, surfaceView.width, surfaceView.height
        )
        overlay.text = header + "\n" + logLines.joinToString("\n")
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    companion object {
        private const val TAG = "rx3main"
    }
}
