# Uso de la TV: medir, guardar, mostrar, y el límite de la hija

Plan, no código. Escrito el 30 de octubre de 2026. Decisiones tomadas por el dueño al final del documento.

## Qué se quiere

1. Saber **cuánto se usa la TV y qué app**, en minutos, por día. **No cuenta que esté encendida**, solo el uso real.
2. Guardar el **historial diario, semanal y mensual** y mostrar estadísticas.
3. Un **límite diario para la hija**. El dueño y su esposa no tienen límite.

Tres piezas, que se construyen en ese orden. Las dos primeras funcionan solas, sin la tercera.

## Pieza 1: medir

| Qué | Cómo |
|---|---|
| Qué es "usar" | Una app **al frente**, con la pantalla encendida. Android la registra con eventos `ACTIVITY_RESUMED` y `ACTIVITY_PAUSED` (`UsageStatsManager.queryEvents`) |
| Qué no cuenta | El Home del launcher, el protector de pantalla, la interfaz del sistema y la TV en espera |
| Permiso | `PACKAGE_USAGE_STATS`, que se concede **una vez por ADB** con `appops set com.gathin.tvhome GET_USAGE_STATS allow` (ya se hizo así con Scoreboard). Sobrevive a las actualizaciones |
| Entradas HDMI | Pasan por la app de TV (`com.mitv.livetv`, o `com.mediatek.wwtv.tvcenter` en la MOOQ2), así que salen como **una sola "TV en vivo / HDMI"**. No se sabe cuál HDMI es |
| Sesiones largas | Una sesión se corta al apagarse la pantalla y se limita a unas horas si no hay eventos (una app olvidada) |
| Cambio de día | Una sesión que cruza la medianoche se reparte entre los dos días |

**Cuándo se lee:** al abrir el Home y cada minuto mientras está a la vista. Android guarda los eventos unos días, así que no hace falta nada corriendo todo el tiempo. Si la TV pasa días apagada, tampoco hubo uso. La primera vez se importan los eventos que Android todavía conserve.

## Pieza 2: historial y estadísticas

**Datos.** Una base SQLite del propio launcher, sin dependencias nuevas:

| Tabla | Columnas |
|---|---|
| `daily` | `day`, `pkg`, `profile` (`adulto` o `hija`), `seconds` |
| `meta` | `last_event_ms` (hasta dónde se leyó) |

Se guardan segundos y se muestran minutos. Semana y mes salen de agrupar `daily`, no se guardan aparte. Pesa unos pocos KB al año. **Todo queda en la TV, sin internet.** Cada TV tiene lo suyo.

**Pantalla en la TV** (`Uso de la TV`, abierta desde el menú de mantener OK en Ajustes):

| Pestaña | Muestra |
|---|---|
| Hoy | Total, y las apps con su barra de minutos, separando adultos e hija |
| Semana | Los últimos 7 días en barras, el promedio y las apps más usadas |
| Mes | Una barra por día, el total y las apps del mes |

## Pieza 3: el límite de la hija

### Quién es "la hija": Modo Hija

La TV no sabe quién la usa. Por eso se **activa un modo**:

- Se activa desde el mismo menú, **sin PIN**. Para **salir** pide un **PIN de 4 dígitos** (guardado con sal, nunca en claro).
- Mientras el modo está activo, el uso cuenta en `profile = hija` contra su límite. El resto del tiempo cuenta como `adulto`, **sin ningún límite ni PIN**.
- El modo **sobrevive a un reinicio**.

### Qué cuenta

**Todo lo que use en la TV**: cualquier app en pantalla, y las entradas HDMI como tiempo de TV en vivo.

### Qué pasa al acercarse y al llegar al límite

| Momento | Qué ocurre |
|---|---|
| Siempre, en modo hija | Una insignia discreta con el tiempo que queda |
| A los 10 minutos y al último minuto | Un aviso sobre la app |
| Al llegar al límite | La app se cierra, vuelve al Home y sale **"Se acabó el tiempo de hoy"** |
| En esa pantalla | Un PIN permite **ampliar** (+15, +30 o +60 minutos) o salir del modo |
| Al día siguiente | El contador se reinicia solo (a las 05:00, para no cortar una película de madrugada) |

### Cómo se bloquea

Un **servicio de accesibilidad nuevo y ligero**, `UsageGuardService`, que solo observa qué ventana está al frente (`TYPE_WINDOW_STATE_CHANGED`). Si el modo hija está agotado y la ventana no es del launcher, ejecuta la acción global **Inicio**. El Home, al estar bloqueado, muestra la pantalla de "se acabó el tiempo" y no lanza nada.

Esto cubre también los botones del mando (Netflix, Prime) y las entradas HDMI, porque no pasan por el Home.

- **No** pide filtrar teclas, así que **no debería costar la repetición del volumen** (eso lo causan los servicios que filtran teclas). A comprobar.
- Se enciende por ADB, como los otros servicios.
- En la TV 2 hoy no hay ningún servicio de accesibilidad. Esta sería el primero.

## Qué NO es

Es un **control suave, no a prueba de niños**. Una hija decidida puede cambiar a una entrada HDMI, usar otro dispositivo, reiniciar, o conectar un cable con otro aparato. Para algo firme, hay que **combinarlo con el control parental del router** (corta la red por horario). Esto aporta lo que el router no ve: qué app y cuántos minutos.

Límites de la medición:
- Un video **en pausa** cuenta mientras la app esté al frente. Mejorable más adelante con las sesiones de medios.
- La hora de la TV debe estar bien (se usa la de los eventos).
- Antes de instalar no hay historia más allá de lo que Android conserve.

## Fases

| Fase | Contenido | Pruebas |
|---|---|---|
| **1** | Permiso, base de datos, lectura de eventos, pantalla de estadísticas | Tests puros de emparejado y corte de sesiones, y de los totales por semana y mes. Comprobación en la TV con uso real |
| **2** | Modo Hija: PIN, límite, contador, avisos, bloqueo, reinicio diario | Tests puros del cálculo del límite y del reinicio. En la TV: que el bloqueo saque de Netflix, de YouTube y de HDMI |
| **3** | Extras: límite distinto en fin de semana, límite por app, resumen semanal, medios en pausa | A definir |

## Decisiones del dueño

1. **Identificar:** Modo Hija que ellos activan, con PIN para salir. Ellos dos nunca tienen límite.
2. **Alcance:** todo lo que use en la TV, HDMI incluido.
3. **Al límite:** bloqueo hasta mañana, con PIN para dar más tiempo.
4. **Estadísticas:** en la pantalla de la TV, sin red.

## Valores por defecto propuestos (a confirmar)

- Límite: **90 minutos al día**, igual todos los días.
- Reinicio del contador: **05:00**.
- PIN: **4 dígitos**.
- Las estadísticas las ve cualquiera que tenga el mando, también la hija.
