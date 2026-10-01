# GuardCall — Analyse complète du dépôt
**Date :** 01/10/2026 (UTC) • **Branche :** `arena/01a0f91d-guardcall` @ `64b5ff5` • **Audit :** statique complet

---

## 0. En bref

GuardCall est un **squelette Jour-0** d’app CallKit Call Directory, pas un produit livrable. L’idée (XcodeGen + SwiftUI + CallKit) est propre, mais :

* **Ne bloque / n’identifie rien de réel** — numéros codés en dur pour la démo.
* **CI cassée par conception** — le workflow `ios.yml` est le template brut GitHub qui attend un `.xcodeproj` commité, alors que ce dépôt génère le projet via `xcodegen generate` et ne le commite pas. Chaque PR échoue à `xcodebuild -list -json`.
* **Presque pas de logique** — 1 fichier Swift pour l’app, 1 pour l’extension, 1 test bidon. Pas de persistance, pas d’App Group, pas d’UI de gestion, pas de mise à jour incrémentale.
* **Fichiers d’hygiène manquants** — pas de `.gitignore`, pas de `PrivacyInfo.xcprivacy`, pas d’`Assets.xcassets`, pas d’icône.

**Verdict :** Excellente base pour démarrer un projet CallKit avec XcodeGen. Il faut ~2-3 jours pour passer en TestFlight crédible.

> Légende sévérité : 🔴 Bloquant — l’app ne tourne pas / refus App Store • 🟠 Majeur — manque fonctionnel • 🟡 Moyen — dette technique • 🟢 Mineur — finition

---

## 1. Vue d’ensemble

| Dimension | Observation |
|---|---|
| **Objet** | `CXCallDirectoryProvider` pour blocage & identification d’appelants |
| **Langage** | Swift 5.10, cycle de vie SwiftUI (`@main struct GuardCallApp: App`) |
| **Cibles** | 3 : `GuardCall` (application) + `GuardCallDirectory` (app-extension) + `GuardCallTests` (tests unitaires) |
| **Build** | **XcodeGen** via `project.yml` — pas de `.xcodeproj` commité (intention correcte, mais la CI l’ignore) |
| **Déploiement min** | iOS 18.0, Xcode 16.0, `IPHONEOS_DEPLOYMENT_TARGET=18.0` dans `options` et dans chaque `settings.base` (redondant mais cohérent) |
| **Volume** | ~75 lignes app + ~45 lignes extension + 12 lignes test ≈ **132 LOC** hors plist/xml |
| **Historique Git** | 1 commit (`64b5ff5 Add iOS starter workflow…`) sur `main`, branche `arena/*` identique et propre |
| **CI** | `.github/workflows/ios.yml` — template stock, `macos-latest`, `build-for-testing` → `test-without-building` |

Arborescence :

```
.
├── GuardCall/
│   ├── GuardCallApp.swift              # App + ContentView dans le même fichier
│   ├── GuardCall.entitlements          # VIDE
│   └── Info.plist                      # Générique, pas de display name
├── GuardCallDirectory/
│   ├── CallDirectoryHandler.swift      # CXCallDirectoryProvider
│   ├── GuardCallDirectory.entitlements # VIDE
│   └── Info.plist                      # NSExtensionPointIdentifier OK
├── GuardCallTests/
│   └── GuardCallTests.swift            # 1 XCTAssertTrue bidon
├── project.yml                         # Spec XcodeGen
└── .github/workflows/ios.yml           # Non customisé
```

---

## 2. `project.yml` — Définition du build

**Globalement sain, minimal. 3 corrections à faire.**

| Vérif | Résultat | Détail |
|---|---|---|
| `bundleIdPrefix` + `PRODUCT_BUNDLE_IDENTIFIER` par cible | 🟡 | `com.guardcall.app` + `com.guardcall.GuardCallDirectory` **ne sont pas hiérarchiques**. Convention Apple : `com.guardcall.app` + `com.guardcall.app.calldirectory`. Ça compile mais sèmera la confusion dans App Store Connect / provisioning. |
| Duplication `deploymentTarget` | 🟢 | Défini dans `options` **et** dans chaque `settings.base`. Inoffensif, choisir un seul endroit. |
| Signature désactivée | 🟠 | `CODE_SIGN_IDENTITY=""`, `CODE_SIGNING_REQUIRED=NO`, `CODE_SIGNING_ALLOWED=NO` — OK pour Simulateur CI mais **à retirer pour archive/TestFlight**. Il faudra `CODE_SIGN_STYLE: Automatic` + `DEVELOPMENT_TEAM`. |
| `createIntermediateGroups: true` | 🟢 | Bien. |
| Types de cibles | ✅ | `application` / `app-extension` / `bundle.unit-test` — correct. |
| **Embarquement de l’extension** | 🔴 | `GuardCall` déclare `dependencies: - target: GuardCallDirectory` mais **sans `embed: true` / `codeSignOnCopy: true`**. XcodeGen va lier mais peut ne pas embarquer + signer le `.appex`. Sans ça, `GuardCall.app/PlugIns/` reste vide au runtime. |
| Schéma partagé manquant | 🟠 | Pas de bloc `schemes:`. XcodeGen en génère un, mais le hack Ruby de la CI (`xcodebuild -list -json … targets[0]`) choisit le premier par ordre alphabétique — fragile. Déclarer un `schemes:` explicite. |
| Pas de séparation `DEBUG`/`RELEASE` | 🟡 | Tout en `base`. Pas de `SWIFT_OPTIMIZATION_LEVEL`, `SWIFT_COMPILATION_MODE`, `ENABLE_TESTABILITY`. |

