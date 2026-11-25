# Smash Fest! (iOS) — Pure Kotlin/Native Architecture & Design Spec

**Date:** 2026-11-25  
**Target Repository:** `https://github.com/anhtuan421123u3/smash--fest-ios`  
**Platform:** iOS / iPadOS 15.0+ (`iosArm64`, Universal iPhone & iPad)  
**Language & Stack:** 100% Pure Kotlin (`Kotlin Multiplatform 2.1.0` — `commonMain` + `iosMain` executable via Apple `SceneKit`, `UIKit`, `AVFAudio`, `Foundation` cinterop; zero Swift/Obj-C source files)

---

## 1. Product Overview & Feature Matrix

Based on Flow Games' *Smash Fest!* (`id6748084174`), this iOS game delivers a 3D festival-themed physics destruction puzzle experience:

1. **Core Gameplay & Physics (Aim – Shoot – Smash & Chain of Destruction)**
   - Player controls a 3D festival cannon at the front of a colorful carnival arena.
   - Drag/touch aiming controls **yaw angle**, **elevation pitch**, **launch power (40%–100%)**, and **shot timing** (crucial for moving/oscillating platforms and pendulums in higher levels), rendered with a live 3D ballistic trajectory arc preview.
   - **Material & Fracture System:**
     - `FRAGILE_JAR` (Ceramic & Painted Festival Vases): Low fracture threshold; shatters into 6–8 colorful 3D shards on direct hit or domino fall, awarding chain combo points.
     - `GLASS_BOTTLE` (Crystal Bottles): Ultra-crisp fracture threshold; shatters with pentatonic crystal ASMR chimes.
     - `WOOD_CRATE` (Structural Planks & Pillars): Medium mass; supports upper jars so breaking one plank triggers a full tower collapse.
     - `HEAVY_STONE` (Anchor & Rolling Boulders): High mass; cannot be shattered directly by weak grazing hits, used to crush fragile targets underneath when dislodged.
     - `EXPLOSIVE_BARREL` (Festival Firework Keg): Detonates on impact, applying radial outward physics impulse and shattering nearby targets in a chain reaction.
   - **Skill-First Tuning:** Every level's shot budget (`ammoCount`) is solvable purely via structural weak-point targeting without requiring paid boosters. Optional boosters (Giant Ball, Rocket Shot) exist as optional helpers.
   - **Satisfying ASMR Soundscape:** Pure-Kotlin procedural 16-bit 44.1kHz PCM WAV synthesizer generating warm, low-pass softened harmonic chimes for ceramic/glass shatters, soft wooden thuds, deep stone rumbles, cannon launch pop, and confetti fanfare, paired with `UIImpactFeedbackGenerator` haptics.

2. **Levels & Progression (2,000+ Levels & Difficulty Tiers)**
   - Deterministic seeded generator (`LevelGenerator`) supporting **Levels 1 to 2,500+** (exceeding the 2,000+ requirement) across 6 structural archetypes (Pyramid Stack, Twin Towers, Bridge Span, Moving Carousel Platform, Stone Crusher Pendulum, Grand Citadel).
   - **Difficulty Classification & Coin Rewards:**
     - **Normal** (most levels): `+20 Coins` on victory.
     - **Hard** (every 5th level, e.g., 5, 10 is Super Hard so 5, 15, 20...): `+50 Coins`, tighter shot par, reinforced stone/moving elements, distinct orange festival theme.
     - **Super Hard** (every 10th level: 10, 20, 30...): `+100 Coins`, multi-stage fortress layout, purple/gold night-carnival theme.
   - **Offline Mode:** 100% playable offline with zero network dependency; when offline (or No Ads purchased), interstitial ads are suppressed automatically.

3. **Lives System (5 Lives & 30-Minute Regeneration) & Economy**
   - Maximum **5 Lives**.
   - Failing a level (running out of cannonballs while breakable targets remain) deducts **1 Life**.
   - When below 5 lives, **1 Life regenerates automatically every 30 minutes** (`1,800,000 ms`), computed deterministically from persisted epoch timestamps so offline elapsed time restores multiple lives accurately.
   - **Flexible Out-of-Lives Recovery:**
     - **Refill Full 5 Lives** using earned Coins (`100 Coins`).
     - **Watch Rewarded Ad** (simulated 5-second skippable countdown modal) to receive **+1 Life** immediately for free.

4. **Competitions & Challenges**
   - Unlocks at **Level 10** (*Carnival Champions Cup* — Single-player Daily Smash Challenge + Simulated Multiplayer Leaderboard League against 19 festival AI rivals whose trophy scores advance deterministically over tournament windows).
   - Destroying fragile jars awards Festival Stars/Cups toward leaderboard rank rewards.

