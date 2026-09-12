package com.muxiao.timart.utils.device

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.nfc.NfcAdapter
import com.muxiao.timart.domain.model.Lang
import com.muxiao.timart.domain.model.unlock.UnlockCondition
import com.muxiao.timart.l10n.stringsFor
import com.muxiao.timart.utils.RuntimeSettings

/**
 * 设备硬件能力与条件依赖扫描（单一事实源，两处消费）：
 * - 创建侧条件选择面板：缺失硬件的条件禁用（RulesStep）；
 * - 备份导出/导入完成提示：扫描规则中本机硬件永远无法达成的条件（BackupManager）。
 */
enum class DeviceHardware {
    PRESSURE,
    ROTATION_VECTOR,
    STEP_COUNTER,
    STEP_DETECTOR,
    ACCELEROMETER,
    NFC,
    LIGHT_SENSOR,
    CAMERA,
    BIOMETRIC,
}

/** 本机缺失的硬件能力（每次调用即时读取，调用方按需 remember） */
fun missingDeviceHardware(context: Context): Set<DeviceHardware> {
    val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    return buildSet {
        if (sm?.getDefaultSensor(Sensor.TYPE_PRESSURE) == null) add(DeviceHardware.PRESSURE)
        if (sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) == null) add(DeviceHardware.ROTATION_VECTOR)
        if (sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) == null) add(DeviceHardware.STEP_COUNTER)
        if (sm?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) == null) add(DeviceHardware.STEP_DETECTOR)
        if (sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) == null) add(DeviceHardware.ACCELEROMETER)
        if (sm?.getDefaultSensor(Sensor.TYPE_LIGHT) == null) add(DeviceHardware.LIGHT_SENSOR)
        if (!context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)) {
            add(DeviceHardware.CAMERA)
        }
        // 探测硬件存在性（不要求已录入指纹）：无生物识别硬件的条件在创建侧禁用
        val noBiometricHardware = runCatching {
            androidx.biometric.BiometricManager.from(context).canAuthenticate(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK,
            ) == androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
        }.getOrDefault(true)
        if (noBiometricHardware) add(DeviceHardware.BIOMETRIC)
        if (NfcAdapter.getDefaultAdapter(context) == null) add(DeviceHardware.NFC)
    }
}

/** 条件依赖的硬件（纯映射；不依赖硬件的条件返回空集） */
fun UnlockCondition.requiredHardware(): Set<DeviceHardware> = when (this) {
    is UnlockCondition.AltitudeRange -> setOf(DeviceHardware.PRESSURE)
    is UnlockCondition.CompassHeading -> setOf(DeviceHardware.ROTATION_VECTOR)
    is UnlockCondition.StepCount, is UnlockCondition.StepStreak -> setOf(DeviceHardware.STEP_COUNTER)
    is UnlockCondition.MotionActivity -> setOf(DeviceHardware.STEP_DETECTOR)
    is UnlockCondition.ShakeCount, is UnlockCondition.FlipOrHold -> setOf(DeviceHardware.ACCELEROMETER)
    is UnlockCondition.NfcTap -> setOf(DeviceHardware.NFC)
    is UnlockCondition.AmbientLight -> setOf(DeviceHardware.LIGHT_SENSOR)
    is UnlockCondition.PhotoKeepsake -> setOf(DeviceHardware.CAMERA)
    is UnlockCondition.BiometricUnlock -> setOf(DeviceHardware.BIOMETRIC)
    else -> emptySet()
}

/**
 * 扫描条件集合中本机硬件无法达成的条件类型，返回去重后的条件名（随界面语言，
 * 与详情页判定文案同语言 [RuntimeSettings.resolvedLang]）。
 * 用于备份导出/导入完成提示：这些条件在本设备上永远无法满足，胶囊可能因此无法解锁。
 */
fun unsupportedConditionNames(
    context: Context,
    conditions: Iterable<UnlockCondition>,
    lang: Lang = RuntimeSettings.resolvedLang,
): List<String> {
    val missing = missingDeviceHardware(context)
    if (missing.isEmpty()) return emptyList()
    val L = stringsFor(lang)
    val found = LinkedHashSet<String>()
    for (condition in conditions) {
        if (condition.requiredHardware().none { it in missing }) continue
        found += when (condition) {
            is UnlockCondition.AltitudeRange -> L.condAltitude
            is UnlockCondition.CompassHeading -> L.condCompass
            is UnlockCondition.StepCount -> L.condStep
            is UnlockCondition.StepStreak -> L.condStreak
            is UnlockCondition.MotionActivity -> L.condMotion
            is UnlockCondition.ShakeCount -> L.condShake
            is UnlockCondition.FlipOrHold -> L.condFlipHold
            is UnlockCondition.NfcTap -> L.condNfc
            is UnlockCondition.AmbientLight -> L.condAmbientLight
            is UnlockCondition.PhotoKeepsake -> L.condPhotoKeepsake
            is UnlockCondition.BiometricUnlock -> L.condBiometric
            else -> continue
        }
    }
    return found.toList()
}
