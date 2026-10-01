# GuardCall — Repository Analysis
**Date:** 2026-10-01 (UTC) • **Branch:** `arena/01a0f91d-guardcall` @ `64b5ff5` • **Scope:** full static audit

---

## 0. TL;DR

GuardCall is a **scaffold / Day-0 skeleton** for a CallKit Call Directory app, not a shippable product. It compiles in theory (`project.yml` → XcodeGen → Xcode 16 / iOS 18) but:

* **Will not block/identify real spam** — numbers are hard-coded demo data.
* **CI is broken by design** — the checked-in `ios.yml` is the unmodified GitHub “iOS starter workflow” that expects a committed `.xcodeproj`, while this repo *intentionally* git-ignores the project and requires `xcodegen generate`. Every PR will fail at `xcodebuild -list -json`.
* **Almost no app logic** — 1 Swift file for the app, 1 for the extension, 1 dummy test. No data persistence, no App Group, no settings UI, no incremental updates.
* **No hygiene files** — no `.gitignore`, no `.swiftlint.yml`, no `PrivacyInfo.xcprivacy`, no `Assets.xcassets`, no AppIcon.

**Verdict:** Good starting point for an XcodeGen-based CallKit project. Needs ~2-3 days to become TestFlight-ready.

> Severity legend: 🔴 Blocker — app won't run / won't pass review • 🟠 High — major functional gap • 🟡 Medium — quality/tech-debt • 🟢 Low — polish

---

## 1. At a Glance

