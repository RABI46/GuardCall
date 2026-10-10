# GuardCall — Analyse du dépôt

**Date :** 10/10/2026 (UTC)
**Branche analysée :** `arena/2080f5db-guardcall` (= `main` @ `64b5ff5`, arbre de travail propre)
**Comparée à :** `arena/01a0f91d-guardcall` @ `12c6532`, tag `v1.0` @ `6c211aa`
**Méthode :** audit statique + vérifications exécutées (voir §1). Aucun outil Apple ni SDK Android n'est disponible dans cet environnement : **rien n'a été compilé ni exécuté** côté produit (§10).

---

## 0. Verdict

> Le dépôt contient **deux projets très différents selon la branche**, et la branche par défaut `main` est la **version la plus ancienne et la plus cassée**. Le code réellement abouti (iOS complet + portage Android) vit sur `arena/01a0f91d-guardcall`, qui a 17 commits d'avance sur `main`, **n'a jamais fait l'objet d'une PR** et n'a **jamais été fusionné**. La release publique `v1.0` (avec un APK de 16 Mo) a été publiée depuis cette branche non fusionnée.
>
> Par ailleurs, la CI iOS **ne peut pas échouer** : les étapes de build et de test avalent le code de retour de `xcodebuild`. Le badge vert iOS ne prouve rien.

---

## 1. Vérifications exécutées (et leur résultat)

| Vérification | Commande | Résultat |
|---|---|---|
| Fichiers versionnés sur `main` | `git ls-files` | **10 fichiers, 344 lignes** au total |
| `.xcodeproj` versionné ? | `git ls-files \| grep -i xcodeproj` | **aucun** (normal, XcodeGen) |
| `xcodegen` appelé dans la CI de `main` ? | `grep -niE "xcodegen\|brew" .github/workflows/ios.yml` | **aucune occurrence** |
| `.gitignore` sur `main` ? | `git ls-files \| grep -i gitignore` | **aucun** |
| Runs CI sur `main` | `gh run list --branch main` | **4 runs, 4 échecs** (dernier : 25/04/2026, 9 s) |
| Runs CI sur `arena/01a0f91d` | `gh run list --branch …` | verts (iOS 25 s · Android 2 m 42 · Release 3 m 41) |
| Écart `main` ↔ branche de dev | `gh api …/compare` | `status: ahead`, **ahead_by 17, behind_by 0** → fast-forward possible |
| Validité des plists | `plistlib.load()` sur tous les `.plist`/`.entitlements`/`.xcprivacy` | **tous OK** (4 sur `main`, 5 sur la branche de dev) |
| Validité des JSON d'assets | `json.load()` | **3/3 OK** |
| Validité des YAML | `yaml.safe_load()` sur `project.yml` + workflows | **tous OK** ; `main` : 3 cibles, **0 scheme** ; branche dev : 3 cibles, 1 scheme |
| **Le pipe de la CI iOS avale-t-il l'échec ?** | reproduction bash du motif exact (`… 2>&1 \| tee /tmp/build.log \| tail -n 200`) avec un `xcodebuild` factice renvoyant **65** | **code de sortie de l'étape = 0** ; `PIPESTATUS[0]` affiche bien `65` mais n'est jamais exploité. Avec `set -o pipefail`, l'étape sort en **1** |
| Wrapper Gradle complet ? | `gh api …/git/trees/…?recursive=1` | `android/gradlew` = `100755` (exécutable) mais **`gradle/wrapper/gradle-wrapper.jar` absent** |
| Tests Android ? | `find android -type d -name "test*"` | **aucun répertoire de test** |
| Doublons de types Swift | `grep "^struct\|^final class\|^enum"` | `BlockedEntry`, `IdentificationEntry`, `BlocklistPayload` déclarés **2×** (app + extension) |

---

## 2. Finding n°1 — le dépôt est schizophrène entre ses branches

