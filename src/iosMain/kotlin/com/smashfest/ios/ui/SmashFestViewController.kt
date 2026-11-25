package com.smashfest.ios.ui

import com.smashfest.core.audio.AsmrSoundType
import com.smashfest.core.economy.IapProductId
import com.smashfest.core.economy.LivesManager
import com.smashfest.core.economy.ShopCatalog
import com.smashfest.core.league.CompetitionEngine
import com.smashfest.core.model.BoosterType
import com.smashfest.core.state.GameSession
import com.smashfest.ios.audio.IosSoundEngine
import com.smashfest.ios.scene.FestivalSceneController
import com.smashfest.ios.storage.IosPersistence
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRect
import platform.Foundation.NSTimer
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIAction
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIEvent
import platform.UIKit.UIFont
import platform.UIKit.UILabel
import platform.UIKit.UITouch
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.locationInView

@OptIn(ExperimentalForeignApi::class)
private class AimOverlayView(
    frame: CValue<CGRect>,
    private val onAimChanged: (yaw: Double, pitch: Double, power: Double) -> Unit,
    private val onAimReleased: () -> Unit
) : UIView(frame = frame) {

    private var startX = 0.0
    private var startY = 0.0

    override fun touchesBegan(touches: Set<*>, withEvent: UIEvent?) {
        val touch = touches.firstOrNull() as? UITouch ?: return
        val pt = touch.locationInView(this)
        pt.useContents {
            startX = x
            startY = y
        }
        onAimChanged(0.0, 18.0, 0.80)
    }

    override fun touchesMoved(touches: Set<*>, withEvent: UIEvent?) {
        val touch = touches.firstOrNull() as? UITouch ?: return
        val pt = touch.locationInView(this)
        pt.useContents {
            val dx = x - startX
            val dy = y - startY
            val yaw = (dx / 7.5).coerceIn(-28.0, 28.0)
            val pitch = (18.0 + dy / 9.5).coerceIn(7.0, 42.0)
            val power = (0.74 + dy / 340.0).coerceIn(0.40, 1.0)
            onAimChanged(yaw, pitch, power)
        }
    }

    override fun touchesEnded(touches: Set<*>, withEvent: UIEvent?) {
        onAimReleased()
    }
}

@OptIn(ExperimentalForeignApi::class)
class SmashFestViewController : UIViewController(nibName = null, bundle = null) {

    private lateinit var sceneController: FestivalSceneController
    private lateinit var aimOverlay: AimOverlayView
    private var session: GameSession? = null
    private var selectedBooster = BoosterType.NONE
    private var modalContainer: UIView? = null
    private var regenTimer: NSTimer? = null

    private val levelBadge = UILabel()
    private val livesLabel = UILabel()
    private val coinsLabel = UILabel()
    private val ammoLabel = UILabel()
    private val comboLabel = UILabel()
    private val offlineBtn = UIButton.buttonWithType(UIButtonTypeSystem)
    private val giantBtn = UIButton.buttonWithType(UIButtonTypeSystem)
    private val rocketBtn = UIButton.buttonWithType(UIButtonTypeSystem)

    override fun viewDidLoad() {
        super.viewDidLoad()
        IosSoundEngine.warmUp()

        val bounds = view.bounds
        sceneController = FestivalSceneController(
            frame = bounds,
            onBlockSmashed = { id, _, _ -> handleBlockSmashed(id) },
            onShotSettled = { handleShotSettled() }
        )
        view.addSubview(sceneController.scnView)

        aimOverlay = AimOverlayView(
            frame = bounds,
            onAimChanged = { yaw, pitch, power ->
                sceneController.updateAim(yaw, pitch, power)
            },
            onAimReleased = { fireCurrentShot() }
        )
        aimOverlay.backgroundColor = UIColor.clearColor
        view.addSubview(aimOverlay)

        buildHud()
        startSession()

        regenTimer = NSTimer.scheduledTimerWithTimeInterval(
            interval = 1.0,
            repeats = true,
            block = { _ -> refreshHud() }
        )
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        sceneController.resize(view.bounds)
        aimOverlay.setFrame(view.bounds)
    }

