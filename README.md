# rx3-android

Player del XDJ-RX3 (`rbp`, ARM32) corriendo **nativo** en una tablet Android
(Galaxy Tab A7, SM-T500) como APK, sin root ni emulación de CPU, controlado por
un controlador MIDI externo.

> **Firmware de Pioneer**: NO vive en este repo y nunca se sube. El `rbp`, el
> rootfs, la `aes256.key` y los archivos de Pioneer quedan en local / en la
> tablet (ver `.gitignore`). Aquí solo está **nuestro** código.

## Fase 1 (lo que hay ahora)

Banco de pruebas que valida la tubería Android **sin firmware**:

- **Pantalla**: render nativo (C++) de un patrón 1280×800 a un `SurfaceView`
  (el camino que usará `fbshim`).
- **Audio**: tono 440 Hz por Oboe/AAudio (el camino de `audioshim`).
- **MIDI USB**: registra cada mensaje de la controladora (VCI-100 original).
- **Táctil**: registra coordenadas y su mapeo al framebuffer 1280×800.

### Build

Se compila en **GitHub Actions** (el Mac de desarrollo es un Intel 2015 sin SDK
local). Cada push a `main` genera el APK como artefacto en la pestaña *Actions*.

Instalar en la tablet:

```bash
adb install -r rx3-fase1-debug.apk
```

## Siguiente

Fase 2: `rbp` real con los parches de rbpi4b, rootfs vía proot y medición de FPS.
