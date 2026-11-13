# Handoff: player XDJ-RX3 nativo en tablet Android

Contexto traído de una conversación en la app de Claude (13-nov-2026). Léelo entero antes de empezar.

## Objetivo de David

Ejecutar el **player real del XDJ-RX3** (`rbp`, ARM32) **directamente en su Samsung Galaxy Tab A9** (Android, MediaTek Helio G99, pantalla 1340×800), **sin emulación**, como **app Android (APK)**, usando **otro controlador** (no el hardware RX3).

- **Solo RX3.** Nada del CDJ-3000X: David lo descartó expresamente.
- **Firmware:** David lo aportará más tarde, ya extraído y por su cuenta: `XDJRX3-rootfs/`, `XDJRX3/gui/`, `XDJRX3-gui/` y el `rbp` original.
  - md5 del `rbp` v1.20 original: `4f2efcfc0c9e3f539289f863acfddcc6`.
  - **No ayudamos a descifrar el `.UPD` ni a conseguir la clave** (código GPL de Pioneer, `fetch_key.py`, `recover-firmware.py`, `rx3_fetch.sh`…). Ya se le dijo varias veces. Trabajamos a partir del árbol ya extraído.
- **Entorno de trabajo:** MacBook Pro Intel 2015 con macOS Monterey. Hay `~/.android` y `~/.gradle`, así que probablemente tiene Android SDK y Gradle.

## Pendiente de David (pedírselo al empezar)

1. Salida de `getprop ro.product.cpu.abilist32` (por `adb shell` o Termux).
   - Si sale `armeabi-v7a`: el plan nativo es viable.
   - Si sale vacío: el `rbp` solo podría ir emulado (qemu-user). Hablarlo con él antes de seguir.
2. Qué controlador usará: DDJ-T1, FLX4, FLX6…
   - El repo `rx3-player-port` en memoria menciona su DDJ-T1.
   - Los proyectos existentes solo traen mapeos de FLX4, FLX6, SC Live 4 y teclado.
3. Tablet con depuración USB activada, y `adb` funcionando en el Mac.

## Arquitectura acordada

Una sola APK, sin root y sin Termux:

1. **Chroot y lanzamiento del `rbp`.**
   - La APK lleva **proot** compilado para ARM en `jniLibs` (así esquiva la restricción W^X de Android 10+).
   - La primera vez, el usuario elige la carpeta del firmware y la app lo copia a su almacenamiento interno.
   - El `rbp` (ARM32 soft-float, glibc 2.13) corre **nativo**: proot solo reescribe rutas, no traduce instrucciones.
2. **Shims `LD_PRELOAD` con backends Android.** Se parte de rbpi4b y de Route B:
   - **Pantalla:** `fbshim` escribe en memoria compartida (`ashmem` o un archivo mapeado) y la app la pinta en un `SurfaceView` a 1280×800. Usar RGB32, que debe coincidir con `RB_FB_LIE_BPP` y el parche de profundidad.
   - **Táctil:** los eventos de Android van por socket al `tscfake` (el protocolo `tsc2007`). Hay que deshacer el reflejo `1279 - x` que aplica el `rbp`.
   - **Audio:** `audioshim` envía PCM por un buffer en anillo a AAudio/Oboe en vez de ALSA. Salida por altavoces o por la tarjeta USB del controlador.
   - **MIDI:** el `MidiManager` de Android (USB) envía por socket al `midi_io`/`knobshim`, con un mapa por controlador.
   - **Dispositivos falsos** (FIFOs `subucom_*`, `gpiodrv`, `tsc2007_2-0048`, `/dev/mem` bloqueado): crearlos como hace `route-b.sh`.
3. **Botones del RX3 que no tenga el controlador:** usar `uishim-rx3`, que los dibuja dentro de los frames y funciona con renderizado por software (Route B). El controlador en pantalla de rb4tab sirve de inspiración.
4. **Alternativa para el MVP:** el protocolo TCP 4480 de nanocodium/xdj-rx3-emu (tiles RGBA, PCM S16, teclas, táctil, eventos USB). Es más sencillo que la memoria compartida. Ojo: está hecho sobre la v1.19.

## Fases

1. **APK con un "player falso"**, sin firmware: pinta un patrón de prueba a 1280×800, reproduce un tono por AAudio, recibe táctil y MIDI USB, y lo registra todo. Así se valida la tubería Android.
2. **`rbp` real:** con el firmware de David, aplicar los parches de rbpi4b (`tools/patch-rbp/rbp_patch.py`, `patch-rbp-nopc.py`, `patch-rbp-depth.py`), montar el rootfs y hacer que arranque y se vea en la app. Medir FPS (`UISHIM_FPS=1`).
3. **Audio real y mapeo del controlador de David.**
4. **`uishim` (botones táctiles), USB/pendrives (`export.pdb`) y rendimiento.**

Riesgo principal: la fluidez. DirectFB dibuja por software y proot añade sobrecarga en las llamadas al sistema. La fase 2 debe medirlo pronto.

## Repos analizados

| Repo | Para qué sirve aquí |
|---|---|
| salesforcedoc/rbpi4b | **Base.** `rbp` v1.20 en Pi 4: shims (`fbshim`, `knobshim`, `audioshim`, `netshim`), parches del `rbp`, md5, noVNC (la entrada por VNC no está implementada) |
| rayocta303/RBP-Amlogic-S905X | Fork de rbpi4b para Amlogic; shims muy completos y mapeo FLX6 |
| fabiodj2/Orangepi4-RX3 | Fork de rbpi4b para RK3399; `docs/04-firmware-assets.md`: estructura del firmware y patrones de parcheo |
| salesforcedoc/full-touch-ui-xdj-rx3 | **Route B** (`route-b.sh`: chroot con kernel propio y DirectFB por software) y `uishim-rx3` |
| wilfredilagan/full-touch-ui-xdj-rx3 | Original del anterior (Wandboard i.MX6 con kernel de Pioneer); tabla de botones del RX3 y mapeo FLX4 verificado |
| nanocodium/xdj-rx3-emu | `rbp` v1.19 en WSL con proot + qemu-arm, sin root; bridge TCP 4480 documentado; `g2d-shim` por software |
| Hex4np/rb4tab (Erhan) | CDJ-3000X nativo en una tablet con postmarketOS (no Android); ideas de controlador en pantalla, jog, LEDs, teclado y USB |
| nzoschke/xdj-emu | RX3 emulado completo en QEMU; parches 22/23 de qemu-user (cpuinfo y ALSA ARM32), útiles como plan B si no hay 32 bits |
| thomas-alrek/xdj-rx-tools | Para el XDJ-RX original (otro equipo); no aplica, salvo la idea del panel web |
| erhan-/a64port | Recompilador AArch64 → C; no aplica (el `rbp` del RX3 es ARM32) |

## Cómo empezar en Claude Code

1. Clonar `salesforcedoc/rbpi4b` y `salesforcedoc/full-touch-ui-xdj-rx3` como referencia.
2. Pedir a David los datos pendientes de arriba.
3. Arrancar la fase 1: proyecto Android (Kotlin, minSdk 29 o la que encaje con la Tab A9) con `SurfaceView`, AAudio/Oboe, `MidiManager` y el player falso nativo (NDK).

Idioma con David: español.