    private fun startSession() {
        val loaded = IosPersistence.load()
        val now = IosPersistence.nowMs()
        if (loaded.lives <= 0) {
            session = GameSession(loaded, now)
            refreshHud()
            showOutOfLivesModal()
            return
        }
        val s = GameSession(loaded, now)
        session = s
        selectedBooster = BoosterType.NONE
        sceneController.loadLevel(s.level)
        comboLabel.text = "Drag & release anywhere to AIM & SMASH!"
        refreshHud()
    }

    private fun fireCurrentShot() {
        val s = session ?: return
        if (s.roundFinished || modalContainer != null) return
        if (s.profile.lives <= 0) {
            showOutOfLivesModal()
            return
        }
        val ok = s.fireCannon(
            yawDeg = sceneController.yawDeg,
            pitchDeg = sceneController.pitchDeg,
            power = sceneController.powerNorm,
            booster = selectedBooster
        )
        if (!ok) {
            selectedBooster = BoosterType.NONE
            if (!s.fireCannon(sceneController.yawDeg, sceneController.pitchDeg, sceneController.powerNorm, BoosterType.NONE)) {
                return
            }
        }
        sceneController.fireShot(selectedBooster)
        selectedBooster = BoosterType.NONE
        IosPersistence.save(s.profile)
        refreshHud()
    }

    private fun handleBlockSmashed(blockId: Int) {
        val s = session ?: return
        val chain = s.registerBlockDestroyed(blockId)
        if (chain >= 2) {
            comboLabel.text = "CHAIN SMASH x$chain!"
        }
        refreshHud()
        if (s.isAllTargetsCleared() && !s.roundFinished) {
            completeRound()
        }
    }

    private fun handleShotSettled() {
        val s = session ?: return
        if (s.roundFinished) return
        if (s.isAllTargetsCleared()) {
            completeRound()
        } else if (s.ammoRemaining <= 0) {
            showFailOfferOrFail()
        }
    }

    private fun completeRound() {
        val s = session ?: return
        val result = s.evaluateRoundEnd(IosPersistence.nowMs())
        IosPersistence.save(result.updatedProfile)
        refreshHud()
        if (result.won) {
            sceneController.spawnVictoryFireworks()
            val adNote = if (result.showInterstitialAd) "\n(Brief sponsor break completed)" else ""
            showModalCard(
                title = "LEVEL ${s.level.levelNumber} CLEARED!",
                subtitle = "Difficulty: ${s.level.difficulty.label}  |  ${"*".repeat(result.stars)}\n+${result.coinsEarned} Coins  |  +${result.cupsEarned} Festival Cups$adNote",
                primaryTitle = "Next Level",
                onPrimary = {
                    dismissModal()
                    startSession()
                }
            )
        }
    }

    private fun showFailOfferOrFail() {
        val s = session ?: return
        val failOffer = ShopCatalog.findProduct(IapProductId.FAIL_OFFER)
        showModalCard(
            title = "OUT OF CANNONBALLS!",
            subtitle = "${s.remainingPrimaryTargets()} targets still standing.\nGrab ${failOffer.title} (${failOffer.priceLabel}): ${failOffer.subtitle}",
            primaryTitle = "Rescue Offer ${failOffer.priceLabel}",
            onPrimary = {
                s.applyFailOfferContinue(IosPersistence.nowMs())
                IosPersistence.save(s.profile)
                dismissModal()
                refreshHud()
            },
            secondaryTitle = "Give Up (-1 Life)",
            onSecondary = {
                val res = s.evaluateRoundEnd(IosPersistence.nowMs())
                IosPersistence.save(res.updatedProfile)
                dismissModal()
                startSession()
            }
        )
    }

    private fun showOutOfLivesModal() {
        val s = session ?: return
        val canBuyCoins = s.profile.coins >= LivesManager.REFILL_COIN_COST
        showModalCard(
            title = "OUT OF LIVES (0/5)",
            subtitle = "Lives regenerate 1 every 30 minutes.\nRecover instantly with Coins or a Rewarded Ad!",
            primaryTitle = if (canBuyCoins) "Refill 5 Lives (100 Coins)" else "Watch Ad (+1 Free Life)",
            onPrimary = {
                val now = IosPersistence.nowMs()
                val updated = if (canBuyCoins) {
                    LivesManager.buyFullLivesWithCoins(s.profile, now) ?: s.profile
                } else {
                    LivesManager.grantRewardedAdLife(s.profile, now)
                }
                IosPersistence.save(updated)
                dismissModal()
                startSession()
            },
            secondaryTitle = if (canBuyCoins) "Watch Ad (+1 Free Life)" else "Open Shop",
            onSecondary = {
                if (canBuyCoins) {
                    val updated = LivesManager.grantRewardedAdLife(s.profile, IosPersistence.nowMs())
                    IosPersistence.save(updated)
                    dismissModal()
                    startSession()
                } else {
                    dismissModal()
                    showShopSheet()
                }
            }
        )
    }

