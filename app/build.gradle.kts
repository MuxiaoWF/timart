import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // AGP 9.0 起 Kotlin 支持内置于 AGP（built-in Kotlin），无需 kotlin.android 插件；
    // Kotlin jvmTarget 自动对齐下方 compileOptions 的 17
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// 天地图地理编码 tk 只存于 local.properties（.gitignore 已忽略，绝不入库）；
// 缺失时注入空串，地址解析链自动跳过该级兜底
val tiandituTk: String by lazy {
    val props = Properties()
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
    props.getProperty("TIANDITU_TK").orEmpty()
}

android {
    namespace = "com.muxiao.timart"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.muxiao.timart"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1"
        buildConfigField("String", "TIANDITU_TK", "\"$tiandituTk\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 本地暂无发布密钥库，复用 debug 密钥使 release 产物可直接安装验证；
            // 正式对外发布前替换为专用 keystore
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time（LocalDate/DayOfWeek）在 API 24 上的 desugaring 支持
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // 应用内语言 = 简/繁/英（l10n 词表自管）；资源限定符只保留三语，
        // 裁掉 AndroidX/Material 依赖携带的其余 ~80 种语言翻译（resources.arsc 收缩）
        localeFilters += listOf("zh", "zh-rTW", "zh-rHK", "en")
    }

    dependenciesInfo {
        // Play 依赖签元数据块，应用内不消费
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            // BouncyCastle 的 META-INF 冲突 + 依赖库 LICENSE 文本不进包
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/androidx/**",
            )
            // bcprov jar 携带的 java 资源（picnic lowmc PQC 参数 ≈1.2MB、x509 校验文案）：
            // Argon2id/Blake2b 闭包不读任何 classpath 资源，全部排除
            excludes += "org/bouncycastle/**"
        }
    }

    testOptions {
        unitTests {
            // 单测中触达 android.util.Log 等桩方法时返回默认值而非抛异常
            isReturnDefaultValues = true
        }
    }
}

// AGP 9 起 v1/v2/v3 签名开关只在 Variant API 上生效（DSL/BuildType 上的同名属性已废弃为空操作）。
// 注意：minSdk ≥ 24 时 AGP 对 v1（JAR）签名强制跳过、此开关无效——v1 仅 Android 6.0- 需要，
// 低于本项目 minSdk 24，故产物为 v2+v3（apksigner 实测）即已覆盖全部支持设备
androidComponents {
    onVariants { variant ->
        variant.signingConfig.apply {
            enableV1Signing.set(true)
            enableV2Signing.set(true)
            enableV3Signing.set(true)
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.exifinterface)
    implementation(libs.bcprov.jdk18on)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