| Réf | Contenu | Statut |
|---|---|---|
| `main` (`64b5ff5`, 25 commits) | Squelette iOS Jour-0 : 10 fichiers, 1 écran SwiftUI, 2 numéros codés en dur, 1 test `XCTAssertTrue(true)` | branche par défaut, **CI en échec** |
| `arena/01a0f91d-guardcall` (`12c6532`) | 60 fichiers · Swift 663 l. · Kotlin 466 l. · iOS complet (App Group, blocklist persistée, UI, 6 tests) **+ app Android Compose** + 3 workflows + `.gitignore` + SwiftLint + privacy manifest | **jamais fusionnée, aucune PR** |
| tag `v1.0` (`6c211aa`) | Idem, 3 commits avant le tip | Release publique **« GuardCall v1.0 — iOS + Android »** publiée le 01/10/2026, asset `GuardCall-debug.apk` (16 151 447 o., 1 téléchargement) |
| PR #1 | « Codebase Verification Report », mergée le 23/04/2026 | C'est elle qui a créé le squelette actuel de `main` |
| Issue #2 | « test issue from sandbox » (`hello`) | **toujours ouverte** |

**Conséquences concrètes :**

1. Quiconque clone le dépôt obtient la version cassée. Le `README.md` de `main` annonce *iOS 18.0+ / Xcode 16* alors que la version aboutie vise *iOS 16+* — la documentation décrit un projet qui n'est pas celui qui a été livré.
2. `main` est **strictement en retard** (`behind_by: 0`) : la fusion est un fast-forward, il n'y a **aucun conflit à résoudre**. C'est le correctif le moins cher de tout ce rapport.
3. Le dépôt contient **deux rapports d'audit déjà périmés** : `ANALYSE.md` (FR, 15,5 Ko) et `ANALYSIS.md` (EN, 18 Ko) — même plan, mêmes sections, l'un traduit de l'autre. Pire, ils sont datés de la branche `@ 64b5ff5` (l'ancien squelette) alors qu'ils ont été commités **avec** le gros correctif : `ANALYSE.md` §3 annonce « `GuardCallApp.swift` (73 lignes) » et §4 « `CallDirectoryHandler.swift` (45 lignes) », alors que le même commit contient **10** et **110** lignes. Ces deux fichiers induisent en erreur et devraient être supprimés ou réécrits.

---

## 3. Anomalies bloquantes (P0)

### 3.1 La CI iOS ne peut pas échouer — faux vert confirmé
`.github/workflows/ios.yml` (branche de dev), lignes 79-80 et 98-99 :

```bash
xcodebuild build-for-testing … 2>&1 | tee /tmp/build.log | tail -n 200
echo "build exit code: ${PIPESTATUS[0]}"
cat /tmp/build.log | grep -i -A2 -B2 "error" | head -n 100 || true
```

Sans `set -o pipefail`, le code de sortie d'une étape est celui de la **dernière** commande du pipeline. La reproduction exécutée (§1) donne : `xcodebuild` → 65, **étape → 0**. Le `PIPESTATUS[0]` est correctement affiché dans le log… puis ignoré, et la dernière commande est un `|| true`.

Preuve corroborante : `gh run view 36927247371` → job **« ✓ Build & Test (XcodeGen + Simulator) in 25s »**. Un cycle `brew`/XcodeGen + `xcodebuild build-for-testing` + `test-without-building` sur simulateur ne tient pas en 25 secondes. *Les logs détaillés n'ont pas pu être lus (l'hôte `results-receiver.actions.githubusercontent.com` n'est pas joignable depuis cet environnement) — la durée et la reproduction du motif shell sont les éléments de preuve.*

