// 时粒（Timart）工程配置：仓库策略 = 腾讯镜像优先 + 官方源兜底
pluginManagement {
    repositories {
        // 腾讯 maven-public（代理 mavenCentral，国内首选）
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        // google 仓库国内镜像（腾讯无独立 google 仓库，采用阿里云 google 镜像）
        maven("https://maven.aliyun.com/repository/google")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        maven("https://maven.aliyun.com/repository/google")
        google()
        mavenCentral()
    }
}

rootProject.name = "timart"
include(":app")
