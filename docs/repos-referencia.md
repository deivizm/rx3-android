# Repos de referencia — XDJ-RX3 nativo en Android

Barrido de GitHub (nov. 2026). Ordenado por utilidad para **nuestro** proyecto:
correr el `rbp` (ARM32) nativo en la Galaxy Tab A7 como APK. Estrellas a fecha
del barrido.

> El firmware de Pioneer no vive en este repo ni se redistribuye. Estas son
> referencias de código/documentación de terceros.

## 🟢 Base del port (núcleo)

| Repo | ★ | Para qué |
|---|---|---|
| [salesforcedoc/rbpi4b](https://github.com/salesforcedoc/rbpi4b) | 85 | **Base.** rbp en Pi4, shims (`fbshim`, `audioshim`, `knobshim`, `netshim`), parches (`tools/patch-rbp/`), md5. |
| [salesforcedoc/full-touch-ui-xdj-rx3](https://github.com/salesforcedoc/full-touch-ui-xdj-rx3) | 1 | **Route B** (`route-b.sh`: chroot software + DirectFB), `uishim-rx3`. |
| [rayocta303/RBP-Amlogic-S905X](https://github.com/rayocta303/RBP-Amlogic-S905X) | 1 | Fork Amlogic; shims muy completos y mapeo FLX6. |
| [fabiodj2/Orangepi4-RX3](https://github.com/fabiodj2/Orangepi4-RX3) | 2 | Fork RK3399; `docs/04-firmware-assets.md` (estructura de firmware y patrones de parcheo). |
| [wilfredilagan/full-touch-ui-xdj-rx3](https://github.com/wilfredilagan/full-touch-ui-xdj-rx3) | 17 | Original; tabla de botones del RX3 + mapeo FLX4 verificado. |

## 🔵 Muy relevantes

| Repo | ★ | Por qué |
|---|---|---|
| [kydzuwu/rx3-ddj-sb3](https://github.com/kydzuwu/rx3-ddj-sb3) | 1 | Casi nuestro caso: RX3 + controlador barato (DDJ-SB3). **Traduce MIDI SB3 → FLX4** que el player espera. Corre en QEMU (Apple Silicon); la idea de traducción MIDI aplica a la VCI-100. |
| [DRAGON-EX/xdj-rx3-linux-audio-driver](https://github.com/DRAGON-EX/xdj-rx3-linux-audio-driver) | 2 | Driver de audio RX3 reverse-engineered (24-bit, 4 canales, ALSA). Referencia para `audioshim` (Fase 3). |
| [nanocodium/xdj-rx3-emu](https://github.com/nanocodium/xdj-rx3-emu) | 2 | proot+qemu sin root; **bridge TCP 4480** (alternativa MVP). Sobre v1.19. |
| [nzoschke/xdj-emu](https://github.com/nzoschke/xdj-emu) | 3 | Emulador QEMU del RX3; parches qemu-user (plan B si falla lo nativo). |
| [Stijn-Jacobs/cdj-nxs2-qemu](https://github.com/Stijn-Jacobs/cdj-nxs2-qemu) | 89 | Emulador CDJ/XDJ en QEMU, pulido. Referencia general. |
| [mutlisensor/Rx3-flx4](https://github.com/mutlisensor/Rx3-flx4) | 17 | Standalone en RPi5 + FLX4; cross-compat del firmware. |
| [alex-j-butler/rb-flx10-standalone](https://github.com/alex-j-butler/rb-flx10-standalone) | 0 | FLX10 standalone con RPi + firmware RX3. |
| [Hex4np/rb4tab](https://github.com/Hex4np/rb4tab) | 0 | CDJ-3000X en tablet (postmarketOS); ideas de UI/jog/LEDs/USB en tablet. |

## 🟣 Mods para hardware real (referencia de hooking `LD_PRELOAD`)

Corren sobre el RX3 físico, pero enseñan cómo enganchan funciones del `rbp`:
[Tratosca/rx3-toolkit](https://github.com/Tratosca/rx3-toolkit) (149★) ·
[Dj-ventotto/rx3-mod-ventotto](https://github.com/Dj-ventotto/rx3-mod-ventotto) (3★) ·
[jamm87/xdj-rx3-malaria-mod](https://github.com/jamm87/xdj-rx3-malaria-mod) ·
[thechrisart/TrackSplit](https://github.com/thechrisart/TrackSplit)

## 🟠 Patching de firmware (referencia Fase 2 — NO descifra)

[wow/pioneer-mods](https://github.com/wow/pioneer-mods) (1★) — inspecciona/parchea
el `.UPD` **ya descifrado** (LZSS, parches deterministas, rebuild verificado). No
extrae claves ni descifra; trabaja con firmware que aporta el usuario.

## ⚪ No aplican / menores

Skins Mixxx: [marcmonka/xdj-open-source-skin-mixxx](https://github.com/marcmonka/xdj-open-source-skin-mixxx),
[nauticsoftware/NauticMixxx](https://github.com/nauticsoftware/NauticMixxx),
[Prathmesh07123/deepflux_x-view](https://github.com/Prathmesh07123/deepflux_x-view).
Sin descripción: [Prasanna14y/xdj-rx3](https://github.com/Prasanna14y/xdj-rx3),
[guilhermewolf/xdj-rx3-link](https://github.com/guilhermewolf/xdj-rx3-link).