    private fun showShopSheet() {
        val s = session ?: return
        val lines = ShopCatalog.allProducts.joinToString("\n") { p ->
            "${p.title} (${p.priceLabel}) — ${p.subtitle}"
        }
        val alert = UIAlertController.alertControllerWithTitle(
            title = "Festival Shop (Coins: ${s.profile.coins})",
            message = lines,
            preferredStyle = UIAlertControllerStyleAlert
        )
        ShopCatalog.allProducts.forEach { product ->
            alert.addAction(
                UIAlertAction.actionWithTitle(
                    title = "${product.title} (${product.priceLabel})",
                    style = UIAlertActionStyleDefault,
                    handler = { _ ->
                        val cur = IosPersistence.load()
                        val updated = ShopCatalog.applyPurchase(cur, product.id, IosPersistence.nowMs())
                        IosPersistence.save(updated)
                        IosSoundEngine.play(AsmrSoundType.VICTORY_FANFARE)
                        startSession()
                    }
                )
            )
        }
        alert.addAction(UIAlertAction.actionWithTitle("Close", style = UIAlertActionStyleCancel, handler = null))
        presentViewController(alert, animated = true, completion = null)
    }

    private fun showLeagueModal() {
        val profile = session?.profile ?: IosPersistence.load()
        if (!CompetitionEngine.isUnlocked(profile.currentLevel)) {
            val alert = UIAlertController.alertControllerWithTitle(
                title = "Carnival Champions Cup",
                message = "Reach Level ${CompetitionEngine.UNLOCK_LEVEL} to unlock global competitions and daily smash challenges! (Current: Lv.${profile.currentLevel})",
                preferredStyle = UIAlertControllerStyleAlert
            )
            alert.addAction(UIAlertAction.actionWithTitle("OK", style = UIAlertActionStyleCancel, handler = null))
            presentViewController(alert, animated = true, completion = null)
            return
        }
        val board = CompetitionEngine.buildLeaderboard("You", profile.festivalCups, daySeed = 20261125L)
        val topText = board.take(10).joinToString("\n") { entry ->
            val marker = if (entry.isPlayer) " <-- YOU" else ""
            "${entry.badge}  ${entry.name}: ${entry.cups} Cups$marker"
        }
        val alert = UIAlertController.alertControllerWithTitle(
            title = "Carnival Cup Standings (Lv.${profile.currentLevel})",
            message = topText,
            preferredStyle = UIAlertControllerStyleAlert
        )
        alert.addAction(UIAlertAction.actionWithTitle("Close", style = UIAlertActionStyleCancel, handler = null))
        presentViewController(alert, animated = true, completion = null)
    }

