# Kids Mode

Optional standalone Android TV module card for the yecoTV branch of this launcher. Opening the card turns
kids mode on in the launcher (it asks for a PIN the first time and for the apps the kids may open), or, with the
mode already on, asks for the PIN to manage or leave it. The mode itself, its screen, guard, PIN and time notices
live in the launcher; this card only opens it.
Build with JDK 17 and Android SDK 34: `./gradlew testReleaseUnitTest lintRelease assembleRelease`.
The small gradlew scripts reuse the repository's pinned Gradle wrapper with this folder as the project;
no wrapper JAR is copied into the add-on. Keep this folder inside the repository when building. Install the
APK you build with ADB or your preferred installer. No binaries or prebuilt APKs are committed.

The launcher discovers the static module card and asks whether to show it. Needs the launcher's kids mode
(`KidModeLaunchActivity`); without it the card says so and does nothing else.

## Permissions

No permissions requested. No network, telemetry, ads or background service. It declares that it needs to see
the launcher's package (`com.gathin.tvhome`) so it can start it.

## Validation

Built with Docker; the activity opens the launcher's kids mode on the TV it was written for (a Xiaomi MiTV).
