import java.util.Properties

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

val buildConfigFile = file("build.config.properties")
val buildConfig = Properties()
if (buildConfigFile.exists()) {
    buildConfig.load(buildConfigFile.inputStream())
}

dependencyResolutionManagement {
    val localProperties = java.util.Properties().apply {
        val localPropertiesFile = rootDir.resolve("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use(::load)
        }
    }
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://artifact.bytedance.com/repository/pangle/")
        maven("https://repo.itextsupport.com/android")
        maven("https://repo.dgtverse.cn/repository/maven-public/")
        maven("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea")
        maven("https://android-sdk.is.com/")
        maven("https://jfrog.anythinktech.com/artifactory/overseas_sdk")
        maven("https://artifacts.applovin.com/android")
        maven("https://repo.dgtverse.cn/repository/maven-public")
        maven {
            url = uri("https://maven.pkg.github.com/toukaRemax/remax_sdk")
            credentials {
                username = buildConfig.getProperty("github.user")
                    ?: System.getenv("GH_PACKAGES_USER")
                    ?: System.getenv("GITHUB_ACTOR")
                password = buildConfig.getProperty("github.token")
                    ?: System.getenv("GH_PACKAGES_TOKEN")
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
        maven {
            url = uri("https://maven.app-plat.com/repository/maven-public")
            // 只开放 Launcher SDK 及其定制版 Firebase Config，避免影响其他依赖的解析。
            content {
                includeGroup("com.launcher.unity")
                includeModule("com.google.firebase", "firebase-config")
            }
            val repositoryUser = localProperties.getProperty("appPlatMavenUser")
                ?: System.getenv("APP_PLAT_MAVEN_USER")
            val repositoryPassword = localProperties.getProperty("appPlatMavenPassword")
                ?: System.getenv("APP_PLAT_MAVEN_PASSWORD")
            if (!repositoryUser.isNullOrBlank() && !repositoryPassword.isNullOrBlank()) {
                credentials {
                    username = repositoryUser
                    password = repositoryPassword
                }
            }
        }
    }
}

rootProject.name = "GPSPhoto"
include(":app")
//include(":bill")
//include(":core")
include(":metrics")
