# GuardCall

Application de **blocage et identification d'appels indésirables**, disponible sur **iOS (CallKit)** et **Android (CallScreeningService)**. Une même logique métier — liste de numéros locale, normalisation E.164, tri croissant, déduplication — déclinée sur les deux plateformes.

| Plateforme | Stack | Cible minimale | Statut |
|---|---|---|---|
| **iOS** | Swift 5.10 · SwiftUI · CallKit `CXCallDirectoryProvider` | iOS 16 · Xcode 16 | build + tests en CI |
| **Android** | Kotlin 1.9 · Jetpack Compose Material 3 · `CallScreeningService` | API 26 (Android 8) | APK debug signé, tests + lint en CI |

Aucune donnée ne quitte l'appareil : la liste est stockée dans l'App Group (iOS) ou dans le stockage privé de l'app (Android).

---

## Structure

```
├── Shared/                     # Code Swift compilé dans l'app ET l'extension
│   ├── Models.swift            #   payload, invariant tri + déduplication
│   ├── PhoneNumber.swift       #   normalisation / validation E.164
│   ├── BlocklistStore.swift    #   persistance App Group
│   ├── DirectorySnapshot.swift #   deltas pour rechargement incrémental
│   └── Constants.swift         #   identifiants et clés
├── GuardCall/                  # App iOS (SwiftUI)
├── GuardCallDirectory/         # Extension Call Directory iOS
├── GuardCallTests/             # Tests unitaires iOS (logique partagée)
├── android/                    # App Android (Kotlin / Compose)
│   └── app/src/test/           # Tests unitaires JVM (normalisation, matching)
├── project.yml                 # Spécification XcodeGen
└── .github/workflows/          # CI iOS, Android, release
```

Côté iOS, le `.xcodeproj` n'est **pas** commité : il est généré depuis `project.yml`.

---

## Démarrage rapide

### iOS

**Prérequis :** Xcode 16+, [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`).

```bash
xcodegen generate
open GuardCall.xcodeproj        # schéma « GuardCall »
```

Puis, sur simulateur ou appareil :

```bash
xcodebuild test -project GuardCall.xcodeproj -scheme GuardCall \
  -destination 'platform=iOS Simulator,name=iPhone 16' -enableCodeCoverage YES
```

Activation : **Réglages → Téléphone → Blocage d'appels et identification → GuardCall**, puis « Recharger l'extension » dans l'app.

### Android

Le wrapper Gradle est complet (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`) :

```bash
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
# APK -> android/app/build/outputs/apk/debug/app-debug.apk
```

**Prérequis :** JDK 17, SDK Android 34 (`ANDROID_HOME` renseigné).

### Installer l'APK

1. Récupérez `GuardCall-<version>-debug.apk` — onglet **Actions → Android CI → GuardCall-debug-apk**, ou dans la [release](../../releases), ou dans `dist/` sur la branche.
2. Transférez-le sur l'appareil et ouvrez-le (autorisez « Sources inconnues »).
3. Lancez GuardCall, touchez **Activer** et sélectionnez GuardCall comme application de filtrage d'appels (rôle système `ROLE_CALL_SCREENING`).

> L'APK publié est un build **debug** : il est signé avec la clé de debug Android et s'installe normalement, mais il n'est pas destiné au Play Store (qui exige un AAB signé en release).

---

## Comportement

| Situation | iOS | Android |
|---|---|---|
| Numéro **bloqué** | appel masqué par CallKit | appel rejeté, sans sonnerie ni notification |
| Numéro **identifié** | étiquette affichée à l'appel | appel laissé passant, sonnerie coupée (Android 10+) |
| Numéro inconnu | appel normal | appel normal |

Les numéros sont saisis au format national (`01 40 85 55 12`) ou international (`+33140855512`) et **toujours** normalisés en E.164 avant stockage. La correspondance est une égalité stricte sur le E.164 normalisé — jamais une comparaison de suffixe, qui bloquerait des numéros par accident.

---

## CI

| Workflow | Déclencheur | Garanties |
|---|---|---|
| `iOS CI` | push `main`/`arena/**`, PR vers `main` | XcodeGen → build → tests sur simulateur → SwiftLint. `set -o pipefail` partout : un échec de `xcodebuild` fait échouer l'étape |
| `Android CI` | push `main`/`arena/**`, PR vers `main` | tests unitaires → `lintDebug` (bloquant) → APK → publication dans `dist/` + release |
| `Release APK` | `workflow_dispatch`, release publiée | APK attaché à un tag dérivé de `versionName` |

## Licence

[MIT](LICENSE)