    private fun buildHud() {
        val topBar = UIView()
        topBar.translatesAutoresizingMaskIntoConstraints = false
        topBar.backgroundColor = UIColor.colorWithRed(0.08, green = 0.06, blue = 0.18, alpha = 0.78)
        topBar.layer.cornerRadius = 16.0
        view.addSubview(topBar)

        listOf(levelBadge, livesLabel, coinsLabel).forEach { lbl ->
            lbl.translatesAutoresizingMaskIntoConstraints = false
            lbl.textColor = UIColor.whiteColor
            lbl.font = UIFont.boldSystemFontOfSize(13.0)
            lbl.textAlignment = NSTextAlignmentCenter
            topBar.addSubview(lbl)
        }

        val shopBtn = makePillButton("Shop", 0xFFFFB703L) { showShopSheet() }
        val leagueBtn = makePillButton("Cup", 0xFF845EF7L) { showLeagueModal() }
        offlineBtn.translatesAutoresizingMaskIntoConstraints = false
        offlineBtn.layer.cornerRadius = 12.0
        offlineBtn.backgroundColor = UIColor.colorWithRed(0.18, green = 0.65, blue = 0.55, alpha = 0.92)
        offlineBtn.setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
        offlineBtn.titleLabel?.font = UIFont.boldSystemFontOfSize(11.0)
        offlineBtn.addAction(
            UIAction.actionWithHandler { _ ->
                val cur = IosPersistence.load()
                val toggled = cur.copy(offlineMode = !cur.offlineMode)
                IosPersistence.save(toggled)
                IosSoundEngine.play(AsmrSoundType.UI_TAP)
                startSession()
            },
            forControlEvents = UIControlEventTouchUpInside
        )

        view.addSubview(shopBtn)
        view.addSubview(leagueBtn)
        view.addSubview(offlineBtn)

        comboLabel.translatesAutoresizingMaskIntoConstraints = false
        comboLabel.textColor = UIColor.colorWithRed(1.0, green = 0.90, blue = 0.45, alpha = 1.0)
        comboLabel.font = UIFont.boldSystemFontOfSize(16.0)
        comboLabel.textAlignment = NSTextAlignmentCenter
        view.addSubview(comboLabel)

        ammoLabel.translatesAutoresizingMaskIntoConstraints = false
        ammoLabel.textColor = UIColor.whiteColor
        ammoLabel.font = UIFont.boldSystemFontOfSize(15.0)
        ammoLabel.textAlignment = NSTextAlignmentCenter
        view.addSubview(ammoLabel)

        configureBoosterButton(giantBtn, BoosterType.GIANT_BALL, 0xFFFFB703L)
        configureBoosterButton(rocketBtn, BoosterType.ROCKET_SHOT, 0xFFFF4D6DL)
        view.addSubview(giantBtn)
        view.addSubview(rocketBtn)

        val safe = view.safeAreaLayoutGuide
        NSLayoutConstraint.activateConstraints(
            listOf(
                topBar.topAnchor.constraintEqualToAnchor(safe.topAnchor, constant = 8.0),
                topBar.leadingAnchor.constraintEqualToAnchor(safe.leadingAnchor, constant = 12.0),
                topBar.trailingAnchor.constraintEqualToAnchor(safe.trailingAnchor, constant = -12.0),
                topBar.heightAnchor.constraintEqualToConstant(46.0),

                levelBadge.leadingAnchor.constraintEqualToAnchor(topBar.leadingAnchor, constant = 8.0),
                levelBadge.centerYAnchor.constraintEqualToAnchor(topBar.centerYAnchor),
                livesLabel.centerXAnchor.constraintEqualToAnchor(topBar.centerXAnchor),
                livesLabel.centerYAnchor.constraintEqualToAnchor(topBar.centerYAnchor),
                coinsLabel.trailingAnchor.constraintEqualToAnchor(topBar.trailingAnchor, constant = -10.0),
                coinsLabel.centerYAnchor.constraintEqualToAnchor(topBar.centerYAnchor),

                shopBtn.topAnchor.constraintEqualToAnchor(topBar.bottomAnchor, constant = 8.0),
                shopBtn.leadingAnchor.constraintEqualToAnchor(topBar.leadingAnchor),
                shopBtn.widthAnchor.constraintEqualToConstant(76.0),
                shopBtn.heightAnchor.constraintEqualToConstant(32.0),

                leagueBtn.topAnchor.constraintEqualToAnchor(topBar.bottomAnchor, constant = 8.0),
                leagueBtn.leadingAnchor.constraintEqualToAnchor(shopBtn.trailingAnchor, constant = 8.0),
                leagueBtn.widthAnchor.constraintEqualToConstant(76.0),
                leagueBtn.heightAnchor.constraintEqualToConstant(32.0),

                offlineBtn.topAnchor.constraintEqualToAnchor(topBar.bottomAnchor, constant = 8.0),
                offlineBtn.trailingAnchor.constraintEqualToAnchor(topBar.trailingAnchor),
                offlineBtn.widthAnchor.constraintEqualToConstant(112.0),
                offlineBtn.heightAnchor.constraintEqualToConstant(32.0),

                comboLabel.topAnchor.constraintEqualToAnchor(shopBtn.bottomAnchor, constant = 10.0),
                comboLabel.centerXAnchor.constraintEqualToAnchor(view.centerXAnchor),

                ammoLabel.bottomAnchor.constraintEqualToAnchor(safe.bottomAnchor, constant = -62.0),
                ammoLabel.centerXAnchor.constraintEqualToAnchor(view.centerXAnchor),

                giantBtn.bottomAnchor.constraintEqualToAnchor(safe.bottomAnchor, constant = -14.0),
                giantBtn.trailingAnchor.constraintEqualToAnchor(view.centerXAnchor, constant = -8.0),
                giantBtn.widthAnchor.constraintEqualToConstant(148.0),
                giantBtn.heightAnchor.constraintEqualToConstant(38.0),

                rocketBtn.bottomAnchor.constraintEqualToAnchor(safe.bottomAnchor, constant = -14.0),
                rocketBtn.leadingAnchor.constraintEqualToAnchor(view.centerXAnchor, constant = 8.0),
                rocketBtn.widthAnchor.constraintEqualToConstant(148.0),
                rocketBtn.heightAnchor.constraintEqualToConstant(38.0)
            )
        )
    }

