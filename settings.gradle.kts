pluginManagement {
    repositories {
        // 1. 华为云公共仓库 (包含 Maven Central 和 JCenter)
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
        // 2. 华为云 Google 镜像
        maven { url = uri("https://repo.huaweicloud.com/repository/google/") }
        // 3. 华为云 Gradle 插件镜像
        maven { url = uri("https://repo.huaweicloud.com/repository/gradle-plugin/") }

        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // 强制使用这里的仓库配置，忽略项目级 build.gradle 的配置
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 1. 华为云公共仓库
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
        // 2. 华为云 Google 镜像
        maven { url = uri("https://repo.huaweicloud.com/repository/google/") }
        // 3. 华为云 JitPack (图表库需要)
        maven { url = uri("https://repo.huaweicloud.com/repository/jitpack/") }
        // 4. JitPack 官方仓库 (Lunar 库需要)
        maven { url = uri("https://jitpack.io") }

        google()
        mavenCentral()
    }
}

rootProject.name = "budgetapp"
include(":app")