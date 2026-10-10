# GuardCall Android

GuardCall est une application Android moderne pour filtrer les appels indésirables. Son interface s'inspire de l'expérience simple de [Saracroche](https://codeberg.org/cbouvat/saracroche-android), avec une identité visuelle GuardCall personnalisée : palette indigo et menthe, cartes Material 3, thème clair/sombre et activation guidée du service système.

## Fonctionnalités

- **Blocage à la demande** : ajoutez un numéro et empêchez ses appels d'arriver jusqu'à vous.
- **Filtrage système** via `CallScreeningService` (Android 8.0+, API 26).
- **Activation guidée** du rôle `ROLE_CALL_SCREENING` sur Android 10+.
- **Normalisation des numéros** : formats nationaux français, `+33` et préfixe `00` sont convertis en E.164.
- **Correspondance stricte** : aucun blocage par suffixe, afin d'éviter les faux positifs.
- **Stockage local** : la liste reste dans le stockage privé de l'appareil, sans permission dangereuse.
- **Interface Jetpack Compose Material 3** : cartes de statut, statistiques, FAB d'ajout et formulaire en bottom sheet.
- **Thème personnalisé** : mode clair et sombre avec une palette GuardCall indigo, menthe et corail.

## Stack

- Kotlin 1.9 · Jetpack Compose · Material 3
- Android Gradle Plugin 8.3 · Gradle 8.7
- SDK de compilation 34 · Android minimum API 26
- Gson pour la sérialisation locale

## Structure

```text
android/
  app/src/main/java/com/guardcall/
    MainActivity.kt
    data/                         # Modèles, stockage et correspondance
    screening/                    # Service Android de filtrage d'appels
    ui/screens/MainScreen.kt      # Écran Compose GuardCall
    ui/theme/                     # Palette, typographie et thème clair/sombre
    util/                         # Normalisation des numéros
  app/src/test/                   # Tests JVM de la logique métier
```

Le dépôt est volontairement **Android uniquement**.

## Construire l'APK

Prérequis : JDK 17 et Android SDK 34, avec `ANDROID_HOME` configuré ou un fichier `android/local.properties`.

```bash
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

L'APK de debug est généré dans `android/app/build/outputs/apk/debug/app-debug.apk`.

Pour l'installer sur un appareil :

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Puis ouvrez GuardCall, touchez **Activer la protection** et sélectionnez GuardCall comme service de filtrage d'appels.

## Sécurité et confidentialité

GuardCall ne demande pas l'accès aux contacts, au journal d'appels ou à l'état du téléphone. Le rôle système de filtrage est accordé explicitement par l'utilisateur et la liste des numéros est enregistrée dans `files/blocklist.json`.

## CI

Les workflows GitHub Actions exécutent les tests JVM, Android Lint et la construction de l'APK debug. Le workflow de release permet de construire et d'attacher l'APK à une release.

## Licence

[MIT](LICENSE)