    private fun configureBoosterButton(btn: UIButton, type: BoosterType, colorHex: Long) {
        btn.translatesAutoresizingMaskIntoConstraints = false
        btn.layer.cornerRadius = 14.0
        val r = ((colorHex ushr 16) and 0xFF) / 255.0
        val g = ((colorHex ushr 8) and 0xFF) / 255.0
        val b = (colorHex and 0xFF) / 255.0
        btn.backgroundColor = UIColor.colorWithRed(r, green = g, blue = b, alpha = 0.92)
        btn.setTitleColor(UIColor.blackColor, forState = UIControlStateNormal)
        btn.titleLabel?.font = UIFont.boldSystemFontOfSize(12.0)
        btn.addAction(
            UIAction.actionWithHandler { _ ->
                selectedBooster = if (selectedBooster == type) BoosterType.NONE else type
                IosSoundEngine.play(AsmrSoundType.UI_TAP)
                refreshHud()
            },
            forControlEvents = UIControlEventTouchUpInside
        )
    }

    private fun makePillButton(title: String, hex: Long, onClick: () -> Unit): UIButton {
        val btn = UIButton.buttonWithType(UIButtonTypeSystem)
        btn.translatesAutoresizingMaskIntoConstraints = false
        btn.layer.cornerRadius = 12.0
        val r = ((hex ushr 16) and 0xFF) / 255.0
        val g = ((hex ushr 8) and 0xFF) / 255.0
        val b = (hex and 0xFF) / 255.0
        btn.backgroundColor = UIColor.colorWithRed(r, green = g, blue = b, alpha = 0.94)
        btn.setTitle(title, forState = UIControlStateNormal)
        btn.setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
        btn.titleLabel?.font = UIFont.boldSystemFontOfSize(12.0)
        btn.addAction(UIAction.actionWithHandler { _ -> onClick() }, forControlEvents = UIControlEventTouchUpInside)
        return btn
    }

    private fun refreshHud() {
        val s = session ?: return
        val now = IosPersistence.nowMs()
        val p = LivesManager.syncRegeneration(s.profile, now)
        val msLeft = LivesManager.msUntilNextLife(p, now)
        val regenText = if (p.lives < LivesManager.MAX_LIVES) {
            val mins = (msLeft / 60_000L).toInt()
            val secs = ((msLeft % 60_000L) / 1000L).toInt()
            " (${mins}m${secs}s)"
        } else {
            ""
        }
        levelBadge.text = "Lv.${s.level.levelNumber} ${s.level.difficulty.label}"
        livesLabel.text = "Lives ${p.lives}/5$regenText"
        coinsLabel.text = "${p.coins} Coins"
        ammoLabel.text = "Cannonballs: ${s.ammoRemaining}  |  Targets Left: ${s.remainingPrimaryTargets()}"
        offlineBtn.setTitle(if (p.offlineMode) "Offline: ON" else "Offline: OFF", forState = UIControlStateNormal)
        giantBtn.setTitle(
            "${if (selectedBooster == BoosterType.GIANT_BALL) "[ON] " else ""}Giant (${p.giantBalls})",
            forState = UIControlStateNormal
        )
        rocketBtn.setTitle(
            "${if (selectedBooster == BoosterType.ROCKET_SHOT) "[ON] " else ""}Rocket (${p.rocketShots})",
            forState = UIControlStateNormal
        )
    }

