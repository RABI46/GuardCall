# GuardCall Android

GuardCall est l'application Android de blocage et d'identification d'appels indésirables. L'interface utilise Jetpack Compose Material 3 avec une palette GuardCall personnalisée, un thème sombre automatique et une activation guidée du rôle de filtrage Android.

## Fonctionnement

- Le service `CallScreeningService` compare chaque appel entrant à la liste locale.
- Un numéro bloqué est rejeté sans sonnerie.
- Un numéro identifié comme spam peut être laissé passer en mode silencieux sur Android 10+.
- Les numéros sont normalisés en E.164 avant d'être stockés et comparés.
- Le fichier `files/blocklist.json` est l'unique source de vérité, partagée par l'interface et le service.

## Construire

Prérequis : JDK 17 et Android SDK 34.

```bash
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK produit : `app/build/outputs/apk/debug/app-debug.apk`.

## Tester sur un appareil

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Dans GuardCall :

1. Touchez **Activer la protection**.
2. Sélectionnez GuardCall comme application de filtrage d'appels.
3. Touchez **Bloquer un numéro** et ajoutez un numéro de test.
4. Appelez depuis un autre téléphone pour vérifier le filtrage.

## Rôle et compatibilité

- `ROLE_CALL_SCREENING` est demandé par Android 10 et supérieur.
- Sur les versions antérieures, Android ouvre les réglages des applications par défaut.
- `setSilenceCall` est protégé par un contrôle d'API 29, alors que le minimum de l'application est l'API 26.
