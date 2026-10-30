# Xiaomi MiTV-MOSR2

Cómo quedó esta TV el **29 de octubre de 2026**. Cómo repetirlo: [`../README.md`](../README.md).

| | |
|---|---|
| Modelo | MiTV-MOSR2 (nombre en clave `irobot`), la misma placa que la MOSR4 |
| Sistema | Google TV con Android 11 (SDK 30), firmware `RTM5.220609.003.1493` |
| Equipo | MediaTek de 4 núcleos y 32 bits (`armeabi-v7a`), **1,4 GB de RAM**, unos 4 GB de datos |
| Home de fábrica | `com.google.android.apps.tv.launcherx` (desactivado) |
| Home ahora | `com.gathin.tvhome`, el launcher de la rama `yecoTV` |
| Lista de debloat | [`debloat.txt`](debloat.txt): 23 entradas, 22 aplicadas (una no existe en esta TV), ninguna desinstalada; el instalador del launcher desactiva además el Home de fábrica |
| Tema | [`theme/config.json`](theme/config.json) |

## Qué quedó instalado

| Qué | Paquete |
|---|---|
| Launcher yecoTV | `com.gathin.tvhome` |
| Servicios de accesibilidad del launcher | `InputKeyService` (botón Input, atajos) y `HomeKeyService` (botón de la cuadrícula) |
| Sources | `io.github.maxhayim.sources` |
| Capacity Stats | `com.lionlev.tv.capacity` |
| Sleep Timer (con su servicio de accesibilidad) | `io.github.luyzfernando08.sleeptimer` |
| Scoreboard | `com.poppy.scoreboard` |
| Bluetooth Connect | `com.gathin.btconnect` |

Las apps del usuario (Netflix, YouTube, Sparkle TV, Diixtream, Steam Link, Disney+, HBO Max) no se tocaron. iQIYI lo desinstaló el propio usuario.

## Qué hace cada botón del mando

| Botón | Código | Qué pasa |
|---|---|---|
| YouTube | 190 | Abre YouTube. Sin el servicio del launcher moría en el manejador de Xiaomi. |
| Netflix | 193 | Abre Netflix |
| Prime Video | 194 | Abre Prime Video |
| Cuadrícula | 284 (`ALL_APPS`) | Lleva al Home. `HomeKeyService` atiende esa tecla. |
| Input | 178 | Abre el selector de entradas |

Los dos servicios de accesibilidad **cuestan la repetición al mantener pulsado el volumen**: sube un paso por pulsación. Es un efecto conocido de este firmware (ver `docs/devices/xiaomi-mitv-mosr4.md`).

## Cómo se ve el Home

- **Dock** de 5 apps abajo: Netflix, YouTube, Sparkle TV, Diixtream, Steam Link. `rows_below_dock: 0`.
- **Fondo**: las películas de TMDb, una cada 25 s (baraja sin repetir), con el panel de datos encima del dock.
- **Seguir viendo** espera oculta debajo del dock y sale al pulsar Abajo. Solo Netflix publica ahí.
- **Barra superior**: Entradas, Wi-Fi, Ajustes.
- **Reposo**: el protector del launcher, no el de Google.

## Particularidades de esta TV

- `com.mitv.tvhome.mitvplus` (Xiaomi TV+) **se reactiva en cada arranque**. El tema lo oculta del Home; se puede volver a desactivar con `pm disable-user --user 0 com.mitv.tvhome.mitvplus`.
- La TV tiene la carga muy alta (promedio de 50 o más): ADB tarda a veces unos segundos en contestar. Un fallo de conexión puntual se resuelve reintentando.
- El Wi-Fi guardado en la configuración inicial pertenecía a `setupwraith`. Se olvidó y se volvió a unir antes de desactivarlo. Olvidarlo apagó la depuración USB.
- La región para plataformas de streaming es **Costa Rica**, aunque la TV diga otra (el firmware trae `cl`).