    private fun showModalCard(
        title: String,
        subtitle: String,
        primaryTitle: String,
        onPrimary: () -> Unit,
        secondaryTitle: String? = null,
        onSecondary: (() -> Unit)? = null
    ) {
        dismissModal()
        val overlay = UIView(frame = view.bounds)
        overlay.backgroundColor = UIColor.colorWithRed(0.0, green = 0.0, blue = 0.0, alpha = 0.68)

        val card = UIView()
        card.translatesAutoresizingMaskIntoConstraints = false
        card.backgroundColor = UIColor.colorWithRed(0.14, green = 0.10, blue = 0.28, alpha = 0.98)
        card.layer.cornerRadius = 20.0
        overlay.addSubview(card)

        val titleLbl = UILabel()
        titleLbl.translatesAutoresizingMaskIntoConstraints = false
        titleLbl.text = title
        titleLbl.textColor = UIColor.colorWithRed(1.0, green = 0.85, blue = 0.25, alpha = 1.0)
        titleLbl.font = UIFont.boldSystemFontOfSize(20.0)
        titleLbl.textAlignment = NSTextAlignmentCenter
        card.addSubview(titleLbl)

        val subLbl = UILabel()
        subLbl.translatesAutoresizingMaskIntoConstraints = false
        subLbl.text = subtitle
        subLbl.textColor = UIColor.whiteColor
        subLbl.font = UIFont.systemFontOfSize(14.0)
        subLbl.numberOfLines = 0
        subLbl.textAlignment = NSTextAlignmentCenter
        card.addSubview(subLbl)

        val btn1 = makePillButton(primaryTitle, 0xFF20C997L, onPrimary)
        card.addSubview(btn1)

        val constraints = mutableListOf(
            card.centerXAnchor.constraintEqualToAnchor(overlay.centerXAnchor),
            card.centerYAnchor.constraintEqualToAnchor(overlay.centerYAnchor),
            card.widthAnchor.constraintEqualToConstant(320.0),

            titleLbl.topAnchor.constraintEqualToAnchor(card.topAnchor, constant = 22.0),
            titleLbl.leadingAnchor.constraintEqualToAnchor(card.leadingAnchor, constant = 16.0),
            titleLbl.trailingAnchor.constraintEqualToAnchor(card.trailingAnchor, constant = -16.0),

            subLbl.topAnchor.constraintEqualToAnchor(titleLbl.bottomAnchor, constant = 12.0),
            subLbl.leadingAnchor.constraintEqualToAnchor(card.leadingAnchor, constant = 18.0),
            subLbl.trailingAnchor.constraintEqualToAnchor(card.trailingAnchor, constant = -18.0),

            btn1.topAnchor.constraintEqualToAnchor(subLbl.bottomAnchor, constant = 18.0),
            btn1.leadingAnchor.constraintEqualToAnchor(card.leadingAnchor, constant = 22.0),
            btn1.trailingAnchor.constraintEqualToAnchor(card.trailingAnchor, constant = -22.0),
            btn1.heightAnchor.constraintEqualToConstant(44.0)
        )

        if (secondaryTitle != null && onSecondary != null) {
            val btn2 = makePillButton(secondaryTitle, 0xFF5C677DL, onSecondary)
            card.addSubview(btn2)
            constraints += listOf(
                btn2.topAnchor.constraintEqualToAnchor(btn1.bottomAnchor, constant = 10.0),
                btn2.leadingAnchor.constraintEqualToAnchor(card.leadingAnchor, constant = 22.0),
                btn2.trailingAnchor.constraintEqualToAnchor(card.trailingAnchor, constant = -22.0),
                btn2.heightAnchor.constraintEqualToConstant(40.0),
                btn2.bottomAnchor.constraintEqualToAnchor(card.bottomAnchor, constant = -20.0)
            )
        } else {
            constraints += btn1.bottomAnchor.constraintEqualToAnchor(card.bottomAnchor, constant = -22.0)
        }

        NSLayoutConstraint.activateConstraints(constraints)
        view.addSubview(overlay)
        modalContainer = overlay
    }

    private fun dismissModal() {
        modalContainer?.removeFromSuperview()
        modalContainer = null
    }
}
