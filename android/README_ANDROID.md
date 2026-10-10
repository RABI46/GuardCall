# GuardCall Android

Portage Android de GuardCall (équivalent CallKit).

## Fonctionnalités
- **Blocage d'appels** via `CallScreeningService` (API 24+). Bloque / rejette les numéros de la blocklist.
- **Identification spam** : fait sonner en silencieux + garde le journal.
- **UI Jetpack Compose Material3** : même esprit que l'app iOS (shield, statut, recharger, liste).
- **Stockage** `DataStore` + fichier `blocklist.json` partagé avec le service.
- **Rôle** `ROLE_CALL_SCREENING` (Android 10+) demandé à l'utilisateur.

## Structure
```
android/
  app/src/main/java/com/guardcall/
    MainActivity.kt          # UI Compose
    data/BlocklistStore.kt   # DataStore + fichier + logique normalize/isBlocked
    screening/GuardCallScreeningService.kt  # équivalent CallDirectoryHandler
    ui/theme/ + ui/screens/
  app/src/main/AndroidManifest.xml
```

## Construire l'APK
### Prérequis
- JDK 17
- Android SDK (API 34, build-tools 34+)
- Définir `ANDROID_HOME` ou `local.properties` : `sdk.dir=/chemin/vers/android-sdk`

### Debug APK
```bash
cd android
./gradlew assembleDebug
# APK -> app/build/outputs/apk/debug/app-debug.apk
```

### Release (non signé)
```bash
./gradlew assembleRelease
```

## Installer sur appareil
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Puis ouvrir GuardCall -> Activer -> choisir GuardCall comme service de filtrage
```

## Tester le filtrage
1. Ajouter un numéro dans l'app (ex. `14085555555`)
2. Depuis un autre téléphone, appeler — l'appel doit être rejeté / silencieux selon le type.
3. Vérifier `adb logcat -s GuardCallScreening`

## Permissions & Rôle
- Le service nécessite `BIND_SCREENING_SERVICE` (déclaré).
- L'utilisateur doit accorder `ROLE_CALL_SCREENING` — l'app le propose au lancement.
- Alternatives pré-Android 10 : activation manuelle dans *Téléphone → Paramètres → Filtrage d'appels*.

## Parité iOS / Android
| iOS | Android |
|-----|---------|
| `CXCallDirectoryProvider` | `CallScreeningService` |
| App Group `group.com.guardcall.shared` | `DataStore` + `files/blocklist.json` |
| `reloadExtension` | Sauvegarde auto + prochain appel filtré |
| `isIncremental` | Non pertinent (filtrage à la volée) |

## CI
Workflow `.github/workflows/android.yml` build l'APK debug automatiquement.
