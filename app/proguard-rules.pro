# 时粒 Release 混淆规则
# 覆盖：kotlinx-serialization（@Serializable 类与 Companion serializer）、BouncyCastle、Room 生成实现类

# R8 收缩优化：允许调整类/成员可见性，进一步减小 dex
-allowaccessmodification

# ---- kotlinx-serialization ----
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.muxiao.timart.**$$serializer { *; }
-keepclassmembers class com.muxiao.timart.** {
    *** Companion;
}
-keepclasseswithmembers class com.muxiao.timart.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- BouncyCastle（Argon2id）----
# 应用只静态调用 Argon2BytesGenerator / Argon2Parameters（无 JCA Provider 注册、无反射），
# R8 从这两个入口自动追溯闭包（Blake2b 摘要等）；其余 BC 模块（TLS/PQC/ASN.1/OpenPGP…）
# 连同 jar 内资源（picnic lowmc .bin 参数 ≈1.2MB、x509 文案 properties）一并裁剪。
# 勿改回 -keep class org.bouncycastle.** { *; }（全量保留会让 APK 增大数 MB）。
-keep class org.bouncycastle.crypto.generators.Argon2BytesGenerator { *; }
-keep class org.bouncycastle.crypto.params.Argon2Parameters { *; }
-dontwarn org.bouncycastle.**

# ---- Room 生成实现类 ----
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class com.muxiao.timart.data.local.db.** { *; }

# ---- 百度地图 SDK（仅 GPS 条件在线选点）----
# JNI/反射驱动，需按官方建议保留核心包；未引入定位/导航 SDK，不动 location/nav 包
-keep class com.baidu.mapapi.** { *; }
-keep class com.baidu.platform.** { *; }
-keep class com.baidu.vi.** { *; }
-dontwarn com.baidu.**