**Patch recommandé (`project.yml`) :**

```yaml
targets:
  GuardCall:
    dependencies:
      - target: GuardCallDirectory
        embed: true
        codeSign: true
schemes:
  GuardCall:
    build: { targets: { GuardCall: all, GuardCallDirectory: all } }
    run: { config: Debug }
    test: { targets: [GuardCallTests] }
```

---

## 3. Cible App — `GuardCall/GuardCallApp.swift` (73 lignes)

### 3.1 Points positifs
* Entrée SwiftUI propre (`WindowGroup`), SF Symbol `shield.fill` cohérent avec le branding “guard”.
* Usage correct de CallKit : `CXCallDirectoryManager.sharedInstance.getEnabledStatusForExtension(withIdentifier:)` + `reloadExtension(withIdentifier:)`.
* Mise à jour d’état dispatchée sur le main thread — correct (callbacks CallKit pas garantis main).

### 3.2 Problèmes

| # | Sév. | Localisation | Description |
|---|---|---|---|
| A1 | 🟠 | `ContentView` dans `GuardCallApp.swift` | Un seul fichier viole le SRP. Séparer en `ContentView.swift` + `GuardCallApp.swift`. Bloque les previews & tests. |
| A2 | 🟠 | `withIdentifier: "com.guardcall.GuardCallDirectory"` (×2) | **String codée en dur dupliquée**. Extraire en `Constants.extensionIdentifier` dérivée de `project.yml`. Risque de divergence. |
| A3 | 🟡 | `statusMessage = "Checking status..."` | Pas de localisation — littéraux non traduits. Prévoir `Localizable.strings`. |
| A4 | 🟡 | `onAppear { checkExtensionStatus() }` | Vérif une seule fois. Pas d’écoute de `Notification` quand l’utilisateur active l’extension dans Réglages → Téléphone → Blocage. Il faut relancer l’app pour voir le changement. |
| A5 | 🟡 | Pas de typage d’erreur | `error.localizedDescription` affiché brut. Mapper `CXErrorCodeCallDirectoryManager` vers un message clair + deep-link Réglages (`UIApplication.openSettingsURLString`). |
| A6 | 🟢 | Pas d’état de chargement | `reloadExtension` sans spinner ; bouton spammable. Ajouter `@State private var isReloading`. |
| A7 | 🟡 | Pas de Concurrency | Callbacks au lieu de `async/await` : `try await CXCallDirectoryManager.sharedInstance.reloadExtension(...)` (iOS 18 le permet). |
| A8 | 🟡 | Pas d’accessibilité | Pas de `.accessibilityIdentifier` / `accessibilityLabel` sur le bouton critique. |
| A9 | 🟢 | Pas de `#Preview` | Macro preview SwiftUI manquante. |
| A10 | 🟡 | Pas de navigation / réglages | Une vraie app CallKit a besoin : gestion de liste, liste blanche, import Contacts, toggle. Le `VStack` actuel est démo uniquement. |

### 3.3 `GuardCall/Info.plist`
* Template minimal + `UILaunchScreen: {}` (OK iOS 14+).
* **Manque :** `CFBundleDisplayName` (“GuardCall”), `ITSAppUsesNonExemptEncryption` (requis App Store depuis Xcode 15).

### 3.4 `GuardCall/GuardCall.entitlements`
```xml
<dict></dict>  // VIDE
```
* Correct pour l’**app hôte** — les extensions Call Directory n’exigent pas d’entitlement côté hôte. Si vous ajoutez un App Group pour partager la blocklist, il faudra le déclarer ici et côté extension.

---

