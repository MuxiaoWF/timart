// 根构建脚本：仅声明插件版本别名，各模块按需 apply
// 注意：AGP 9.0 起 Kotlin 支持内置于 AGP，禁止再应用 org.jetbrains.kotlin.android 插件
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
