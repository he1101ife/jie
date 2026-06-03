pluginManagement {
    repositories {
        // 替换 google()，去掉内容过滤，直接使用阿里云镜像
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        // 如果上面 gradle-plugin 镜像不全，也可保留 gradlePluginPortal() 并额外添加镜像
        gradlePluginPortal()  // 这个通常也需要能访问，先保留；若仍有问题，可注释掉
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/public") } // 额外公共库，加全更保险
    }
}

rootProject.name = "MyFirstKotlinApp"
include(":app")