## 4. Cible Extension — `GuardCallDirectory/CallDirectoryHandler.swift` (45 lignes)

### 4.1 Positif
* Sous-classe correcte `CXCallDirectoryProvider`, `context.delegate = self`, `context.completeRequest()` après ajout.
* Respecte l’invariant **ordre strictement croissant** : trie les deux tableaux avant `addBlockingEntry(withNextSequentialPhoneNumber:)` / `addIdentificationEntry(...)`.
* Utilise `CXCallDirectoryPhoneNumber` (Int64) avec littéraux `1_408_555_5555` — idiomatique.

### 4.2 Problèmes

| # | Sév. | Détail |
|---|---|---|
| E1 | 🔴 | **Données de démo uniquement** : `phoneNumbers: [1_408_555_5555]`, `entries: [(1_877_555_5555, "Spam")]`. Aucun chargement. En prod, lire depuis le conteneur partagé (App Group `UserDefaults` / fichier). Comportement actuel = no-op. |
| E2 | 🟠 | **Pas de gestion incrémentale** : `beginRequest(with:)` ignore `context.isIncremental`. En reload incrémental, il faut ajouter/supprimer uniquement les deltas via `removeBlockingEntry`. L’implé actuelle ré-ajoute tout → erreur `duplicateEntries` et extension désactivée par l’OS. |
| E3 | 🟡 | **Erreurs ignorées** : si `addBlockingEntry` lève (duplicata/ordre), rien n’est fait. Il faut `context.cancelRequest(withError:)` en cas d’échec. |
| E4 | 🟡 | **`requestFailed` vide** : juste `// Log error`. Logger via `os.Logger` et écrire dans le conteneur partagé pour affichage côté app. |
| E5 | 🟡 | Label `"Spam"` non localisé. |
| E6 | 🟢 | Pas de garde sur le volume. Limite système ~1M numéros ; prévoir chunk + `hasRemainingEntries` à terme. |

### 4.3 `Info.plist` extension
* Correct : `NSExtensionPointIdentifier: com.apple.callkit.call-directory`, `NSExtensionPrincipalClass: $(PRODUCT_MODULE_NAME).CallDirectoryHandler`.

### 4.4 Entitlements extension
* Vide — **correct**. Si App Group ajouté :
```xml
<key>com.apple.security.application-groups</key>
<array><string>group.com.guardcall.shared</string></array>
```
dans **les deux** cibles.

---

## 5. Tests — `GuardCallTests/GuardCallTests.swift`

```swift
func testExample() throws { XCTAssertTrue(true) }
```
* 🟠 **Placeholder.** 0 couverture : ordre de tri, contrat API blocking, bundle ID, mapping de statut.
* Manque : tests du handler avec mock de `CXCallDirectoryExtensionContext`.

---

## 6. CI/CD — `.github/workflows/ios.yml`

> Template brut “iOS starter workflow” — **non édité**.

| Problème | Sév. | Pourquoi ça casse |
|---|---|---|
| **Pas d’étape `xcodegen generate`** | 🔴 | `xcodebuild -list -json` échoue avec `The project does not exist` car `.xcodeproj` n’est jamais généré (et c’est voulu de ne pas le committer). Workflow DOA. |
| One-liner Ruby pour choisir le scheme | 🟠 | `ruby -e "require 'json'; …['project']['targets'][0]"` — fragile, dépend de `ruby` + ordre alphabétique. Mettre `scheme: GuardCall` en dur. |
| Parsing `xcrun xctrace` | 🟡 | `grep -oE 'iPhone.*?[^\(]+' … sed …` casse sur noms localisés. Utiliser `-destination 'platform=iOS Simulator,name=iPhone 16'`. |
| Pas de cache | 🟡 | Pas de `actions/cache` pour `xcodegen`, SPM, DerivedData. Lent. |
| Filtre branches `main` seul | 🟡 | `push` sur `arena/*` ne lance pas la CI — on ne voit le rouge qu’au merge. |
| `xcodegen --version` non épinglée | 🟡 | `xcodegen` n’est pas préinstallé sur `macos-latest`. Il faut `brew install xcodegen`. |

**Fix minimal :**

```yaml
steps:
  - uses: actions/checkout@v4
  - name: Installer XcodeGen
    run: brew install xcodegen
  - name: Générer le projet
    run: xcodegen generate
  - name: Build & Test
    run: |
      xcodebuild test \
        -project GuardCall.xcodeproj -scheme GuardCall \
        -destination 'platform=iOS Simulator,name=iPhone 16'
```

---

## 7. Hygiène & DX

