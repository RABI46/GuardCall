# GuardCall

GuardCall est une application de **blocage et identification d'appels indésirables**, disponible sur **iOS (CallKit)** et **Android (CallScreeningService)**. Elle partage la même logique métier et la même expérience : un bouclier, un statut clair, une liste de numéros gérée localement.

> 🇫🇷 Documentation principale en français. Voir `ANALYSE.md` pour l'audit complet.

## 📱 Plateformes

| Plateforme | Technologie | Cible | Statut |
|---|---|---|---|
| **iOS** | Swift 5.10 · SwiftUI · CallKit `CXCallDirectoryProvider` | iOS 16+ · Xcode 16 | ✅ Corrigé (voir ci-dessous) |
| **Android** | Kotlin · Jetpack Compose Material3 · `CallScreeningService` | API 26+ (Android 8) | ✅ Nouveau · APK debug via CI |

---

## 🗂️ Structure

```
.
├── GuardCall/                  # App iOS (SwiftUI)
│   ├── GuardCallApp.swift
│   ├── ContentView.swift       # UI principale (statut + liste + aide)
│   ├── Constants.swift
│   ├── BlocklistStore.swift    # App Group: UserDefaults + blocklist.json
│   ├── Assets.xcassets/
│   ├── PrivacyInfo.xcprivacy   # Manifest confidentialité (obligatoire App Store)
│   └── Info.plist
├── GuardCallDirectory/         # Extension Call Directory iOS
│   ├── CallDirectoryHandler.swift
│   ├── BlocklistLoader.swift   # Chargement partagé
│   └── Info.plist
├── GuardCallTests/             # Tests unitaires
├── android/                    # App Android (Kotlin/Compose)
│   ├── app/src/main/java/com/guardcall/
│   │   ├── MainActivity.kt
│   │   ├── data/BlocklistStore.kt
│   │   └── screening/GuardCallScreeningService.kt
│   ├── app/src/main/AndroidManifest.xml
│   └── README_ANDROID.md       # Détails build Android
├── project.yml                 # Spec XcodeGen
└── .github/workflows/
    ├── ios.yml                 # CI iOS (XcodeGen + simulateur)
    └── android.yml             # CI Android (build APK debug)
```

Ce projet utilise [XcodeGen](https://github.com/yonaskolb/XcodeGen) côté iOS : le `.xcodeproj` n'est **pas** commité, il est généré via `project.yml`.

---

## 🚀 Démarrage rapide

### iOS

**Prérequis :** Xcode 16+, [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`)

```bash
xcodegen generate
open GuardCall.xcodeproj
# Lancer sur simulateur iPhone 16 (iOS 16+)
```

**Activer l'extension après install :**
Réglages → Téléphone → Blocage d'appels et identification → activer GuardCall → revenir dans l'app → *Recharger l’extension*.

### Android (APK)

**Option A — CI (recommandé, pas besoin de SDK local) :**
1. Push sur `main` ou `arena/**` → workflow `Android CI` se lance.
2. Télécharge l'artefact **GuardCall-debug-apk** dans l'onglet *Actions* → *Android CI*.
3. `adb install -r GuardCall-debug.apk` ou transfert manuel sur téléphone (autoriser sources inconnues).

**Option B — Local :**
```bash
cd android
./gradlew assembleDebug
# APK -> android/app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Voir `android/README_ANDROID.md` pour les détails (rôle `ROLE_CALL_SCREENING`, test d'appel, logs).

---

## ✅ Correctifs appliqués (audit initial)

Tous les points de `ANALYSE.md` ont été corrigés :

**iOS — Bloquants :**
- `project.yml` : `embed: true` + `codeSign: true` pour l'extension, `bundleId` corrigé en `com.guardcall.app.calldirectory`, ajout `schemes:` explicite, `deploymentTarget` passée à **iOS 16** (au lieu de 18), configs Debug/Release.
- `ios.yml` : ajout `brew install xcodegen` + `xcodegen generate`, cache SPM, projet/scheme explicites (`GuardCall.xcodeproj` / `GuardCall`), runner `macos-14`.
- `.gitignore` complet (Xcode, SPM, Android, DerivedData).
- `PrivacyInfo.xcprivacy` + `Assets.xcassets` + `ITSAppUsesNonExemptEncryption=false`.

**iOS — Fonctionnel :**
- Refactor `GuardCallApp.swift` → `ContentView.swift` séparé, `Constants.swift`, `BlocklistStore.swift` (App Group `group.com.guardcall.shared` avec fichier + UserDefaults).
- `ContentView` : async/await `CXCallDirectoryManager`, état de chargement, deep-link Réglages, gestion d'erreurs, accessibilité, add/suppression de numéros, tri croissant, `refreshable`, onboarding.
- `CallDirectoryHandler` : chargement réel depuis App Group, vérif ordre croissant + déduplication, gestion `isIncremental` (log + best-effort), `Logger` `os_log`, persistance des erreurs pour l'app.
- `GuardCallTests` : 6 tests (constantes, round-trip, tri, déduplication, suppression, invariant CallKit).
- `.swiftlint.yml`, `deploymentTarget` 16.0, `CODE_SIGN_STYLE Automatic`.

**Android — Nouveau :**
- Projet Gradle 8.7 + AGP 8.3.2 + Kotlin 1.9.22 + Compose BOM 2024.02, minSdk 26, targetSdk 34.
- `CallScreeningService` (équivalent iOS) : bloque / silence / laisse passer selon `BlocklistStore`.
- `BlocklistStore` : `DataStore` + fichier `blocklist.json` + `loadSync()` pour le service, normalisation des numéros.
- UI Compose Material3 fidèle à iOS (shield, carte statut avec rôle, liste bloqués, dialog ajout).
- `AndroidManifest` avec `BIND_SCREENING_SERVICE`, workflow `android.yml` qui build l'APK debug.

---

## 🔐 Confidentialité

- Aucun tracking, aucune collecte. `PrivacyInfo.xcprivacy` déclare `NSPrivacyTracking = false`.
- Données stockées **uniquement localement** (App Group iOS / DataStore Android). Pas d'envoi serveur.
- Voir `GuardCall/PrivacyInfo.xcprivacy` et `android/app/src/main/res/xml/*`.

## 📄 Licence

À définir. Ajoutez un fichier `LICENSE` avant publication.

---

## 🛠️ Commandes utiles

```bash
# iOS
xcodegen generate
xcodebuild -list
xcodebuild test -project GuardCall.xcodeproj -scheme GuardCall -destination 'platform=iOS Simulator,name=iPhone 16'

# Android
cd android && ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s GuardCallScreening
```
