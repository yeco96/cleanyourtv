# yecoTV: qué hace esta versión

yecoTV es un **fork personal** de [CleanYourTV](https://github.com/Gathin23/cleanyourtv) (Gathin23, licencia MIT). Parte del
proyecto original, que limpia un Android TV (desactiva lo que sobra, sin desinstalar nada) y lo deja con un launcher
propio, y lo lleva más lejos para **dos TVs Xiaomi de una casa con niños**: un Home más cinematográfico, un modo niños
con límite de tiempo, y estadísticas de uso. Esta página documenta todo lo que el fork añade o cambia. La guía de cómo se
instaló cada TV, con sus lecciones, está en [`README.md`](README.md) de esta carpeta.

> Rama de trabajo: `yecoTV` (es la rama por defecto del fork). `main` sigue al original.

## Enfoque

1. **Personal primero.** Se construye para las dos TVs de la casa (MiTV-MOSR2 y MiTV-MOOQ2, Android TV 11), con sus
   listas de depuración y temas en [`xiaomi-mitv-mosr2/`](xiaomi-mitv-mosr2/) y [`xiaomi-mitv-mooq2/`](xiaomi-mitv-mooq2/).
2. **Simple de usar con el mando.** Todo se maneja con las flechas, OK y Atrás; nada pide teclado ni teléfono.
3. **Sin nube propia.** Los datos de uso, el PIN y las listas se guardan solo en cada TV. La única salida a internet es la
   descarga de imágenes y datos de películas de TMDb, y las carátulas de "Seguir viendo".
4. **Mantiene el espíritu del original:** nada se desinstala, todo se deshace, y se prueba antes de afirmar que funciona.
5. **Se aparta del original donde hace falta.** Las reglas del proyecto original (sin `INTERNET`, sin servicios, sin permisos
   nuevos en el launcher) **no se cumplen aquí a propósito**; la sección [Diferencias con el original](#diferencias-con-el-original)
   las lista una por una. Por eso esto vive en un fork y no como un PR al original.

## Qué añade

### 1. Home cinematográfico con películas de TMDb

Reemplaza el fondo por **imágenes de películas** (TMDb) que rotan, con la información de cada una.

- **Panel de la película:** título, estrellas y nota, año, géneros, duración y resumen, abajo cerca del dock.
- **Dock abajo del todo** (`rows_below_dock: 0`), para que la imagen se vea más.
- **Contraste adaptativo:** la sombra y el oscurecido se ajustan al brillo de cada imagen para que el texto siempre se lea.
- **Transición suave** (fundido de 700 ms) y **orden aleatorio sin repetir** hasta agotar las imágenes de la tanda (se
  recuerda entre reinicios).
- **Salvapantallas** (`ClockDream`): el mismo reloj, día y fecha, sobre las películas, con su pie de foto.
- **Descarga:** hasta unas 20 imágenes de 1280 px, cada 24 horas o cuando quedan menos de 5, en su propia carpeta
  `tmdb/` (un push de tema no la borra). Solo películas aptas: clasificación de EE. UU. hasta `PG-13` (configurable) y
  región CR.
- **Estado** en Ajustes del launcher (línea "tmdb": cuántas imágenes y cuándo se actualizó).
- **Botón "Actualizar las películas"** en el menú de mantener OK sobre el engranaje: trae imágenes nuevas ahora.

Claves del tema (`config.json`), documentadas en [`themes/README.md`](../themes/README.md):

| Clave | Valor por defecto | Qué hace |
|---|---|---|
| `tmdb_backdrops` | `false` | Enciende las películas (necesita la clave) |
| `tmdb_language` | `es-MX` en estos temas | Idioma de títulos y resúmenes |
| `tmdb_rotation_seconds` | `25` | Cada cuánto cambia la imagen |
| `tmdb_max_certification` | `PG-13` | Clasificación máxima (G, PG, PG-13, R, NC-17 o "any") |
| `rows_below_dock` | `0` aquí | Filas de apps visibles bajo el dock |

**La clave de TMDb** (v3, de 32 caracteres) va en un archivo `tmdb_key.txt` en
`/sdcard/Android/data/com.gathin.tvhome/files/`. Nunca va en el repositorio (está en `.gitignore`):

```
tools\venv\Scripts\python.exe -I tools\tv.py --push tmdb_key.txt /sdcard/Android/data/com.gathin.tvhome/files/tmdb_key.txt
```

> Este producto usa la API de TMDb pero no está respaldado ni certificado por TMDb. Los datos e imágenes de películas
> son de [The Movie Database](https://www.themoviedb.org/).

### 2. Seguir viendo

Una fila bajo el dock con lo que se estaba viendo en cada app (lo que las apps publican en la guía de programación de
Android TV), con su carátula. Se enciende o apaga en Ajustes del launcher (`continue_row`); apagada, el launcher no lee la
guía ni descarga nada. Necesita el permiso `READ_TV_LISTINGS`, que se concede una vez por ADB.

### 3. Uso de la TV

Una pantalla (menú de mantener OK sobre el engranaje, "Uso de la TV") que muestra **cuánto se usó la TV y en qué app**.

- Cuenta solo la **app que está al frente**, no que la TV esté encendida. No cuenta el Home, el salvapantallas, la
  interfaz del sistema ni la TV en espera. Una sesión sin cierre se corta a las 6 horas.
- **Pestañas Hoy, 7 días y 30 días:** total, adultos y niños por separado, comparación con el periodo anterior, barras por
  día, el día que más usan los niños y las 10 apps principales con su icono.
- **El día corre de 5:00 a 5:00**, para que una película de madrugada cuente en el día que empezó. La pestaña lo dice.
- **Historial guardado en la TV** (base `usage.db`, dos años), sin nube. Cada TV guarda lo suyo.

Se activa una vez por TV con: `appops set com.gathin.tvhome GET_USAGE_STATS allow`.

### 4. Modo niños

Un modo que **separa el tiempo de los niños del de los adultos**, limita su uso y ofrece una pantalla propia.

**Activarlo y salir**
- Botón **"Modo niños"** en la barra superior. Con el modo activo se pone dorado y muestra "Niños · quedan X min".
- También como **tarjeta de módulo** ("Kids Mode", ver [add-on](#5-add-on-kids-mode)), para ponerlo en el dock.
- **PIN de 4 dígitos**, que se pide para salir y para administrar. Se introduce con el mando como el de Android TV:
  Arriba/Abajo cambian el número, OK lo deja y pasa al siguiente (los dejados se ven como puntos), Izquierda o Atrás
  retroceden. Las teclas numéricas también sirven. Se guarda con sal y SHA-256, nunca en claro.
- **Protección contra adivinar:** tras 5 PIN erróneos seguidos, cada intento espera 30 s, duplicando hasta 5 min.
- **PIN genérico de reinicio** (`kid_master_pin` en `launcher/app/src/main/res/values/config.xml`, `2580` por defecto): sirve
  para elegir un PIN nuevo si se olvida el actual. Está en el código y el repositorio es público: cámbialo si importa.

**La pantalla de niños**
- Cielo nocturno morado con estrellas, saludo "¡Hola!", y una **barra de tiempo** que se llena en verde, amarillo y rojo.
- Un **dock de vidrio** como el de los adultos, con una tarjeta por app permitida (su banner, su nombre y los minutos usados
  hoy) y una tarjeta "Adultos" que pide el PIN. Atrás no hace nada.
- **Lista de apps permitidas:** se elige en las opciones ("Apps que pueden abrir los niños"); las apps infantiles conocidas
  vienen marcadas y nunca el YouTube completo. Se pide la primera vez que se activa el modo.
- **Home de niños:** con apps elegidas, el Home pasa a ser esta pantalla.

**La guardia**
- Un **servicio de accesibilidad** pequeño que solo ve qué app pasó al frente (sin leer la pantalla ni las teclas). Cualquier
  app que no esté en la lista, incluidos los botones del mando, vuelve a la pantalla de niños. El launcher enciende su propio
  servicio al activar el modo (necesita `WRITE_SECURE_SETTINGS`) y lo repone si una instalación lo borra.
- Es una **guardia, no una bóveda:** una app no permitida puede verse un instante y una entrada HDMI puede escaparse en
  algunas TVs.

**Límite de tiempo**
- **2 horas al día por defecto**, separado para lunes a viernes y para sábado y domingo. Se cambia con OK entre: sin límite,
  30, 45, 60, 90, 120, 150 y 180 minutos.
- **Avisos** en una tarjeta dorada sobre la pantalla (8 s, no pausa el video ni toma el mando) a los 10 minutos y al último
  minuto. Sin límite, avisa a la 1 h y a las 2 h de uso.
- **Al llegar a cero:** el servicio saca de cualquier app, aunque sea una permitida (revisa cada 15 s en los últimos tres
  minutos). La pantalla de niños queda sin apps y dice "Se acabó el tiempo de hoy"; solo abre "Adultos".
- **Tiempo extra** con el PIN: +15 o +30 minutos, solo por hoy, visible aparte ("de 1 h (+30 min extra)") y que se puede
  quitar. Reinicia los avisos.
- Con la lista de apps vacía, el modo no bloquea nada si no hay límite; con límite, el bloqueo saca todo menos el launcher.

**Horario automático**
- Encendido y apagado solo en una franja (apagado, 15–19, 15–20, 16–21 o 14–22 h), si lo encendió el horario y ya hay PIN y
  apps. Si un adulto sale con el PIN dentro de la franja, no se vuelve a encender ese día.

**Dónde se ve todo:** las filas de las opciones (con scroll) son: desactivar, límites, tiempo extra, quitar el extra, horario,
apps, cambiar el PIN y cerrar.

### 5. Add-on "Kids Mode"

[`addons/kids-mode/`](../addons/kids-mode/): una tarjeta de módulo de 9 KB (como las otras cuatro que usa el Home) que abre
el modo niños del launcher. Sin permisos propios. Necesita el launcher de este fork.

### 6. Documentación y configuración de las dos TVs

- [`xiaomi-mitv-mosr2/`](xiaomi-mitv-mosr2/) y [`xiaomi-mitv-mooq2/`](xiaomi-mitv-mooq2/): lista de depuración, tema y notas
  de cada TV. Dock: Netflix, YouTube, Disney+ y Diixtream (más las apps opcionales conservadas).
- [`README.md`](README.md): cómo se instaló, el orden seguro de los pasos, y las lecciones aprendidas.

## Qué NO se queda

- **Botón de memoria:** se probó y se quitó. Android rechaza `killBackgroundProcesses` desde una app normal en estos
  firmwares; solo `am kill` desde el ordenador funciona. Está explicado en [`README.md`](README.md).

## Qué hace falta en cada TV (una vez)

```
pm grant com.gathin.tvhome android.permission.READ_TV_LISTINGS
pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS
appops set com.gathin.tvhome GET_USAGE_STATS allow
```

El instalador (`launcher\build-and-install.ps1 -InputKeyService -HomeKeyService`) reescribe la lista de servicios de
accesibilidad; el launcher vuelve a encender el de la guardia solo al abrir el Home con el modo activo.

## Ajustes de las TVs fuera del launcher

Cambios hechos en el sistema de una TV, no en el código. Cada uno se puede deshacer con el comando indicado.

### La TV se apagaba sola a los segundos de encenderla (TV 2)

- **Síntoma:** la primera vez del día que se enciende, a los pocos segundos se apaga; al volver a encenderla ya no
  se repite. Pasaba también antes de este launcher, y en las dos TVs.
- **Causa encontrada:** el registro de la TV 2 (`logcat -d -v time | grep "Going to sleep due to"`) muestra
  `Going to sleep due to hdmi`, unos 21 s después de pulsar el botón de encendido, en el servicio de energía de Xiaomi
  (`MitvPowerManagerService`, "go to str direct for reason = 5"). Es HDMI-CEC: un aparato conectado por HDMI manda la
  orden de apagado. No viene del launcher ni de una app. Las otras apagadas del día eran `power_button`.
- **Cambio:** HDMI-CEC desactivado en la TV 2 (no hay nada útil conectado por HDMI).
  `settings put global hdmi_control_enabled 0`. Ya estaban en 0 `hdmi_control_auto_wakeup_enabled` y
  `hdmi_control_auto_device_off_enabled`.
- **Deshacer:** `settings put global hdmi_control_enabled 1`.
- **Por confirmar:** que mañana, en el primer encendido del día, ya no se apague. Si se apaga, el motivo que salga en el
  registro será otro y hay que buscarlo ahí. En la TV 1 no hay un evento así en el registro actual; allí hay además una
  app de temporizador de sueño (`sleeptimer`) activa como servicio de accesibilidad, a revisar si se repite.
- **No confundir con** el reloj de reposo del launcher (`home_idle_clock_minutes`, 2 minutos en el tema de la TV 2): es
  una pantalla de reloj oscura, no un apagado.

## Diferencias con el original

El original pide que el launcher se mantenga sin red ni servicios. Este fork **las rompe a propósito**:

| Regla del original | En este fork |
|---|---|
| Sin permiso `INTERNET` | Añadido: descarga de imágenes y datos de TMDb y carátulas de "Seguir viendo" |
| Sin permisos nuevos | Añadidos `PACKAGE_USAGE_STATS`, `READ_TV_LISTINGS` y `READ_EPG_DATA` |
| Sin servicios en segundo plano | Un servicio de accesibilidad (la guardia), que solo trabaja con el Modo niños activo |
| Nada nuevo se enciende por defecto | El botón "Modo niños" y las filas "Uso de la TV" y "Actualizar películas" aparecen siempre |
| Tope de APK de 600 KB | Subido a 640 KB (`.github/workflows/ci.yml`) |
| El modo no cambia el launcher | El Modo niños, el uso y las películas viven dentro del launcher |

Por eso no se propone como PR de código al original; el fork se presenta en su
[`SHOWCASE.md`](../SHOWCASE.md) como proyecto construido sobre él.

## Pruebas

- **Launcher:** `testReleaseUnitTest lintRelease assembleRelease` con Docker (`tools\docker-build.ps1`); 354 pruebas
  JVM, entre ellas las de sesiones de uso, días, informes, PIN, límite, horario y política de la guardia.
- **Python:** `tools\venv\Scripts\python.exe -m unittest discover -s tests`. En Windows fallan algunas pruebas de los scripts
  de tema que ya fallaban antes de este fork.
- **En las TVs:** cada función se vio funcionando en la TV 1; el bloqueo se verificó de punta a punta con un límite de prueba.
  La tarjeta del dock, el botón de actualizar películas y el horario automático no se han visto con el mando.

## Límites conocidos

- **Control suave:** no es a prueba de niños decididos; combínalo con el control parental del router si hace falta.
- Un video en pausa cuenta mientras la app esté al frente; una app olvidada se corta a las 6 horas.
- Las entradas HDMI salen como una sola app ("TV en vivo").
- Borrar los datos de la app quita el PIN y el historial.

## Créditos

- [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv): el proyecto original (MIT).
- [RuckieOfficial/cleanyourtv](https://github.com/RuckieOfficial/cleanyourtv): la idea de las películas de TMDb con panel.
- [gabzferrer/my-system-tv](https://github.com/gabzferrer/my-system-tv): la idea de "Seguir viendo".
- [maxhayim/kids-mode](https://github.com/maxhayim/kids-mode) (MIT): las ideas de la lista de apps permitidas, la guardia y la
  espera tras PIN erróneos; el código de este fork es propio.
- [The Movie Database](https://www.themoviedb.org/): datos e imágenes de películas.

## Mantener el fork al día

```
git fetch upstream
git merge upstream/main      # los conflictos más probables: MainActivity, Config, WallpaperPlayer, AndroidManifest
```

Tras un merge: recompilar, correr las pruebas y reinstalar con `-InputKeyService -HomeKeyService`.
