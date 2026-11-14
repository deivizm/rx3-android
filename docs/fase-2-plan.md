# Fase 2 — `rbp` real en Android (plan)

Base de referencia: **`rb-odroid`** (familia rbpi4b/erhan), el port mejor
documentado. Adaptamos su receta de **chroot en SBC Linux** a **proot dentro de
una APK Android** en la Galaxy Tab A7. Ver [repos-referencia.md](repos-referencia.md).

## Lo que aporta David (su paso, no lo hago yo)

El **árbol de firmware ya descifrado** del XDJ-RX3 v1.20:

```
extracted/
├─ XDJRX3-rootfs/     userland soft-float (de rootfs.cramfs)
├─ XDJRX3/gui/        fontdata + imagedata
├─ XDJRX3-gui/        gui.tar.gz desempaquetado
└─ (rbp dentro de XDJRX3/pdj/rbp)   md5 esperado: 4f2efcfc0c9e3f539289f863acfddcc6
```

Cuando esté, me pasas la ruta y arranco.

## Orden de build (adaptado de rb-odroid `03-port-plan`)

| # | Paso | Reutilizamos | Cambio para Android |
|---|---|---|---|
| 1 | **Firmware assets** | — | Lo aporta David (árbol extraído) |
| 2 | **Parchear `rbp`** | `rbp_patch.py` → `patch-rbp-nopc.py` → `patch-rbp-depth.py` | Sin cambios. Produce `rbp-audio`. `nopc` evita el crash de `getPcController()`. `depth` fija bpp (nativo 16bpp RGB565). |
| 3 | **Shims (ARM32 soft-float)** | `scripts/shims/` (fbshim, tscfake, audioshim, midi_io, ctrlshim, UI…) | Cross-compilar para el chroot. Reescribir los *backends*: fb→SurfaceView, audio→Oboe, touch/MIDI→sockets a Android. |
| 4 | **DirectFB 1.4.16** | `tools/build-directfb` | Cross-compilar para el rootfs (igual que en SBC). |
| 5 | **Ensamblar deploy root** | `build-chroot.sh` (host-side, sin root) | Igual: rootfs + rbp-audio + gui + dfb + shims → tgz. |
| 6 | **Entrar al rootfs** | — | **chroot → proot** (sin root, esquiva W^X). El `rbp` corre NATIVO; proot solo reescribe rutas. |
| 7 | **Pantalla** | `fb_shim.c` | El shim escribe el framebuffer 1280×800 a memoria compartida; la app lo pinta en el SurfaceView (validado en Fase 1, escala a 2000×1200). |
| 8 | **Táctil** | `tscfake.c` + `point_xform.c` | El táctil de Android → socket → `tscfake` (protocolo tsc2007). Deshacer reflejo 1279−x (`mirror_policy`). |
| 9 | **Audio** | `audioshim.c` + `s24pack.c` | 3×S24_LE 44.1k → Oboe/AAudio (validado en Fase 1). |
| 10 | **Controles** | `map_kbd.c` (default), `midi_io.c` | Arrancar con teclado (no necesita controlador). Luego `map_vci100.c` desde el mapeo djay + captura en vivo. |
| 11 | **USB/pendrives** | `usb-watch` + `export.pdb` | Más adelante (Fase 4). |
| 12 | **Launcher** | `start-rb.sh` | Lógica equivalente desde la app (NDK): preparar proot, lanzar `rbp` con `LD_PRELOAD` de los shims. |

> **Clave:** se puede arrancar el `rbp` **solo con teclado** (`EVDEV_MAP=kbd` es
> el default), así que la VCI-100 no bloquea la puesta en marcha. Primero que
> arranque y se vea; luego el mapa del controlador.

## Riesgo nº1 — fluidez

DirectFB dibuja por software + proot añade sobrecarga por syscall. Medir FPS
cuanto antes (`UISHIM_FPS=1`). Palancas si va justo: double-buffer, escalado
eficiente en el SurfaceView, bajar a RGB16, afinar el pacer del fb.

## Parche no-op de `getPcController()` (de rb-odroid)

```
0x31DF64  e30636b0 -> e3a00000   mov r0, #0
0x31DF68  e3403268 -> e12fff1e   bx  lr
```
Lo aplica `patch-rbp-nopc.py`. Evita un deref NULL ~1s tras arrancar.

## Primer hito de la Fase 2

`rbp` parcheado + rootfs montado por proot + `fbshim` → **ver la UI del RX3 en la
tablet** y un número de FPS. Con eso sabremos si el enfoque nativo es fluido.
