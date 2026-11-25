# Smash Fest! (iOS) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the complete *Smash Fest!* 3D physics destruction game for iOS 15.0+ in 100% pure Kotlin/Native, verify an unsigned downloadable `.ipa` via GitHub Actions on a `build-ipa` PR branch first, and merge into `main` on `anhtuan421123u3/smash--fest-ios` only after CI `.ipa` build verification passes.

**Architecture:** A Kotlin Multiplatform (`2.1.0`) project split into `commonMain` (deterministic 2,500-level generator, ballistics & chain-destruction rules, 5-lives 30-minute regeneration economy, offline AI tournament league, IAP/ad pacing, and 16-bit PCM ASMR audio synthesizer) and `iosMain` (`iosArm64` native executable using Apple `SceneKit`, `UIKit`, `AVFAudio`, and `NSUserDefaults` via cinterop with zero Swift files).

**Tech Stack:** Kotlin Multiplatform `2.1.0`, Gradle `8.11.1`, Apple `SceneKit` / `UIKit` / `AVFAudio`, GitHub Actions (`macos-latest`, `gradle/actions/setup-gradle@v4`, `actions/upload-artifact@v4`).

**Spec:** `docs/superpowers/specs/2026-11-25-smash-fest-ios-design.md`

## Global Constraints

- Language: 100% pure Kotlin (`.kt`) source code — zero `.swift` or `.m` files.
- iOS deployment floor: iOS / iPadOS `15.0` (`MinimumOSVersion = 15.0`), Universal (`UIDeviceFamily = [1, 2]`), Age Rating `4+`.
- Economy & Progression constants: Max `5` lives, `-1` life on fail, `+1` life per `30` minutes (`1_800_000L` ms), Normal = `20` coins, Hard = `50` coins, Super Hard = `100` coins, `2,000+` playable levels, No Ads = `$9.99`, Fail Offer = `$4.99`, coin/booster packs `$1.99`–`$54.99`.
- CI/CD gate: `SmashFest-unsigned.ipa` must build and upload as a downloadable GitHub Actions artifact on branch `build-ipa` before merging commits into `main`.

---

### Task 1: Core Game Domain & TDD Suite (`commonMain` + `commonTest`)

**Files:**
- Create: `settings.gradle.kts`, `gradle.properties`, `build.gradle.kts`
- Create: `src/commonMain/kotlin/com/smashfest/core/model/GameModels.kt`
- Create: `src/commonMain/kotlin/com/smashfest/core/level/LevelGenerator.kt`
- Create: `src/commonMain/kotlin/com/smashfest/core/physics/Ballistics.kt`
- Create: `src/commonMain/kotlin/com/smashfest/core/economy/LivesManager.kt`
- Create: `src/commonMain/kotlin/com/smashfest/core/economy/ShopCatalog.kt`
- Create: `src/commonMain/kotlin/com/smashfest/core/league/CompetitionEngine.kt`
- Create: `src/commonMain/kotlin/com/smashfest/core/audio/AsmrSynthesizer.kt`
- Create: `src/commonMain/kotlin/com/smashfest/core/state/GameSession.kt`
- Test: `src/commonTest/kotlin/com/smashfest/core/SmashFestCoreTest.kt`

- [ ] **Step 1:** Write failing unit tests in `SmashFestCoreTest.kt` covering 2,000+ levels, Normal/Hard/SuperHard rewards (20/50/100 coins), cannon trajectory & chain fracture physics, 5-lives 30-min regeneration, coin/ad recovery, Lv.10 tournament unlock, IAP catalog (`$9.99` No Ads, `$4.99` Fail Offer, `$1.99`–`$54.99` packs), offline ad suppression, and RIFF/WAVE PCM header & waveform synthesis.
- [ ] **Step 2:** Run `gradle jvmTest` and confirm RED compilation failure.
- [ ] **Step 3:** Implement `commonMain` domain classes cleanly with zero external runtime dependencies.
- [ ] **Step 4:** Run `gradle jvmTest` and confirm 100% GREEN pass.

---

### Task 2: iOS Native 3D SceneKit Engine, ASMR Audio & Festival UI (`iosMain`)

**Files:**
- Create: `src/iosMain/kotlin/com/smashfest/ios/Main.kt`
- Create: `src/iosMain/kotlin/com/smashfest/ios/storage/IosPersistence.kt`
- Create: `src/iosMain/kotlin/com/smashfest/ios/audio/IosSoundEngine.kt`
- Create: `src/iosMain/kotlin/com/smashfest/ios/scene/FestivalSceneController.kt`
- Create: `src/iosMain/kotlin/com/smashfest/ios/ui/SmashFestViewController.kt`

- [ ] **Step 1:** Implement `IosPersistence` backed by `NSUserDefaults` saving/restoring `PlayerProfile` (level, coins, lives, lastLifeTimestampMs, noAdsOwned, boosters, festivalCups, offlineMode).
- [ ] **Step 2:** Implement `IosSoundEngine` wrapping `AsmrSynthesizer` WAV `NSData` buffers in pooled `AVAudioPlayer` instances plus `UIImpactFeedbackGenerator` tactile feedback.
- [ ] **Step 3:** Implement `FestivalSceneController` building the 3D festival stage, carnival banners, oscillating platforms, cannon barrel, aiming dotted trajectory spheres, physical `SCNNode` towers (fragile jars, glass bottles, wood planks, heavy stones, firework barrels), real-time velocity-delta fracture & 3D shard shatter, radial barrel explosions, and victory confetti burst.
- [ ] **Step 4:** Implement `SmashFestViewController` and `Main.kt` (`UIApplicationMain` + `@ExportObjCClass AppDelegate`) with drag-to-aim touch overlay, power meter, booster bar, Lives timer badge, Level tier banner, Win/Fail modals, Out-of-Lives modal (100 coins or Watch Ad +1), `$4.99` Fail Offer modal, IAP Shop sheet, and Lv.10 Carnival Cup Leaderboard modal.

---

### Task 3: Unsigned `.ipa` Packaging & GitHub Actions Gate (`build-ipa` -> `main`)

**Files:**
- Create: `iosResources/Info.plist`
- Create: `scripts/generate_icons.py`
- Create: `scripts/package_ipa.sh`
- Create: `.github/workflows/ios-ipa.yml`
- Create: `README.md`

- [ ] **Step 1:** Create `Info.plist` (`MinimumOSVersion` `15.0`, `UIDeviceFamily` `[1, 2]`, `CFBundleExecutable` `SmashFest`), icon generator script, and `package_ipa.sh` (`Payload/SmashFest.app`, ad-hoc `codesign -s -`, `zip -qry SmashFest-unsigned.ipa Payload`).
- [ ] **Step 2:** Create `.github/workflows/ios-ipa.yml` running `jvmTest`, `linkReleaseExecutableIosArm64`, `package_ipa.sh`, archive integrity verification, `actions/upload-artifact@v4` (`SmashFest-unsigned-ipa`), and automatic GitHub Release attachment on `main`.
- [ ] **Step 3:** Initialize `main` with minimal repo bootstrap (`README.md` + `.gitignore`), push full game & workflow to `build-ipa`, open PR `#1`, wait for GitHub Actions `macos-latest` run to succeed and verify `SmashFest-unsigned-ipa` artifact is downloadable, then merge PR `#1` into `main`.
