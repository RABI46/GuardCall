# Onraccroche Android

Portage Android d'Onraccroche (équivalent CallKit).

> **Ancien nom** : l'application s'appelait *GuardCall*. Le nom affiché est
> désormais **Onraccroche** (`@string/app_name`, thème Compose
> `OnraccrocheTheme`, style `Theme.Onraccroche`).
> Le paquet applicatif reste volontairement `com.guardcall` / `com.guardcall.app` :
> l'`applicationId` est l'identité permanente d'une app sur le Play Store et sur
> l'appareil, le changer empêcherait toute mise à jour des installations
> existantes. Seul le nom visible a donc changé.

## Fonctionnalités
- **Blocage d'appels** via `CallScreeningService` (API 24+). Bloque / rejette les numéros de la blocklist.
- **Plages Begone** : import de la liste communautaire `Begone-FR.xml` — 18 règles
  couvrant les plages ARCEP du démarchage commercial (0162, 0163, 0270, 0271,
  0377, 0378, 0424, 0425, 0568, 0569, 0948, 0949) et les plages « OnOff ».
- **Identification spam** : fait sonner en silencieux + garde le journal.
- **UI Jetpack Compose Material3** : thème **bleu-blanc-rouge**, sept écrans (Accueil, Bloqués, Liste blanche, Plages, Identifiés, Historique, Réglages).
- **Stockage** fichier `blocklist.json` partagé avec le service.
- **Rôle** `ROLE_CALL_SCREENING` (Android 10+) demandé à l'utilisateur.

## Liste Begone-FR

Le fichier `app/src/main/assets/Begone-FR.xml` est la liste communautaire
[oulixalt/BegoneListeFR](https://github.com/oulixalt/BegoneListeFR), au format
`.plist` Apple :

```xml
<dict>
    <key>number</key><string>+33162######</string>
    <key>title</key><string>Anti démarchage FR</string>
</dict>
```

`#` est un joker valant pour un chiffre. Le parseur
(`data/BegoneListParser.kt`) convertit chaque motif en une **règle de plage**
(`BlockRule` = préfixe E.164 + longueur totale) : les millions de numéros
couverts ne sont jamais énumérés, ni en mémoire ni dans `blocklist.json`.

Points d'attention couverts par les tests :
- la DTD du plist pointe vers `apple.com` — un `EntityResolver` renvoie une
  source vide, donc **aucun accès réseau** n'est déclenché au filtrage ;
- le test d'appartenance exige la **longueur exacte**, une plage ne peut donc
  déborder sur un numéro d'une autre longueur ou d'un autre indicatif.

L'import se fait depuis l'écran Plages (bouton *Importer*) et est idempotent ;
*Retirer* ne supprime que les plages Begone.

## Structure
```
android/
  app/src/main/java/com/guardcall/
    MainActivity.kt                  # UI Compose (OnraccrocheTheme)
    data/BegoneListParser.kt         # lecture du .plist Begone -> BlockRule
    data/BlocklistStore.kt           # fichier blocklist.json + import Begone
    data/BlocklistMatcher.kt         # égalité E.164 + appartenance aux plages
    screening/GuardCallScreeningService.kt  # équivalent CallDirectoryHandler
    ui/theme/Theme.kt                # palette bleu-blanc-rouge
    ui/screens/MainScreen.kt
  app/src/main/assets/Begone-FR.xml
  app/src/main/AndroidManifest.xml
```

## Construire l'APK
### Prérequis
- JDK 17
- Android SDK (API 37.0, build-tools 37+)
- Gradle 9.7.1 via wrapper, Android Gradle Plugin 9.3.2 (Kotlin intégré), plugin Compose 2.3.0
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
# Puis ouvrir Onraccroche -> Activer -> choisir Onraccroche comme service de filtrage
```

## Tester le filtrage
1. Ajouter un numéro dans l'app (ex. `14085555555`), ou importer la liste Begone.
2. Depuis un autre téléphone, appeler — l'appel doit être rejeté / silencieux selon le type.
3. Vérifier `adb logcat -s OnraccrocheScreening`

## Permissions & Rôle
- Le service nécessite `BIND_SCREENING_SERVICE` (déclaré).
- L'utilisateur doit accorder `ROLE_CALL_SCREENING` — l'app le propose au lancement.
- Android 8/9 : gestion des listes uniquement ; cette application autonome ne peut pas obtenir le rôle de filtrage.
- Aucune permission dangereuse n'est demandée.

## Parité iOS / Android
| iOS | Android |
|-----|---------|
| `CXCallDirectoryProvider` | `CallScreeningService` |
| App Group `group.com.guardcall.shared` | `files/blocklist.json` |
| `reloadExtension` | Sauvegarde auto + prochain appel filtré |
| `isIncremental` | Non pertinent (filtrage à la volée) |

## CI
Workflow `.github/workflows/android.yml` : tests unitaires, lint (bloquant sur
`NewApi`, et désormais sur tout avertissement) puis build de l'APK debug.

## V2 — reconstruction

La V2 de cette branche est une nouvelle implémentation : les commits locaux
`ca6bdc4` et `8488e7e` de la session précédente n’ont pas pu être récupérés.
Leurs 109 tests ne sont donc pas présentés comme des tests récupérés ou validés.

- Sept écrans : Accueil, Bloqués, Liste blanche, Plages, Identifiés, Historique,
  Réglages. Recherche, ajout/modification/suppression et confirmations d’effacement.
- Moteur pur `domain/RuleEngine.kt` : pause → liste blanche → blocage exact →
  plage (la plus spécifique pour le motif) → silencieux → autorisation.
- `blocklist.json` conserve les champs V1 (`blocked`, `identified`, `rules`) et
  ajoute `schemaVersion: 2`, `allowed`, `settings`. La première mutation sauvegarde
  l’original dans `blocklist.v1.bak` avant remplacement atomique. Une lecture seule
  adapte la V1 en mémoire, sans réécriture. Un fichier corrompu ou d’une version
  future n’est jamais remplacé par une liste vide ; l’UI affiche l’erreur.
- Écritures sérialisées, snapshot partagé entre l’UI et le service. Le service
  dispose d’un délai indépendant de 3 secondes avant autorisation par défaut.
- Historique privé, borné à 500 entrées, stocké dans `call_history.json`, exclu
  des sauvegardes cloud **et** des transferts d’appareil. Écriture après réponse
  à Android, sur un exécuteur indépendant. Une interruption du processus peut
  perdre une dernière entrée ; ce n’est pas un journal téléphonique exhaustif.
- minSdk reste 26, mais le filtrage par une application autonome nécessite le
  rôle Android 10+ : cette application n’est pas un composeur téléphonique par
  défaut. Android 8/9 permettent de gérer les listes, pas d’activer ce filtrage.
  `setSilenceCall` reste strictement gardé par `SDK_INT >= Q`.

### Validation V2

La CI doit réussir `testDebugUnitTest`, `lintDebug`, puis `assembleDebug`.
Un succès des tests métier seuls ne valide pas les écrans Compose. Les tests
ajoutés couvrent notamment les combinaisons de priorité, migration, versions
inconnues/corruption, écritures concurrentes et limite de l’historique.

Le build CI ne remplace pas un essai sur appareil : vérifier l’octroi/retrait
du rôle, un appel bloqué, une exception whitelist sur plage Begone, le silencieux,
la pause, l’historique et une mise à jour depuis la V1. Aucune simulation de ces
scénarios sur appareil n’est revendiquée ici.