À noter : le workflow **Android**, lui, fait les choses correctement (`GRADLE_EXIT=$?` puis `exit $GRADLE_EXIT`, lignes 87-113, + `if-no-files-found: error` sur l'artefact APK, ligne 161). C'est donc bien la partie iOS qui est fautive.

**Correctif :** `set -o pipefail` en tête des deux étapes, ou `exit ${PIPESTATUS[0]}` en fin d'étape.

### 3.2 La CI de `main` est morte (vérifié, pas déduit)
`gh run list --branch main` → **4 runs, 4 échecs**, dont le dernier (25/04/2026, 9 s) sur le commit actuel. Cause lisible dans le fichier : `.github/workflows/ios.yml` est le *starter workflow* GitHub brut — il n'installe **jamais** XcodeGen et ne génère **jamais** `GuardCall.xcodeproj`, alors qu'aucun `.xcodeproj` n'est versionné. L'étape « Set Default Scheme » exécute donc `xcodebuild -list -json` dans un répertoire sans projet.

### 3.3 Bundle ID de l'extension invalide (sur `main`)
`main/project.yml` : app = `com.guardcall.app` (l.27), extension = `com.guardcall.GuardCallDirectory` (l.43). Une extension **doit** être préfixée par le bundle ID de l'app conteneur suivi d'un point, sinon l'installation/la validation App Store échoue (« App extension bundle identifiers must have a prefix consisting of their containing application's bundle identifier followed by a '.' ») [1](https://stackoverflow.com/questions/30536413/after-removing-app-extension-still-get-app-installation-failed-error-this-app-c). Le même identifiant est recodé en dur deux fois dans `GuardCall/GuardCallApp.swift` (l.41 et l.62).
**Déjà corrigé sur la branche de dev** (`com.guardcall.app.calldirectory`, centralisé dans `Constants.swift`).

### 3.4 Android : crash runtime garanti sur API 26-28
`android/app/build.gradle.kts` déclare `minSdk = 26` (l.12), mais `GuardCallScreeningService.kt` appelle `setSilenceCall(...)` (l.46 et l.54) **sans aucun garde `Build.VERSION.SDK_INT`**. Or `setSilenceCall` n'existe qu'à partir de l'**API 29** [1](https://developer.android.com/reference/android/telecom/CallScreeningService.CallResponse.Builder). Sur Android 8.x/9, tout appel « identifié » (non bloqué, labellisé) déclenche un `NoSuchMethodError` dans `onScreenCall`.
**Correctif :** `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setSilenceCall(true)`.

---

## 4. Anomalies fonctionnelles (P1)

### 4.1 iOS — aucune validation E.164 des numéros
`ContentView.swift` l.197-199 :

```swift
private var sanitizedNumber: Int64? {
    let digits = newNumberText.filter { $0.isNumber }
    return Int64(digits)
}
```

et le champ de saisie propose en placeholder **« Ex. 014085551234 »** (l.159) — un format national français, **sans indicatif pays**. CallKit exige des numéros au format E.164 avec indicatif pays ; une entrée invalide fait échouer le rechargement entier de l'annuaire (`requestFailed`) [3](https://stackoverflow.com/questions/40673904/call-blocking-feature-in-ios-10). Effets de bord supplémentaires : `Int64(digits)` **supprime silencieusement les zéros de tête**, et renvoie `nil` au-delà de 19 chiffres → le bouton « Ajouter » reste désactivé sans aucune explication pour l'utilisateur.
Aucune normalisation E.164 n'existe non plus côté extension (`BlocklistLoader` renvoie les `Int64` tels quels).

### 4.2 Android — sur-blocage par suffixe
`BlocklistStore.kt` l.90 :

```kotlin
return payload.blocked.any { norm.endsWith(it.phoneNumber) || it.phoneNumber.endsWith(norm) }
```

La correspondance est **bidirectionnelle par suffixe** : bloquer `1234` bloque *tous* les numéros se terminant par `1234`. Combiné à `normalize()` qui ne conserve que 4 à 15 chiffres (`takeLast(15)`, l.85), un utilisateur peut bloquer des milliers d'appels légitimes par accident. Même logique pour l'identification (l.95).

### 4.3 iOS — le rechargement incrémental est une coquille vide
`CallDirectoryHandler.swift` l.69-86 : `addBlockingIncremental` et `addIdentificationIncremental` se terminent par `_ = number` / `_ = entries`. Elles sont déclarées `throws` sans jamais lever. Un rechargement incrémental **n'applique donc rien** (ni ajout, ni suppression) et se contente de logger un warning. Le commentaire l'admet (« journal de diffs non implémenté »), mais l'API publique donne l'illusion du contraire.

### 4.4 Modèles dupliqués entre les deux cibles iOS
`BlockedEntry`, `IdentificationEntry` et `BlocklistPayload` sont déclarés **deux fois** (`GuardCall/BlocklistStore.swift` et `GuardCallDirectory/BlocklistLoader.swift`), sans cible partagée. La dérive a déjà commencé : le payload par défaut de l'app contient **4 entrées** (`BlocklistPayload.default`), celui de l'extension **2** (`BlocklistPayload.fallback`). Idem pour les constantes : `group.com.guardcall.shared` apparaît à **5 endroits** (`Constants.swift`, `BlocklistLoader.swift`, les 2 `.entitlements`, le test).
**Correctif :** une cible `GuardCallShared` (framework statique) référencée par les deux cibles.

### 4.5 Android — la couche données a deux vérités
- `load()` (suspend) lit **DataStore** en priorité ; `loadSync()` — celle qu'utilise le service de filtrage (`GuardCallScreeningService.kt` l.27) — lit **le fichier puis SharedPreferences** et **ignore totalement DataStore**.
- `save()` écrit les trois, mais **avale l'échec d'écriture du fichier** : `try { blocklistFile.writeText(encoded) } catch (_: Exception) {}` (l.51). Si l'écriture disque échoue, l'UI affiche la liste réelle pendant que le service de filtrage retombe sur `BlocklistPayload.DEFAULT`. **Quatre** `catch (_: Exception)` silencieux dans le fichier (l.24, 32, 39, 51 — compte vérifié par `grep -c`).
- `loadSync()` fait de l'**E/S fichier sur le thread appelant** dans `onScreenCall` → risque d'ANR.

### 4.6 Android — permissions largement surdimensionnées
`AndroidManifest.xml` demande `READ_PHONE_STATE`, `READ_CALL_LOG`, `CALL_PHONE`, `ANSWER_PHONE_CALLS` et `READ_CONTACTS`. Le code n'utilise **aucune** de ces APIs : seul le rôle `ROLE_CALL_SCREENING` est requis (déjà correctement demandé via `RoleManager` dans `MainScreen.kt`). C'est à la fois de la surface d'attaque inutile et un motif de friction à la revue Play Store.

---

## 5. CI/CD — autres constats

| Élément | Constat |
|---|---|
| `ios.yml` l.39, 103 | « Show project info » et « Lint Swift » en `continue-on-error: true` |
| SwiftLint | `.swiftlint.yml` existe mais **aucune étape n'installe SwiftLint** ; l'étape teste `command -v swiftlint` → **toujours sautée**. De plus, `included:` liste `android/app/src` (du Kotlin) : SwiftLint n'y trouvera jamais de Swift |
| `ios.yml` | Cache SPM configuré alors que le projet **n'a aucune dépendance SPM** (pas de `Package.resolved`) → étape inutile |
| `android.yml` l.165 | `./gradlew testDebugUnitTest \|\| echo "no tests yet — ok"` → masque tout échec de test (et il n'y a de toute façon aucun test) |
| `android.yml` | `setup-android@v3` en `continue-on-error: true`, puis étapes de debug verbeuses (`ls -R app/build \| head -n 500`) : traces de tâtonnements à nettoyer |
| `release.yml` | Déclenché sur `push: branches: ["arena/**"]` avec `paths: [".github/workflows/release.yml"]` + `workflow_dispatch` + `release` ; le tag par défaut est **codé en dur à `v1.0`** → toute exécution future écrasera l'asset de la v1.0 (`--clobber`) |
| Artefact publié | La release `v1.0` embarque un **APK debug non signé de release**, sans AAB → non publiable sur Play Store |
| Aucun | Pas de `CODEOWNERS`, pas de templates de PR/issue, pas de scan de dépendances, pas de workflow de release iOS |

---

## 6. Qualité de code

**iOS**
- `ContentView.swift` l.186-195 : l'**état de l'UI est déduit de chaînes localisées** (`statusMessage == Constants.Localization.extensionEnabled`). Un changement de texte ou une localisation casse l'icône et la couleur. Il faut un `enum ExtensionStatus`.
- `ContentView.swift` l.202-206 : `formatted(_:)` est un stub qui renvoie son argument, avec le commentaire « petit formatage lisible » → code mort.
- `deleteBlocked` (l.279-286) utilise `try?` : un échec de suppression est invisible pour l'utilisateur.
- `BlocklistStore.save()` écrit aussi dans `UserDefaults.standard` comme « fallback simulateur » (l.75) : un chemin de test embarqué en production.
- `BlocklistPayload.default` contient **2 numéros réellement bloqués** par défaut : au premier rechargement, l'app pousse du blocage de démonstration dans CallKit.
- Points positifs réels : `os.Logger` avec `privacy:` explicite, `context.delegate` posé, déduplication + tri croissant avant `addBlockingEntry` (l'exigence CallKit est respectée), erreurs d'extension republiées dans l'App Group pour affichage côté app, wrappers `async/await` propres, `accessibilityIdentifier` posés sur les contrôles, `PrivacyInfo.xcprivacy` rédigé (UserDefaults `CA92.1`, FileTimestamp `C617.1`).

**Android**
- `proguard-rules.pro` protège correctement les modèles Gson (`-keep class com.guardcall.data.** { *; }`) — le risque R8/Gson est **couvert**.
- Compose Material3 correct (`HorizontalDivider` après le correctif `61f7fcd`), thème et icônes présents, `RoleManager` utilisé proprement.
- `PhoneNumberUtils.formatForDisplay` est un stub avec `// TODO: libphonenumber si besoin` ; l'objet `PhoneNumberUtils` n'est d'ailleurs **jamais appelé** (`sanitize` non plus : `BlocklistStore` refiltre les chiffres lui-même) → code mort.

**Configuration iOS**
- `project.yml` (branche dev) l.35-36 : `excludes: [PrivacyInfo.xcprivacy]` et **aucune section `resources:`** → le manifeste de confidentialité **n'entre jamais dans le bundle**, alors que l'app utilise `UserDefaults` (raison `CA92.1` déclarée dans le fichier). C'est un motif de rejet App Store depuis 2024.
- `project.yml` fixe `TEST_HOST` manuellement pour la cible de tests sans `BUNDLE_LOADER` — XcodeGen complète normalement seul, mais **à confirmer au premier `xcodegen generate`** (non vérifiable ici).

---

## 7. Tests

| | iOS | Android |
|---|---|---|
| Nombre | **6** (`GuardCallTests.swift`, 62 l.) | **0** (aucun dossier `src/test` ni `src/androidTest`) |
| Couvrent | `Constants`, round-trip du store, tri, déduplication, suppression, invariant de tri | — |
| Ne couvrent pas | `CallDirectoryHandler` (le tri/dédup **critique CallKit**), `BlocklistLoader` (cible extension), `ContentView`, aucun test UI | tout |
| Fiabilité | Les 6 tests partagent `UserDefaults.standard` **sans `setUp`/`tearDown`** de remise à zéro → dépendants de l'ordre d'exécution | — |
| Couverture | `gatherCoverageData: true` dans le scheme, **aucun seuil** | — |
| Exécution réelle en CI | Non prouvable : l'étape de test ne peut pas échouer (§3.1) | Étape masquée par `\|\| echo` |

Le point le plus préoccupant : **la seule logique où une erreur a un effet visible pour l'utilisateur** (l'ordre strictement croissant et la déduplication avant `addBlockingEntry`, sous peine d'`entriesOutOfOrder` et d'échec global du rechargement) est **dupliquée dans l'extension et non testée**.

---

## 8. Hygiène du dépôt

- **Pas de `LICENSE`** sur `main` (la branche de dev en ajoute une section au README, mais `licenseInfo` reste `null` côté GitHub) → juridiquement, le code n'est pas réutilisable.
- `main` n'a **pas de `.gitignore`** → le premier `xcodegen generate` suivi d'un `git add .` committerait le `.xcodeproj`.
- Description du dépôt **vide** ; 0 étoile ; 1 issue de test ouverte.
- `ANALYSE.md` + `ANALYSIS.md` : doublon FR/EN **périmé** (§2.3).
- Historique de `main` bruité par des commits d'interface GitHub (« Delete workflows directory », « Rename project.yml to GuardCall/project.yml »).
- 17 commits de la branche de dev sont presque tous des allers-retours de debug CI (`fix(ci): debug workflows`, `ci(android): ajoute post log en issue si échec`, `ci(android): capture HEAD + grep FAILURE…`) → à squasher avant fusion.

---

## 9. Ce que je n'ai **pas** pu vérifier (à faire sur un poste équipé)

Aucun des outils nécessaires n'est présent dans cet environnement (`xcodegen`, `xcodebuild`, `swift`, `swiftc`, `plutil` : absents ; ni JDK, ni Gradle, ni SDK Android). Je n'ai donc **ni compilé, ni exécuté les tests, ni lancé SwiftLint**. Restent à confirmer sur une machine outillée :

```bash
# iOS
brew install xcodegen && xcodegen generate
xcodebuild -list -project GuardCall.xcodeproj
xcodebuild test -project GuardCall.xcodeproj -scheme GuardCall \
  -destination 'platform=iOS Simulator,name=iPhone 16' -enableCodeCoverage YES
swiftlint lint

# Android
cd android && gradle wrapper --gradle-version 8.7 && ./gradlew assembleDebug lintDebug
```

Les logs détaillés des runs GitHub Actions sont inaccessibles depuis cet environnement (hôte hors liste blanche) : la conclusion « faux vert » repose sur la reproduction du motif shell + la durée de 25 s, pas sur la lecture du log.

---

## 10. Plan d'action priorisé

**Immédiat (1 commit chacun, tous mécaniques)**
1. **Fusionner `arena/01a0f91d-guardcall` dans `main`** — fast-forward pur (`behind_by: 0`), 17 commits. Cela règle d'un coup §3.2, §3.3, l'absence de `.gitignore`, et aligne le README sur le code.
2. **Réparer la CI iOS** : `set -o pipefail` (ou `exit ${PIPESTATUS[0]}`) dans les étapes *Build for testing* et *Run tests*. Sans ça, aucune des corrections suivantes n'est vérifiable.
3. **Garder `setSilenceCall`** derrière `Build.VERSION.SDK_INT >= Q`.
4. Supprimer `ANALYSE.md` et `ANALYSIS.md` (périmés), fermer l'issue #2, ajouter un `LICENSE`.

**Court terme**
5. Validation **E.164** à la saisie (indicatif pays obligatoire, message d'erreur explicite) + normalisation partagée app/extension.
6. Android : remplacer le match par suffixe par une **égalité sur numéro normalisé E.164** ; ne plus avaler les exceptions de `BlocklistStore` ; sortir `loadSync()` du thread principal.
7. iOS : sortir `PrivacyInfo.xcprivacy` de `excludes:` et l'ajouter aux ressources ; implémenter (ou retirer franchement) le chemin incrémental ; remplacer les comparaisons de chaînes localisées par un `enum`.
8. Cible **`GuardCallShared`** pour les modèles et constantes dupliqués.
9. Android : retirer `READ_PHONE_STATE`, `READ_CALL_LOG`, `CALL_PHONE`, `ANSWER_PHONE_CALLS`, `READ_CONTACTS`.

**Structurel**
10. Tests sur le chemin critique : `CallDirectoryHandler` (tri/dédup/ordre bloqués-avant-identification) + `BlocklistLoader`, avec remise à zéro du store en `setUp`.
11. Installer réellement SwiftLint en CI (ou retirer l'étape et le fichier) ; retirer `android/app/src` de son `included:` ; supprimer le cache SPM inutile.
12. Paramétrer le tag de `release.yml` (plus de `v1.0` par défaut), publier un **AAB signé** plutôt qu'un APK debug, ajouter `CODEOWNERS` et un template de PR.

---

### Sources externes citées
1. Préfixe obligatoire du bundle ID d'une extension — [stackoverflow.com/questions/30536413](https://stackoverflow.com/questions/30536413/after-removing-app-extension-still-get-app-installation-failed-error-this-app-c)
2. `setSilenceCall` ajouté en API 29 — [developer.android.com/reference/android/telecom/CallScreeningService.CallResponse.Builder](https://developer.android.com/reference/android/telecom/CallScreeningService.CallResponse.Builder)
3. Numéros CallKit : ordre croissant + indicatif pays — [stackoverflow.com/questions/40673904](https://stackoverflow.com/questions/40673904/call-blocking-feature-in-ios-10)
