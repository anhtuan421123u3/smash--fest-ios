plugins {
    kotlin("multiplatform") version "2.0.21"
}

group = "com.smashfest"
version = "1.0.0"

kotlin {
    jvm()

    iosArm64 {
        binaries {
            executable {
                entryPoint = "com.smashfest.ios.main"
                baseName = "SmashFest"
                freeCompilerArgs += listOf(
                    "-linker-option", "-framework", "-linker-option", "UIKit",
                    "-linker-option", "-framework", "-linker-option", "SceneKit",
                    "-linker-option", "-framework", "-linker-option", "Foundation",
                    "-linker-option", "-framework", "-linker-option", "CoreGraphics",
                    "-linker-option", "-framework", "-linker-option", "QuartzCore",
                    "-linker-option", "-framework", "-linker-option", "AVFAudio",
                    "-linker-option", "-framework", "-linker-option", "AudioToolbox"
                )
            }
        }
    }

    sourceSets {
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting
        val jvmTest by getting
        val iosMain by creating {
            dependsOn(commonMain)
        }
        val iosArm64Main by getting {
            dependsOn(iosMain)
        }
    }
}
