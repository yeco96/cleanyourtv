# yecoTV

> Qué hace esta versión, función por función: [`FEATURES.md`](FEATURES.md).

Mi versión del launcher de [CleanYourTV](https://github.com/Gathin23/cleanyourtv), en la rama `yecoTV` de este fork.
Esta carpeta guarda **lo mío**: cómo dejé cada TV y cómo repetirlo. Lo que no está aquí es del proyecto original.

Una carpeta por modelo de TV: `yecotv/<modelo>/` con su `debloat.txt` y su `theme/config.json`.

| TV | Estado |
|---|---|
| [`xiaomi-mitv-mosr2`](xiaomi-mitv-mosr2/) | Google TV, la primera. Lista, el 29 de octubre de 2026 |
| [`xiaomi-mitv-mooq2`](xiaomi-mitv-mooq2/) | Android TV (Mi TV P1 43"). Lista, el 29 de octubre de 2026 |

## Qué añade respecto al original

El original no tiene red en el launcher. Este sí, y por eso **no se propone al proyecto original** (`AGENTS.md` lo excluye).

| Función | Clave de tema | Qué hace |
|---|---|---|
| **Seguir viendo** | `continue_row` | Fila bajo el dock con lo que las apps publican en la guía de la TV (hoy solo Netflix lo hace). Descarga carátulas. |
| **Fondos de TMDb** | `tmdb_backdrops` | 20 películas al día de fondo, con título, estrellas, año, géneros, duración y sinopsis. |
| Idioma, ritmo, clasificación | `tmdb_language`, `tmdb_rotation_seconds`, `tmdb_max_certification` | Español de México, una película cada 25 s, hasta PG-13. |
| **Reposo con películas** | `clock_wallpaper` | El protector del launcher muestra la película detrás del reloj, una por minuto, con su título pequeño abajo a la izquierda. |

Detalle de cada clave: [`themes/README.md`](../themes/README.md). Capturas: [`docs/switches.md`](../docs/switches.md).

## Reproducir una TV desde cero (Windows, PowerShell)

Requisitos en el PC: Python 3, Docker y estar en la misma red que la TV. No hace falta JDK ni SDK de Android (se compila en Docker).
En la TV: Ajustes, Preferencias del dispositivo, Acerca de, OK siete veces en *Build*, y activar **Depuración USB**.

```powershell
# 0. Una vez
python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
docker build -t cleanyourtv-android-build tools\docker        # primera vez: unos minutos

# 1. Comprobar la conexión (acepta "¿Permitir depuración USB?" en la TV, con "Permitir siempre")
$env:TV_HOST = "<ip de la tv>"
tools\venv\Scripts\python.exe -I tools\tv.py --probe

# 2. Compilar el launcher
powershell -ExecutionPolicy Bypass -File tools\docker-build.ps1
```

Si `docker-build.ps1` falla con `No such image` en PowerShell 5.1, ejecuta antes el `docker build` de arriba: la comprobación escribe en stderr y el script lo toma por error.

```powershell
# 3. Debloat. Lee antes el ORDEN en el encabezado de debloat.txt (olvidar y volver a unir el Wi-Fi primero)
tools\venv\Scripts\python.exe -I tools\recovery.py --save yecotv\<modelo>\debloat.txt
powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost $env:TV_HOST yecotv\<modelo>\debloat.txt

# 4. Instalar el launcher (la APK ya compilada). Quita el launcher de fábrica y toma el Home.
$env:SKIP_BUILD = '1'
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost $env:TV_HOST -StockLauncher <paquete del launcher de fábrica> -InputKeyService -HomeKeyService

# 5. Permiso de la guía de programas (para Seguir viendo)
tools\venv\Scripts\python.exe -I tools\tv.py "pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS"

# 6. Tema primero (crea la carpeta de la app), luego la clave de TMDb (ver abajo)
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost $env:TV_HOST yecotv\<modelo>\theme
tools\venv\Scripts\python.exe -I tools\tv.py --push tmdb_key.txt /sdcard/Android/data/com.gathin.tvhome/files/tmdb_key.txt

# 7. Protector de pantalla del launcher
tools\venv\Scripts\python.exe -I tools\tv.py "settings put secure screensaver_components com.gathin.tvhome/.ClockDream"
```

**Repetir el paso 5 cada vez que se reinstala el launcher** si no se hizo el 4 con el mismo launcher: el permiso sobrevive a las actualizaciones, no a una instalación desde cero.

### La clave de TMDb

Es gratuita (themoviedb.org, Ajustes, API, la **API Key v3** de 32 caracteres; no el token de lectura).
Va en `tmdb_key.txt` en la raíz del repo, **una sola línea**. Está en `.gitignore` y **nunca debe subirse**.
En la TV queda en un archivo de la carpeta del launcher; no va dentro de la APK ni de ningún tema.

**Si el envío falla con `secure_mkdirs failed`** (la TV no deja al shell escribir en `Android/data`, como la MOOQ2), se manda por el proveedor del launcher, que acepta ese archivo. Comando en [`xiaomi-mitv-mooq2/`](xiaomi-mitv-mooq2/README.md#la-clave-de-tmdb).

### Las películas no se pierden al cambiar de tema

Viven en `files/tmdb/`, no en `wallpapers/`. Aplicar un tema no las borra. Una vez al día se renuevan solas (la próxima vez que el Home aparece pasadas 24 h), o si quedan menos de 5.

## Volver atrás

| Qué | Cómo |
|---|---|
| Solo el protector de Google | `settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop` |
| Tema anterior | `tools\theme.ps1 -TvHost <ip> <carpeta del tema>` o `-Reset` |
| Todo (volver al Home de fábrica) | `tools\restore.ps1 -TvHost <ip> yecotv\<modelo>\debloat.txt -Stock <paquete del launcher de fábrica>` |

`restore.ps1` usa el registro que `recovery.py` guardó **antes** del primer cambio, en `%USERPROFILE%\.cleanyourtv\`
(un archivo por TV y lista). **Es la única copia: no lo borres.** Sin él, ver `docs/restore.md`.

## Mantenimiento

- **Tras actualizar el firmware** (las actualizaciones siguen activas): `tools\venv\Scripts\python.exe -I tools\check.py yecotv\<modelo>\debloat.txt --fix` vuelve a desactivar lo que regresó. Hay que reactivar la depuración USB antes.
- **Cerrar la depuración** al terminar: es ADB en texto plano por la red. `tools\adb-closed.py <ip>` comprueba que el puerto está cerrado.
- **Compilar y probar** antes de instalar: `testReleaseUnitTest lintRelease assembleRelease` en `launcher/` (con Docker). Hoy: 331 tests, lint sin errores.

## Lo que aprendí (para no repetirlo)

- **El recortador de recursos del build conserva todo lo que una constante de texto podría nombrar**, porque `AppAdapter` busca dibujables por nombre (`getIdentifier`). Una constante como `"ok"` o `"failed"` hizo que la APK pasara de 593 KB a 640 KB. Los textos que se guardan o comparan llevan un prefijo (`tmdb:ok`).
- **`rows_below_dock: 0` baja el dock al borde inferior.** La fila Seguir viendo no cabe debajo: espera oculta y aparece al pulsar Abajo. La altura que se le reserva depende del tamaño de las tarjetas, y se recalcula cuando cambia.
- **Las tarjetas de Seguir viendo no deben aceptar foco mientras el dock lo tiene**, o Arriba desde el dock las encuentra a ellas y no a la barra superior.
- **El protector activo de fábrica era el de Google** (`dreamx/.service.Backdrop`), no el del launcher. Para probar el del launcher sin esperar: `am start -n com.android.systemui/.Somnambulator`.
- **Una app normal no puede cerrar a otras en estas TVs.** Se probó un botón de memoria con `killBackgroundProcesses`: Android contestó `Invalid packageName` para cada app y no cerró ninguna (siempre "+0 MB"), mientras que `am kill` desde ADB sí cerraba YouTube y la Play Store. `FORCE_STOP_PACKAGES` no se puede conceder por ADB (es de firma). Se quitó el botón. Si hace falta liberar memoria: `am kill <paquete>` desde el PC, o desactivar lo que se conserva (el asistente de voz usa unos 290 MB).
- **El permiso `READ_TV_LISTINGS` no lo tiene ADB.** Consultar la guía desde `adb shell` devuelve 0 filas aunque la app vea datos. El informe del launcher está en `files/content-report.txt`.
- **Git Bash convierte las rutas** que empiezan por `/` en rutas de Windows (`/data/local/tmp/x` pasa a `C:/Program Files/Git/...`). Con `MSYS_NO_PATHCONV=1` no lo hace.
- **Una TV con mucha guía publicada** (Pluto TV, un sintonizador) puede tener cientos de filas: la MOOQ2 tenía 762 de "para ver después". El launcher lee solo las de "seguir viendo" y muestra 20.
- **PowerShell 5.1**: `$(...)` dentro de un comando para la TV se evalúa en el PC; usa Bash o comillas simples. Un aviso de Java en stderr aborta los scripts con `ErrorActionPreference Stop`.
- **El CI de este fork tiene el límite de la APK en 640 KB** (el original, 576 KB). La APK pesa unos 600 KB.
- La suite de Python en Windows da fallos que ya existen en el original (permisos, `git` ausente). Se compara contra ellos, no contra cero.

## Qué falta por comprobar

- La **línea de estado de TMDb** en los ajustes del launcher (esa pantalla no se puede abrir por ADB).
- La secuencia completa con el mando por **Seguir viendo**: Abajo, Izquierda y Derecha, Arriba y OK sobre una tarjeta.
- Las funciones de red no se han revisado contra una auditoría externa: el launcher habla solo con `api.themoviedb.org`, `image.tmdb.org` y las direcciones de carátulas que cada app publica.

## Uso de la TV y Modo niños

- **Uso de la TV** (menú de mantener OK en Ajustes): minutos por app, hoy, 7 y 30 días. Solo cuenta la app al frente, no el Home ni el protector de pantalla. Necesita una vez por TV: `appops set com.gathin.tvhome GET_USAGE_STATS allow`.
- **Modo niños** (botón de la barra superior): cuenta el tiempo aparte y avisa a la 1 h y a las 2 h. No bloquea nada. Se sale con el PIN de 4 dígitos (arriba/abajo cambian el número, OK avanza), y desde ahí se cambia el PIN. Si se olvida, el PIN genérico de reinicio (`kid_master_pin` en `config.xml`) deja elegir uno nuevo. Para que los avisos funcionen el launcher enciende su propio servicio, y para eso necesita una vez por TV: `pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS`.
- El instalador reescribe la lista de servicios de accesibilidad: el launcher vuelve a encender el suyo al abrir Home con el modo activo.
- **Home de niños:** al activar el modo por primera vez se elige qué apps pueden abrir (Opciones del modo, "Apps que pueden abrir los niños"). Con apps elegidas, el Home pasa a ser una pantalla simple con esas apps y una tarjeta "Adultos" (PIN); cualquier otra app que se abra, incluidos los botones del mando, vuelve a esa pantalla. Es una guardia, no una bóveda: una entrada HDMI puede escaparse en algunos TV. Tras 5 PIN erróneos cada intento espera 30 s, duplicando hasta 5 min. Con la lista vacía el modo solo cuenta el tiempo y avisa. Las ideas vienen de [maxhayim/kids-mode](https://github.com/maxhayim/kids-mode) (MIT); el código es propio.
- **Tarjeta "Kids Mode"** (add-on `addons/kids-mode`, igual que las otras tarjetas): se instala con `tools\docker-build.ps1 -Project addons/kids-mode` y `adb install`; el launcher pregunta una vez si mostrarla y después se coloca en el dock con "Editar el dock". Abrirla activa el modo (PIN la primera vez) o, si ya está activo, pide el PIN para administrarlo. Necesita que el launcher tenga el modo niños (esta rama).
- **Actualizar las películas** (menú de mantener OK en Ajustes): trae imágenes nuevas de TMDb ahora, sin esperar el control diario.
- **Horario automático** (opciones del modo niños, se cambia con OK: apagado, 15-19, 15-20, 16-21, 14-22 h): el modo se enciende solo en la franja y se apaga solo al terminar, si lo encendió el horario y hay PIN y apps elegidas. Si un adulto sale con el PIN dentro de la franja, no se vuelve a encender ese día.
- **Aviso de 1 h y 2 h:** una tarjeta dorada arriba de la pantalla durante 8 segundos, que no toma el mando ni pausa el video (con el servicio de la guardia encendido; si no, un mensaje breve).
- **Límite diario y bloqueo** (opciones del modo niños): 2 horas al día por defecto, separado para lunes a viernes y para sábado y domingo (se cambia con OK: sin límite, 30, 45, 60, 90, 120, 150, 180 min). El botón del Home muestra los minutos que quedan; avisa a 10 min y a 1 min. Al llegar a 0 el servicio de la guardia saca de cualquier app, la pantalla de niños queda sin apps ("Se acabó el tiempo de hoy") y solo la tarjeta "Adultos" abre: con el PIN se da tiempo extra (+15 / +30 min, solo por hoy) o se sale del modo. El día cuenta de 5:00 a 5:00. Sin límite, el modo solo cuenta y avisa a la 1 h y a las 2 h. El bloqueo necesita el servicio de la guardia encendido (permiso `WRITE_SECURE_SETTINGS` una vez por TV).
