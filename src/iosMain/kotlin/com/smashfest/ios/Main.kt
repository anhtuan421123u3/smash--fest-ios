package com.smashfest.ios

import com.smashfest.ios.ui.SmashFestViewController
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectBase
import kotlinx.cinterop.autoreleasepool
import platform.Foundation.NSStringFromClass
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDelegateProtocol
import platform.UIKit.UIApplicationDelegateProtocolMeta
import platform.UIKit.UIApplicationMain
import platform.UIKit.UIResponder
import platform.UIKit.UIResponderMeta
import platform.UIKit.UIScreen
import platform.UIKit.UIWindow

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class AppDelegate @ObjCObjectBase.OverrideInit constructor() : UIResponder(), UIApplicationDelegateProtocol {
    companion object : UIResponderMeta(), UIApplicationDelegateProtocolMeta

    private var mainWindow: UIWindow? = null

    override fun window(): UIWindow? = mainWindow

    override fun setWindow(window: UIWindow?) {
        mainWindow = window
    }

    override fun application(
        application: UIApplication,
        didFinishLaunchingWithOptions: Map<Any?, *>?
    ): Boolean {
        val win = UIWindow(frame = UIScreen.mainScreen.bounds)
        win.rootViewController = SmashFestViewController()
        win.makeKeyAndVisible()
        mainWindow = win
        return true
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
fun createSmashFestViewController(): platform.UIKit.UIViewController = SmashFestViewController()

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
fun main() {
    autoreleasepool {
        UIApplicationMain(0, null, null, NSStringFromClass(AppDelegate))
    }
}
