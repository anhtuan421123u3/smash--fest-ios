plugins {
    kotlin("multiplatform") version "2.0.21"
}

group = "com.smashfest"
version = "1.0.0"

kotlin {
    jvm()

    iosArm64 {
        binaries {
            framework {
                baseName = "SmashFestKit"
                isStatic = true
            }
        }
    }

    iosSimulatorArm64 {
        binaries {
            framework {
                baseName = "SmashFestKit"
                isStatic = true
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
        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain)
        }
    }
}
