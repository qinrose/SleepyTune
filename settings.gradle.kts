pluginManagement {
    repositories {
        // 离线本地仓库（aiankicard 物化生成，补齐缺失/过期元数据；文件仓库离线可用）
        maven { url = uri("${System.getProperty("user.home").replace('\\', '/')}/.gradle/local-m2") }
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
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 离线本地仓库：由 ~/.gradle/caches/modules-2 物化生成（aiankicard 同款），补齐缺失模块元数据，保证无网络可解析
        maven { url = uri("${System.getProperty("user.home").replace('\\', '/')}/.gradle/local-m2") }
        google()
        mavenCentral()
    }
}

rootProject.name = "Sleepy Tune"
include(":app")
include(":wear")
