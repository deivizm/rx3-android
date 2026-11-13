package com.rx3android.player

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Abre todos los dispositivos MIDI (la VCI-100 entra como USB class-compliant)
 * y registra cada mensaje recibido. En Fase 3 esto alimentará el mapa de la
 * controladora hacia las funciones del RX3; de momento solo lo loguea.
 */
class MidiEngine(
    private val context: Context,
    private val onLog: (String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var midiManager: MidiManager? = null
    private val openPorts = mutableListOf<MidiOutputPort>()
    private val openDevices = mutableListOf<MidiDevice>()

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) {
            log("MIDI conectado: ${describe(info)}")
            openDevice(info)
        }

        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            log("MIDI desconectado: ${describe(info)}")
        }
    }

    fun start() {
        val mm = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
        if (mm == null) {
            log("Este dispositivo no expone servicio MIDI")
            return
        }
        midiManager = mm
        @Suppress("DEPRECATION")
        mm.registerDeviceCallback(deviceCallback, handler)
        @Suppress("DEPRECATION")
        val devices = mm.devices
        if (devices.isEmpty()) {
            log("No hay dispositivos MIDI todavía. Enchufa la VCI-100 por USB-OTG.")
        }
        for (info in devices) {
            log("MIDI presente: ${describe(info)}")
            openDevice(info)
        }
    }

    fun stop() {
        midiManager?.unregisterDeviceCallback(deviceCallback)
        openPorts.forEach { runCatching { it.close() } }
        openDevices.forEach { runCatching { it.close() } }
        openPorts.clear()
        openDevices.clear()
    }

    private fun openDevice(info: MidiDeviceInfo) {
        val mm = midiManager ?: return
        mm.openDevice(info, { device ->
            if (device == null) {
                log("No se pudo abrir ${describe(info)}")
                return@openDevice
            }
            openDevices.add(device)
            // Abrimos los puertos de SALIDA del dispositivo = su MIDI OUT =
            // lo que la controladora nos manda cuando tocas algo.
            val outputs = info.ports.filter { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }
            if (outputs.isEmpty()) {
                log("${describe(info)} no tiene puertos de salida MIDI")
            }
            for (port in outputs) {
                val outPort = device.openOutputPort(port.portNumber)
                if (outPort == null) {
                    log("Puerto ${port.portNumber} no se pudo abrir")
                    continue
                }
                outPort.connect(LoggingReceiver(describe(info)))
                openPorts.add(outPort)
                log("Escuchando puerto ${port.portNumber} de ${describe(info)}")
            }
        }, handler)
    }

    private inner class LoggingReceiver(private val source: String) : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            val hex = StringBuilder()
            for (i in offset until offset + count) {
                hex.append(String.format("%02X ", msg[i]))
            }
            val decoded = decode(msg, offset, count)
            val line = "MIDI [$source] ${hex.toString().trim()}  $decoded"
            Log.i(TAG, line)
            handler.post { onLog(line) }
        }
    }

    private fun decode(msg: ByteArray, offset: Int, count: Int): String {
        if (count < 1) return ""
        val status = msg[offset].toInt() and 0xFF
        val type = status and 0xF0
        val ch = (status and 0x0F) + 1
        val d1 = if (count > 1) msg[offset + 1].toInt() and 0xFF else -1
        val d2 = if (count > 2) msg[offset + 2].toInt() and 0xFF else -1
        return when (type) {
            0x90 -> if (d2 > 0) "NoteOn  ch$ch n=$d1 v=$d2" else "NoteOff ch$ch n=$d1"
            0x80 -> "NoteOff ch$ch n=$d1 v=$d2"
            0xB0 -> "CC      ch$ch cc=$d1 val=$d2"
            0xE0 -> "PitchB  ch$ch"
            else -> ""
        }
    }

    private fun describe(info: MidiDeviceInfo): String {
        val props = info.properties
        val name = props.getString(MidiDeviceInfo.PROPERTY_NAME)
            ?: props.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
            ?: "desconocido"
        return name
    }

    companion object {
        private const val TAG = "rx3midi"
    }
}
