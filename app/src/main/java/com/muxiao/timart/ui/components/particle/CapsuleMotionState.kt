package com.muxiao.timart.ui.components.particle

/**
 * 胶囊动效状态机（架构 §8.1）：
 * IDLE ─→ BREATHE ─→ PENDING ─→ UNSEAL ─→ CONTENT ─→ DISSOLVE ─→ ARCHIVED
 *
 * 引擎不持有业务逻辑：业务侧经 [setState] 驱动，状态 → 预设映射集中在此文件。
 */
enum class MotionState {
    /** 无粒子 */
    IDLE,

    /** 持续呼吸循环（LOCKED 冷灰青内流 / UNLOCKED 暖金慢呼吸），每球 ≤ 档位预算 */
    BREATHE,

    /** 条件满足单发：300–600ms 向心聚合 */
    PENDING,

    /** 揭封序列：3.2s 五阶段，可 skipToEnd 安全直落 CONTENT */
    UNSEAL,

    /** 阅读态：仅静态光晕，无循环粒子 */
    CONTENT,

    /** 消散：0–1000ms 沿轨道散逸 */
    DISSOLVE,

    /** 尘迹静止（由静态绘制承担，引擎无粒子） */
    ARCHIVED,
}

/** 状态 → 默认预设映射表（§8.1；UNSEAL/ASSEMBLE 序列经 fire(sequence=true) 单独驱动） */
fun MotionState.defaultPreset(): ParticlePreset? = when (this) {
    MotionState.IDLE -> null
    MotionState.BREATHE -> ParticlePreset.BREATHE
    MotionState.PENDING -> ParticlePreset.PENDING
    MotionState.UNSEAL -> ParticlePreset.UNSEAL
    MotionState.CONTENT -> null
    MotionState.DISSOLVE -> ParticlePreset.DISSOLVE
    MotionState.ARCHIVED -> null
}