| Dimension | Observation |
|---|---|
| **Purpose** | CallKit `CXCallDirectoryProvider` for blocking & caller ID |
| **Language** | Swift 5.10, SwiftUI lifecycle (`@main struct GuardCallApp: App`) |
| **Targets** | 3: `GuardCall` (application) + `GuardCallDirectory` (app-extension) + `GuardCallTests` (unit-test bundle) |
| **Build system** | **XcodeGen** via `project.yml` — no `.xcodeproj` committed (correct intent, but CI doesn't know it) |
| **Min deployment** | iOS 18.0, Xcode 16.0, `IPHONEOS_DEPLOYMENT_TARGET=18.0` in both `project.yml` `options` and per-target `settings.base` (redundant but consistent) |
| **LOC** | ~75 lines app + ~45 lines extension + 12 lines test ≈ **132 LOC** (excluding plist/xml) |
| **Git history** | 1 commit (`64b5ff5 Add iOS starter workflow…`) on `main`, branch `arena/*` is clean replica |
| **CI** | `.github/workflows/ios.yml` — stock template, `macos-latest`, `build-for-testing` → `test-without-building` |

File tree:

```
.
├── GuardCall/
│   ├── GuardCallApp.swift              # App + ContentView in one file
│   ├── GuardCall.entitlements          # EMPTY dict
│   └── Info.plist                      # Generic, no display name / no BG modes
├── GuardCallDirectory/
│   ├── CallDirectoryHandler.swift      # CXCallDirectoryProvider
│   ├── GuardCallDirectory.entitlements # EMPTY dict
│   └── Info.plist                      # NSExtensionPointIdentifier OK
├── GuardCallTests/
│   └── GuardCallTests.swift            # 1 dummy XCTAssertTrue
├── project.yml                         # XcodeGen spec
└── .github/workflows/ios.yml           # Uncustomized starter
```

---

## 2. `project.yml` — Build Definition

**Overall: sound, minimal. 3 fixable issues.**

```yaml
name: GuardCall
options:
  bundleIdPrefix: com.guardcall
  deploymentTarget: { iOS: "18.0" }
  xcodeVersion: "16.0"
```

| Check | Result | Note |
|---|---|---|
| `bundleIdPrefix` + per-target `PRODUCT_BUNDLE_IDENTIFIER` | 🟡 | `com.guardcall.app` + `com.guardcall.GuardCallDirectory` are **not hierarchically related**. Apple's convention is `com.guardcall.app` + `com.guardcall.app.calldirectory` (extension must share prefix of parent). Current naming works but will confuse App Store Connect / provisioning. |
| `deploymentTarget` duplication | 🟢 | Defined in `options` **and** per-target `settings.base.IPHONEOS_DEPLOYMENT_TARGET`. Harmless redundancy; pick one. |
| Code signing disabled | 🟠 | `CODE_SIGN_IDENTITY=""`, `CODE_SIGNING_REQUIRED=NO`, `CODE_SIGNING_ALLOWED=NO` — fine for Simulator CI but **must be removed for archive/TestFlight**. Needs `CODE_SIGN_STYLE: Automatic` + `DEVELOPMENT_TEAM` override. |
| `createIntermediateGroups: true` | 🟢 | Good. |
| Targets typed correctly | ✅ | `application` / `app-extension` / `bundle.unit-test` — correct. |
| **Extension embedding** | 🔴 | `GuardCall` declares `dependencies: - target: GuardCallDirectory` but **no `embed: true` / `codeSignOnCopy: true`**. XcodeGen will link but may not embed + codesign the `.appex` without `embed: true`. Stock Xcode template auto-embeds; XcodeGen does not. **Fix required or `GuardCall.app/PlugIns/` stays empty at runtime.** |
| Missing shared scheme | 🟠 | No `schemes:` block. XcodeGen will auto-generate, but CI's `Set Default Scheme` ruby hack (`xcodebuild -list -json … targets[0]`) will pick the **first alphabetically**, which is `GuardCall` today but fragile. Declare explicit `schemes:` instead. |
| No `settings` for `DEBUG`/`RELEASE` separation | 🟡 | All settings in `base`. No `SWIFT_OPTIMIZATION_LEVEL`, `SWIFT_COMPILATION_MODE`, or `ENABLE_TESTABILITY`. |
| No `breakpoints`, `fileGroups`, `attributes` | 🟢 | Fine for now. |

**Recommended `project.yml` patch (delta):**

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

## 3. App Target — `GuardCall/GuardCallApp.swift` (73 lines)

### 3.1 Positive
* Clean SwiftUI entry (`WindowGroup`), SF Symbol `shield.fill` aligns with “guard” branding.
* Correct CallKit Manager usage: `CXCallDirectoryManager.sharedInstance.getEnabledStatusForExtension(withIdentifier:)` + `reloadExtension(withIdentifier:)`.
* State update dispatched to main queue — correct (CallKit callbacks are not guaranteed main-thread).

### 3.2 Issues

| # | Severity | Location | Description |
|---|---|---|---|
| A1 | 🟠 | `ContentView` inside `GuardCallApp.swift` | Single-file violates SRP. Should be `ContentView.swift` + `GuardCallApp.swift`. Hinders previews & tests. |
| A2 | 🟠 | `withIdentifier: "com.guardcall.GuardCallDirectory"` (×2) | **Hard-coded string** duplicated. Must be constant e.g. `Constants.extensionIdentifier` and derived from `project.yml`. Drift risk if bundle ID changes. |
| A3 | 🟡 | `statusMessage = "Checking status..."` | No localization — string literals not in `Localizable.strings`. No `LocalizedStringKey`. |
| A4 | 🟡 | `onAppear { checkExtensionStatus() }` | Only checks once. No `onReceive(Notification …)` for Settings → Phone → Call Blocking toggle. User must restart app to see new state. |
| A5 | 🟡 | No error typing | `error.localizedDescription` shown raw to user. Should map `CXErrorCodeCallDirectoryManager` to friendly copy + Settings deep-link (`UIApplication.openSettingsURLString`). |
| A6 | 🟢 | No loading state | `reloadExtension` shows no spinner; button can be spam-tapped. Add `@State private var isReloading`. |
| A7 | 🟡 | No `.task` / Swift Concurrency | Uses callback hell. Modernize to `async/await`: `try await CXCallDirectoryManager.sharedInstance.reloadExtension(...)`. Requires iOS 18 anyway. |
| A8 | 🟡 | No accessibility | No `.accessibilityIdentifier`, no `accessibilityLabel` on critical button. |
| A9 | 🟢 | No `#Preview` | Missing SwiftUI preview macro. |
| A10 | 🟡 | No navigation / settings | Real CallKit apps need: list management, allowlist, import from contacts, toggle. Current `VStack` is demo-only. |
| A11 | 🟢 | `@State` usage OK but `@MainActor` missing | `checkExtensionStatus` could be `@MainActor` instead of explicit `DispatchQueue.main.async`. |

### 3.3 `GuardCall/Info.plist`
* Minimal template + `UILaunchScreen: {}` (correct for iOS 14+).
* **Missing:** `CFBundleDisplayName` (“GuardCall”), `ITSAppUsesNonExemptEncryption` (required for App Store since Xcode 15), `NSUserDefaults` suite, `BGTaskSchedulerPermittedIdentifiers` if background reload needed.
* Not missing `com.apple.developer.callkit` — main app doesn't need it.

### 3.4 `GuardCall/GuardCall.entitlements`
```xml
<dict></dict>  // EMPTY
```
* Correct for the **host app** — Call Directory extensions need no host entitlement. Some teams add `com.apple.developer.app-groups` here to share block lists with extension; currently not present, so App Group path (see §4) impossible. Flag as **design decision, not bug**, but note future need.

---

## 4. Extension Target — `GuardCallDirectory/CallDirectoryHandler.swift` (45 lines)

### 4.1 Positive
* Correct subclass `CXCallDirectoryProvider`, sets `context.delegate = self`, calls `context.completeRequest()` after adding entries.
* Correctly respects **strictly ascending order** invariant: sorts both blocking and identification arrays before `addBlockingEntry(withNextSequentialPhoneNumber:)` / `addIdentificationEntry(...)`.
* Uses `CXCallDirectoryPhoneNumber` (Int64) with underscore literals `1_408_555_5555` — idiomatic.

### 4.2 Issues

| # | Severity | Detail |
|---|---|---|
| E1 | 🔴 | **Demo data only:** `phoneNumbers: [1_408_555_5555]`, `entries: [(1_877_555_5555, "Spam")]`. No loader. Production must read from shared container (App Group `UserDefaults` / file). Current behavior is effectively a no-op. |
| E2 | 🟠 | **No incremental handling:** `beginRequest(with:)` ignores `context.isIncremental`. When system does incremental reload, handler must only add/remove deltas via `removeBlockingEntry` / etc. Current impl re-adds everything, which will throw `CXErrorCodeCallDirectoryManagerError.duplicateEntries` on incremental. Needs `if context.isIncremental { … } else { … }`. |
| E3 | 🟡 | **No error propagation:** If `addBlockingEntry` throws (duplicate/ordering), code ignores it. Should `context.cancelRequest(withError:)` on failure. |
| E4 | 🟡 | **`requestFailed` is empty:** Just `// Log error`. Should log to `os_log` / `Logger`, and for debugging, write to shared container so host app can display last error. |
| E5 | 🟢 | No `os.Logger` import — silent failures. |
| E6 | 🟡 | Hard-coded label `"Spam"` not localized. Should be `NSLocalizedString`. |
| E7 | 🟡 | No batch size guard. System limit is ~ 1M numbers; should chunk and check `context.hasRemainingEntries`? (Not needed now but plan). |
| E8 | 🟢 | No phone number validation (E.164, NANP). Accepts any Int64. |

### 4.3 `GuardCallDirectory/Info.plist`
* Correct: `NSExtensionPointIdentifier: com.apple.callkit.call-directory`, `NSExtensionPrincipalClass: $(PRODUCT_MODULE_NAME).CallDirectoryHandler`.
* **Missing:** `NSExtension` → `NSExtensionAttributes` not needed for call-directory; fine.

### 4.4 `GuardCallDirectory/GuardCallDirectory.entitlements`
* Empty dict — **correct**. Call Directory extensions historically require no entitlements. If you add App Groups, add:
```xml
<key>com.apple.security.application-groups</key>
<array><string>group.com.guardcall.shared</string></array>
```
to **both** targets.

---

## 5. Tests — `GuardCallTests/GuardCallTests.swift`

```swift
func testExample() throws { XCTAssertTrue(true) }
```
* 🟠 **Placeholder only.** 0 coverage of: sort order, blocking API contract, bundle identifier, status mapping.
* Missing: extension tests via `CXCallDirectoryExtensionContext` mock, `XCTExpectFailure` for duplicate ordering.
* `project.yml` test target depends on `GuardCall` — correct, but no host app specified for `bundle.unit-test` (Xcode will infer, but explicit `host` is cleaner).

---

## 6. CI/CD — `.github/workflows/ios.yml`

> Stock “iOS starter workflow” generated by GitHub `actions/starter-workflows` — **not edited**.

| Problem | Severity | Why it breaks |
|---|---|---|
| **No `xcodegen generate` step** | 🔴 | `xcodebuild -list -json` will fail with `The project does not exist` because `.xcodeproj` is never generated (and is correctly not committed). Entire workflow is DOA. |
| Ruby one-liner to pick scheme | 🟠 | `ruby -e "require 'json'; …['project']['targets'][0]"` — depends on `ruby` + `json`. Fragile; will pick wrong target if order changes. Should be explicit `scheme: GuardCall`. |
| `macos-latest` + `xcrun xctrace` device parsing | 🟡 | Shell is brittle: `grep -oE 'iPhone.*?[^\(]+' … sed …` breaks on localized names / watchOS. Use `xcodebuild -destination 'platform=iOS Simulator,name=iPhone 15'` or `xcpretty`. |
| No caching | 🟡 | No `actions/cache` for `xcodegen`, SPM, DerivedData. Slow. |
| Branches filter `main` only | 🟡 | PRs from `arena/*` or feature branches won't trigger? Actually `pull_request: branches: [main]` triggers for PRs *targeting* main regardless of source, so OK, but `push` to arena branches won't CI — hidden until merge. |
| Two separate `xcodebuild` invocations | 🟡 | `build-for-testing` + `test-without-building` is correct but could be one `xcodebuild test` with less ceremony now. |
| No code signing override | 🟢 | Relies on `CODE_SIGNING_ALLOWED=NO` in `project.yml`, so it passes, but no comment explaining. |
| No `xcodegen --version` pin | 🟡 | Will break if `xcodegen` not installed on runner (it isn't by default on `macos-latest`). Needs `brew install xcodegen` or `yonaskolb/XcodeGen` action. |

**Minimal fix:**

```yaml
steps:
  - uses: actions/checkout@v4
  - name: Install XcodeGen
    run: brew install xcodegen
  - name: Generate project
    run: xcodegen generate
  - name: Build & Test
    run: |
      xcodebuild test \
        -project GuardCall.xcodeproj -scheme GuardCall \
        -destination 'platform=iOS Simulator,name=iPhone 16'
```

---

## 7. Hygiene & DX

| Item | Status | Note |
|---|---|---|
| `.gitignore` | 🔴 Missing | Need `*.xcodeproj/`, `DerivedData/`, `.DS_Store`, `*.xcuserdata`, `*.xcuserstate`. Without it, `xcodegen generate` dirties git. |
| `PrivacyInfo.xcprivacy` | 🔴 Missing | Required since May 2024 App Store. Even if no tracking, need manifest. |
| `Assets.xcassets` / `AppIcon` | 🟠 Missing | App will show generic icon, fails HIG. |
| `README` | 🟢 Good | Correct xcodegen instructions. Add CI badge + `brew install xcodegen` note. |
| `project.yml` drift | 🟡 | README says `xcodegen generate` creates `GuardCall.xcodeproj` but CI doesn't. Contradiction. |
| SwiftLint / SwiftFormat | 🟡 | No config. Add `.swiftlint.yml` early to prevent style drift. |
| Commit signing / hooks | 🟢 | `commit-msg` hook present (Arena infra). |
| License | 🟡 | No `LICENSE`. Needed for open-source. |

---

## 8. Security, Privacy, App Store

* **No secrets** — clean; no hard-coded keys (helful).
* **No tracking** — currently none, so ATT not needed, but add `NSPrivacyTracking = NO` to privacy manifest proactively.
* **CallKit disclosure:** App Store Review guideline 4.5.2 requires call-blocking apps to clearly describe behavior. Need `NSUserDefaults` prompt copy and settings onboarding.
* **No App Attest / DeviceCheck** — not needed for MVP, but consider if block lists become server-driven (anti-tamper).
* **Deployment target iOS 18.0** — 🟡 aggressive. Drops ~80% of 2025 installed base still on iOS 17. Unless you need `CXCallDirectory` iOS 18 features (none), target iOS 16 or 15. Xcode 16 can still build for lower.

---

## 9. Architecture Gaps & Roadmap

```
Current: App --(hard-coded)--> Extension
Wanted:  App ↔ App Group (UserDefaults/File) ↔ Extension
               ↕
            CloudKit / API → blocklist sync
               ↕
           BackgroundTasks → periodic reload
```

**Missing pieces prioritized:**

1. **P0 — Make it actually block:** App Group container + file `blocklist.json` + handler loading it.
2. **P0 — Fix CI & `.gitignore`** so PRs turn green.
3. **P1 — Incremental support** (`isIncremental` + `remove*Entry`) or else random `duplicateEntries` crashes after OS incremental reloads.
4. **P1 — Settings UI:** add/remove number, test call, explain “Settings → Phone → Call Blocking & Identification → Enable GuardCall”.
5. **P1 — Error surfacing:** `reloadExtension` error → banner + Settings deep-link.
6. **P2 — Data source:** local CSV import or remote API; consider `BGTaskScheduler` + `CXCallDirectoryManager.reloadExtension`.
7. **P2 — Tests:** handler unit tests with sorted-order assertion, integration test via `XCTAttachment`.

---

## 10. Risk Matrix

| Risk | Likelihood | Impact | Score |
|---|---|---|---|
| CI stays red (xcodeproj missing) | High | Blocks contributors | 🔴 |
| Extension not embedded (XcodeGen) | High | App installs but extension never appears in Settings | 🔴 |
| Incremental reload → duplicate error → extension disabled by OS | Medium | User thinks app broken | 🟠 |
| Hard-coded numbers shipped to TestFlight | High | Reviewer flags as non-functional | 🟠 |
| No AppIcon / launch screen polish → rejection for “minimum functionality” (4.2) | Medium | App Store rejection | 🟠 |
| iOS 18-only → TestFlight testers can't install | Medium | Reduced beta pool | 🟡 |

---

## 11. Suggested Next Commits (small, reviewable)

1. `chore: add .gitignore, PrivacyInfo.xcprivacy, Assets stub`
2. `fix(ci): install xcodegen and generate project before xcodebuild; pin scheme`
3. `fix(xcodegen): embed GuardCallDirectory with codeSignOnCopy`
4. `refactor: extract ContentView, constants, localization`
5. `feat: App Group + JSON blocklist loader + incremental support`
6. `test: add handler ordering + reload error tests`
7. `feat: settings UI + onboarding deep-link`

---

## 12. Verdict

* **Code quality:** 6/10 — idiomatic Swift, but scaffold-level.
* **Completeness:** 2/10 — not shippable.
* **CI health:** 1/10 — workflow is stock template, non-functional for XcodeGen.
* **Test coverage:** 0/10 — dummy test only.

**Biggest win:** Fix CI + embedding (30 min) → then implement App Group data sharing (2-3 h) → you have a credible MVP to iterate.

---

*Want me to apply the P0 fixes (`.gitignore`, `PrivacyInfo`, `project.yml` embed + scheme, and `ios.yml` xcodegen step) on this branch? Say the word and I'll push `arena/01a0f91d-guardcall`.*
