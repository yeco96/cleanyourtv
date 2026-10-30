# Xiaomi Mi TV P1 43" (MiTV-MOOQ2)

Cómo quedó esta TV el **29 de octubre de 2026**. Cómo repetirlo: [`../README.md`](../README.md).
La otra TV, con otra placa y más notas: [`../xiaomi-mitv-mosr2/`](../xiaomi-mitv-mosr2/).

| | |
|---|---|
| Modelo | Xiaomi Mi TV P1 43" (MiTV-MOOQ2, nombre en clave `croods`), unidad de Latinoamérica (L43M6) |
| Sistema | **Android TV** 11 (SDK 30), firmware `RTM4.220307.164.8238` |
| Equipo | MediaTek de 4 núcleos y 32 bits (`armeabi-v7a`), **1,7 GB de RAM**, 11,8 GB de datos (4,8 GB libres al empezar) |
| Home de fábrica | `com.google.android.tvlauncher` (desactivado) |
| Home ahora | `com.gathin.tvhome`, el launcher de la rama `yecoTV` |
| Lista de debloat | [`debloat.txt`](debloat.txt): la de `examples/xiaomi-mitv-mooq2`, 23 entradas aplicadas, 4 opcionales conservadas |
| Tema | [`theme/config.json`](theme/config.json) |

## Más sencilla que la otra TV

- `setupwraith` **no toma el Home** y el Wi-Fi guardado **no es suyo** (`cname=android.uid.system`), así que no hubo que olvidar y volver a unir la red.
- Basta con instalar el launcher (que desactiva `tvlauncher` tras comprobar que toma el Home) y aplicar la lista. El orden que sí importa: **launcher, protector y recién entonces la lista**, porque la lista desactiva los protectores de Xiaomi y Google.
- **No hacen falta los servicios de accesibilidad**: los dos botones del mando abren sus apps solos.

## Estado de partida (antes de tocar nada)

Todo de fábrica: ningún paquete desactivado, ningún servicio de accesibilidad, depuración USB activa, y como protector `com.google.android.backdrop/.Backdrop`. Para volver a ese protector: `settings put secure screensaver_components com.google.android.backdrop/.Backdrop` (y reactivar `com.google.android.backdrop` y `com.mitv.dream`).

## Qué quedó

- **Debloat**: Xiaomi y Google (telemetría), PatchWall, canales y Xiaomi TV+, el gestor "TV Manager", el puente de Alexa, la galería, **los dos protectores de pantalla de fábrica**, el bloqueo parental de Xiaomi, el menú de fábrica y la demostración de tienda, Play Juegos y Play Películas.
- **Conservado a propósito**: el asistente de voz (el botón del micrófono sigue), YouTube Music, el reproductor USB y el de Mi, Chromecast y Miracast, las entradas HDMI y AV, la tecla de Netflix y las actualizaciones de firmware.
- **Tema**: el mismo de la otra TV (fondos de TMDb en español de México, hasta PG-13, 25 s por película y reposo con la película), con el dock **Netflix, YouTube, Disney+ y Diixtream** y Live TV en `com.mediatek.wwtv.tvcenter`.
- **Protector**: el del launcher (`ClockDream`), con la película, la hora y su título abajo. Comprobado en la TV, en 1080p.

## El mando

Solo tiene dos botones propios y los dos funcionan sin ayuda, con el launcher como Home:

| Botón | Código | Qué lanza |
|---|---|---|
| Netflix | 193 | `com.netflix.action.NETFLIX_KEY_START` a Netflix |
| Prime Video | (sin registro propio) | `AMAZON_BUTTON` a Prime Video |

No tiene botón de YouTube ni de cuadrícula, y el botón de entradas no existe: se usa **Entradas** de la barra superior. Por eso el tema no lleva `app_keys` y no se activaron `InputKeyService` ni `HomeKeyService` (que además cuestan la repetición al mantener pulsado el volumen).

## Seguir viendo

Esta TV tiene muchísimo en la guía de programas: **762 filas** de "para ver después" y 293 canales, por **Pluto TV** (188 canales) y el sintonizador de MediaTek (77). De esas, el launcher saca **44 entradas reales de "seguir viendo"**, de varias apps (Star+, Twitch, Red Bull TV, Stremio…), con la más reciente primero y un máximo de 20 tarjetas.

El launcher **no lee las filas de recomendaciones** (866 aquí): no las usa el Home y en esta TV eran trabajo en vano en cada regreso.

## La clave de TMDb

El envío directo a la carpeta de la app **falla en esta TV** (`secure_mkdirs failed`: Android 11 no deja al shell escribir en `Android/data`). Se envía por el proveedor del launcher, que acepta el archivo `tmdb_key.txt`:

```powershell
$env:MSYS_NO_PATHCONV = 1      # solo en Git Bash: evita que convierta la ruta de la TV
tools\venv\Scripts\python.exe -I tools\b64push.py tmdb_key.txt /data/local/tmp/cyt-key
tools\venv\Scripts\python.exe -I tools\tv.py "content write --uri content://com.gathin.tvhome.theme/tmdb_key.txt < /data/local/tmp/cyt-key; rm -f /data/local/tmp/cyt-key"
```

Hay que haber aplicado un tema antes, para que exista la carpeta de la app.

## Pendiente

- **Depuración USB**: sigue activa. Cerrarla al terminar.
- La secuencia por la fila Seguir viendo con el mando sigue sin probarse, igual que en la otra TV.
- No se instalaron add-ons (en la otra: Sources, Capacity Stats, Sleep Timer, Scoreboard y Bluetooth Connect).