| Élément | État | Note |
|---|---|---|
| `.gitignore` | 🔴 Manquant | À ajouter : `*.xcodeproj/`, `DerivedData/`, `.DS_Store`, `*.xcuserdata` |
| `PrivacyInfo.xcprivacy` | 🔴 Manquant | Obligatoire App Store depuis mai 2024. Même sans tracking, manifest requis. |
| `Assets.xcassets` / `AppIcon` | 🟠 Manquant | Icône générique, échec HIG. |
| `README` | 🟢 Bien | Instructions xcodegen correctes. Ajouter badge CI. |
| SwiftLint / SwiftFormat | 🟡 | Pas de config. Ajouter `.swiftlint.yml` tôt. |
| Licence | 🟡 | Pas de `LICENSE`. |

---

## 8. Sécurité, confidentialité, App Store

* **Pas de secrets** — propre, pas de clés en dur.
* **Pas de tracking** — donc pas d’ATT, mais prévoir `NSPrivacyTracking = NO` dans le manifest.
* **Guideline 4.5.2** : les apps de blocage d’appels doivent décrire clairement leur comportement. Prévoir onboarding + explication “Réglages → Téléphone → Blocage d’appels → Activer GuardCall”.
* **Cible iOS 18.0** — 🟡 agressif. Coupe ~80% du parc encore en iOS 17 fin 2025. Sauf besoin d’une API iOS 18 (aucune pour `CXCallDirectory`), cibler iOS 16.

---

## 9. Architecture manquante & Roadmap

```
Actuel : App --(codé en dur)--> Extension
Souhaité : App ↔ App Group (UserDefaults/Fichier) ↔ Extension
                 ↕
              CloudKit / API → sync blocklist
                 ↕
             BackgroundTasks → reload périodique
```

**Manques priorisés :**

1. **P0 — Bloquer pour de vrai :** conteneur App Group + fichier `blocklist.json` + chargement dans le handler.
2. **P0 — Réparer CI & `.gitignore`** pour que les PR passent au vert.
3. **P1 — Support incrémental** (`isIncremental` + `remove*Entry`) sinon crash `duplicateEntries`.
4. **P1 — UI de réglages :** ajouter/supprimer numéro, tester un appel, expliquer l’activation.
5. **P1 — Remontée d’erreurs :** erreur `reloadExtension` → bannière + deep-link Réglages.
6. **P2 — Source de données :** import CSV local ou API distante + `BGTaskScheduler`.
7. **P2 — Tests :** tests d’ordre + erreurs de reload.

---

## 10. Matrice des risques

| Risque | Proba | Impact | Score |
|---|---|---|---|
| CI reste rouge (xcodeproj manquant) | Élevée | Bloque les contributeurs | 🔴 |
| Extension non embarquée (XcodeGen) | Élevée | App installée mais extension invisible dans Réglages | 🔴 |
| Reload incrémental → duplicate → extension désactivée | Moyenne | L’utilisateur croit l’app cassée | 🟠 |
| Numéros codés en dur livrés en TestFlight | Élevée | Reviewer = “non fonctionnel” | 🟠 |
| Pas d’AppIcon → rejet “fonctionnalité minimale” (4.2) | Moyenne | Rejet App Store | 🟠 |
| iOS 18 only → testeurs ne peuvent pas installer | Moyenne | Bassin bêta réduit | 🟡 |

---

## 11. Prochains commits suggérés (petits, relisables)

1. `chore: ajout .gitignore, PrivacyInfo.xcprivacy, Assets stub`
2. `fix(ci): installe xcodegen et génère le projet avant xcodebuild; fige le scheme`
3. `fix(xcodegen): embarque GuardCallDirectory avec codeSignOnCopy`
4. `refactor: extrait ContentView, constantes, localisation`
5. `feat: App Group + loader JSON blocklist + support incrémental`
6. `test: ajoute tests d’ordre + erreurs reload`
7. `feat: UI réglages + onboarding deep-link`

---

## 12. Verdict chiffré

* **Qualité de code :** 6/10 — Swift idiomatique, mais niveau squelette.
* **Complétude :** 2/10 — non livrable.
* **Santé CI :** 1/10 — workflow template non fonctionnel pour XcodeGen.
* **Couverture tests :** 0/10 — test bidon uniquement.

**Plus gros gain immédiat :** Réparer CI + embarquement (30 min) → puis partage de données via App Group (2-3 h) → MVP crédible.

---

*Tu veux que j’applique les correctifs P0 (`.gitignore`, `PrivacyInfo`, `project.yml` embed + scheme, et `ios.yml` avec étape xcodegen) sur cette branche `arena/01a0f91d-guardcall` ? Dis-moi et je pousse.*
