# Plan: el Modo niños como add-on del repo original

Estado: **propuesto, sin empezar** (30 oct 2026). Se retoma en otra sesión.

## Objetivo

Aportar el Modo niños a la comunidad (`Gathin23/cleanyourtv`) como un add-on `addons/kids-mode/`, en una rama nueva
basada en `upstream/main` (no en la `main` local, que está desfasada). El fork completo ya está presentado como
proyecto en el `SHOWCASE.md` (PR #2702); esto es la parte que sí cabe en el repo.

## Lo que dicen las reglas (leídas)

Fuentes: `AGENTS.md`, `docs/workflows/addon.md`, `addons/README.md`, `.github/maintainer/MAINTAINER.md`.

- Un add-on es su propia app, en `addons/<nombre>/`, y solo habla con el launcher por la tarjeta de módulo
  (`docs/modules.md`, `docs/specs/card.md`). No toca `launcher/` y el launcher no nombra `addons/`.
- **`MAINTAINER.md`: "No new accessibility-service, notification-access or network add-ons in the repo"**
  (#1177, #1316, #1537, #1816, #1949, #1976). Un add-on con la guardia actual (servicio de accesibilidad) se cerraría.
- Red solo si el mantenedor la invitó en el PR o en un issue. Por eso se quita `INTERNET`.
- Sin APK en el repo, sin telemetría, sin anuncios, sin IPs ni identificadores, un tema por PR.
- Si la idea necesita un cambio en el launcher, no es un add-on.

## Qué cambia respecto al fork

| Fork | Add-on |
|---|---|
| `INTERNET`, películas de TMDb | Se quitan |
| Servicio de accesibilidad (guardia) | Servicio en primer plano que consulta `UsageStatsManager` cada pocos segundos; si la app no está permitida, vuelve al inicio o muestra su pantalla de bloqueo. Precedentes: Scoreboard (`PACKAGE_USAGE_STATS`), Brightness (overlay, servicio, arranque) |
| Pastilla "Modo niños", horario y redirección en el Home | Se quitan: no se toca el launcher. Queda la tarjeta de módulo |
| Pantalla de inicio de niños, banners, minutos por app | Se queda, como app propia |
| PIN maestro `kid_master_pin` en `config.xml` | Se quita (un PIN universal en un repo público es un hueco). El reinicio sería por ADB, documentado |
| `WRITE_SECURE_SETTINGS` para reactivar el servicio | Ya no hace falta |

## Lista de tareas

1. Rama `addon-kids-mode` desde `upstream/main`; solo `addons/kids-mode/` y una fila en `addons/README.md`.
2. Proyecto Gradle propio (`settings.gradle`, `app/`, wrappers y `.gitignore` copiados de un vecino), paquete propio.
3. App: PIN (poner, repetir, pedir, bloqueo tras intentos), apps permitidas, límite diario con tiempo extra por PIN,
   uso por app, pantalla de niños, tarjeta de módulo (`module_card.xml`).
4. Servicio de vigilancia con `PACKAGE_USAGE_STATS` y overlay; medir el retraso real del bloqueo en la TV.
5. README con sección **Permissions** (cada permiso con el nombre del manifest y su motivo, más el servicio en segundo
   plano) y la columna de permisos de la tabla igual al manifest (`tests/test_addon_permissions_index.py`).
6. `screenshot.jpg` de una TV real, 16:9, menos de 300 KB, sin datos personales.
7. Línea "Tested on" con modelo y versión de Android; una fila en la tabla de `addons/README.md`.
8. Pruebas: `python -m unittest discover -s tests` y `./gradlew --no-daemon assembleRelease` en el add-on.
9. Revisar que no haya ya un PR abierto parecido (los duplicados se cierran).
10. Publicar la rama en el fork; el PR lo abre el dueño del fork. Título: `addons: add Kids Mode ...`.

## Riesgos

- Sin accesibilidad el bloqueo reacciona en segundos, no al instante; el README debe decirlo.
- Ya hay un `addons/kids-mode` en el fork que solo abre el modo niños del launcher: hay que rehacerlo como app
  independiente, no reutilizarlo.
- El mantenedor puede cerrarlo igual por vigilar el uso de apps con overlay; entonces queda el showcase.
- El control sigue siendo suave: no es a prueba de niños decididos.

## Pendiente de otras conversaciones

- Apagado de la TV al primer encendido del día: ver "Ajustes de las TVs fuera del launcher" en [`FEATURES.md`](FEATURES.md).
- Anuncios: el DNS privado no está decidido; ninguna opción propuesta convenció. El add-on "Ad Blocking" del repo solo
  muestra estadísticas de un Pi-hole o AdGuard Home; no bloquea.
