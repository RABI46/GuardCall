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
- **UI Jetpack Compose Material3** : thème **bleu-blanc-rouge** (shield, statut, recharger, liste).
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

L'import se fait depuis l'écran principal (bouton *Importer*) et est idempotent ;
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
# Puis ouvrir Onraccroche -> Activer -> choisir Onraccroche comme service de filtrage
```

## Tester le filtrage
1. Ajouter un numéro dans l'app (ex. `14085555555`), ou importer la liste Begone.
2. Depuis un autre téléphone, appeler — l'appel doit être rejeté / silencieux selon le type.
3. Vérifier `adb logcat -s OnraccrocheScreening`

## Permissions & Rôle
- Le service nécessite `BIND_SCREENING_SERVICE` (déclaré).
- L'utilisateur doit accorder `ROLE_CALL_SCREENING` — l'app le propose au lancement.
- Alternatives pré-Android 10 : activation manuelle dans *Téléphone → Paramètres → Filtrage d'appels*.
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
`NewApi`) puis build de l'APK debug.
