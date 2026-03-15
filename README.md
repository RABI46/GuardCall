# 🛡️ GuardCall — Bloqueur d'appels iOS (sans Xcode requis)

## ✅ Workflow 100% GitHub (sans Mac ni Xcode)

1. **Fork ou crée ce repo** sur github.com
2. **Upload tous les fichiers** (glisser-déposer sur github.com)
3. **Push sur `main`** → GitHub Actions build automatiquement l'IPA
4. **Télécharge l'IPA** dans : Actions → ton build → Artifacts
5. **Installe via SideStore** sur ton iPhone

## Structure
```
GuardCall/
├── project.yml                    ← XcodeGen (génère le .xcodeproj auto)
├── .github/workflows/build.yml    ← CI/CD GitHub Actions
├── GuardCall/                     ← App principale SwiftUI
│   ├── GuardCallApp.swift
│   ├── BlockedNumber.swift
│   ├── FrenchSpamList.swift
│   ├── CallDirectoryService.swift
│   ├── ContentView.swift
│   ├── DashboardView.swift
│   ├── BlocklistView.swift
│   ├── SettingsView.swift
│   ├── Info.plist
│   └── GuardCall.entitlements
└── GuardCallDirectory/            ← Extension CallKit (blocage auto)
    ├── CallDirectoryHandler.swift
    ├── Info.plist
    └── GuardCallDirectory.entitlements
```

## Après installation : activer le blocage
Réglages iPhone → Téléphone → Blocage des appels → Activer GuardCallDirectory

## Base de données France
- ARCEP : tranches officielles de démarchage téléphonique
- Bloctel : service gouvernemental d'opposition au démarchage