5. **Monetization (Free-to-Play Mock IAP & Paced Ads)**
   - **Interstitial Ads:** Shown at moderate pacing (only every 3 completed levels starting after Level 3, never after every level, never when offline mode toggle is active or `No Ads` is owned).
   - **Rewarded Ads:** Optional +1 Life recovery or +25 Bonus Coins.
   - **In-App Purchases (Mock StoreKit UI):**
     - `No Ads` — `$9.99` (permanently disables interstitial ads + grants 250 bonus coins).
     - `Fail Offer` — `$4.99` ( contextual popup on level fail: +2 Extra Cannonballs to continue immediately + 5 Lives refill + 300 Coins).
     - Coin & Booster Packs: Starter Pack (`$1.99` — 500 Coins), Carnival Chest (`$4.99` — 1,500 Coins + 3 Giant Balls), Mega Smash Vault (`$19.99` — 7,000 Coins + 10 Boosters), Royal Festival Treasury (`$54.99` — 25,000 Coins + 35 Boosters).

---

## 2. System Architecture

```
smash-fest-ios/
├── build.gradle.kts                  # Kotlin Multiplatform 2.1.0 (jvm + iosArm64 executable)
├── settings.gradle.kts
├── gradle.properties
├── scripts/
│   ├── package_ipa.sh                # Bundles SmashFest.kexe + Info.plist + icons -> SmashFest-unsigned.ipa
│   └── generate_icons.py             # Generates festival cannon PNG app icons on macOS CI
├── iosResources/
│   └── Info.plist                    # iOS/iPadOS 15.0+, UIDeviceFamily [1,2], 4+ age-clean metadata
├── .github/workflows/
│   └── ios-ipa.yml                   # Builds & tests on macos-latest, uploads .ipa artifact, gates main
└── src/
    ├── commonMain/kotlin/com/smashfest/core/
    │   ├── model/GameModels.kt       # LevelConfig, BlockSpec, MaterialType, DifficultyTier, BoosterType
    │   ├── level/LevelGenerator.kt   # 2,000+ deterministic seeded 3D structures & difficulty tiers
    │   ├── physics/Ballistics.kt     # Cannon aim angles, launch velocity, 3D trajectory preview points, fracture rules
    │   ├── economy/LivesManager.kt   # 5-lives cap, 30-min regeneration math, coin refill & ad +1 life
    │   ├── economy/ShopCatalog.kt    # No Ads ($9.99), Fail Offer ($4.99), $1.99-$54.99 packs, ad pacing
    │   ├── league/CompetitionEngine.kt # Unlock at Lv.10, AI rival leaderboard & Daily Smash Challenge
    │   ├── audio/AsmrSynthesizer.kt  # Pure Kotlin 16-bit PCM WAV generator for ASMR shatter & festival audio
    │   └── state/GameSession.kt      # Pure state machine coordinating shots, chain combos, win/fail, persistence DTO
    ├── commonTest/kotlin/com/smashfest/core/
    │   └── SmashFestCoreTest.kt      # Comprehensive unit tests verifying all gameplay, 2000+ levels, lives, shop, audio
    └── iosMain/kotlin/com/smashfest/ios/
        ├── Main.kt                   # UIApplicationMain + @ExportObjCClass AppDelegate
        ├── storage/IosPersistence.kt # NSUserDefaults JSON/key-value persistence
        ├── audio/IosSoundEngine.kt   # AVAudioPlayer pool + UIImpactFeedbackGenerator haptics
        ├── scene/FestivalSceneController.kt # SceneKit 3D arena, cannon, rigid bodies, 3D shatter & fireworks
        └── ui/SmashFestViewController.kt    # Full UIKit HUD, Aim Touch overlay, Out-of-Lives, Fail Offer, Shop, League
```

---

## 3. CI/CD & Branch Gate Workflow (`build-ipa` -> `main`)

1. **Build & Verify on `build-ipa` / PR first:**
   - Every push to `build-ipa` (or any PR targeting `main`) triggers `.github/workflows/ios-ipa.yml` on `macos-latest`.
   - Step 1: Runs `./gradlew jvmTest` — verifies 100% of core game unit tests pass.
   - Step 2: Runs `./gradlew linkReleaseExecutableIosArm64` — compiles pure Kotlin/Native ARM64 iOS Mach-O executable (`SmashFest.kexe`).
   - Step 3: Executes `scripts/package_ipa.sh` — creates `Payload/SmashFest.app/`, embeds `Info.plist` (MinimumOSVersion `15.0`) and generated app icons, applies ad-hoc Mach-O signature (`codesign --force --sign - --timestamp=none`) for TrollStore / Sideloadly / AltStore compatibility, zips `Payload/` into `SmashFest-unsigned.ipa`, and validates the archive structure (`unzip -l` checks `Payload/SmashFest.app/SmashFest` + `Info.plist`).
   - Step 4: Uploads `SmashFest-unsigned.ipa` via `actions/upload-artifact@v4` (90-day retention) so it is immediately downloadable from the GitHub Actions run page.
2. **Gate Before `main`:**
   - Code is pushed to `build-ipa` and opened as a Pull Request first.
   - Only after the GitHub Actions run succeeds (`conclusion == success`) and `SmashFest-unsigned.ipa` is confirmed downloadable as an artifact is the PR merged into `main` (which also publishes a tagged GitHub Release containing `SmashFest-unsigned.ipa`).
