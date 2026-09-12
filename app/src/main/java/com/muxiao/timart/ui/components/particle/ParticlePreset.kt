package com.muxiao.timart.ui.components.particle

/**
 * 粒子预设（架构 §8.1）：引擎发射粒子的行为模板。
 * 预设只描述"怎么动"，不携带业务语义；业务状态经 [MotionState] 映射到此枚举。
 */
enum class ParticlePreset {
    /** 胶囊呼吸：锚点外环带慢公转 + 径向呼吸，循环重生 */
    BREATHE,

    /** 封存聚合：三段时间轴 0–350 / 350–800 / 800–1200ms */
    ASSEMBLE,

    /** 条件满足：300–600ms 向心聚合单发 */
    PENDING,

    /** 重读过渡：尘粒向信笺中心聚合单发（450ms，与 RereadVeilOverlay 叠加） */
    REREAD,

    /** 揭封序列：3.2s 五阶段，可 [ParticleEngine.skipToEnd] 直落终态 */
    UNSEAL,

    /** 消散：0–1000ms 沿轨道向外散逸 */
    DISSOLVE,

    /** 输入飘粒：光标附近 3–6 粒上飘，250ms 节流 */
    INPUT_SPARK,
}

/** 粒子运动方向语义 */
enum class ParticleFlow {
    /** 任意方向缓慢漂移（背景） */
    DRIFT,

    /** 从外周向锚点汇聚（PENDING / ASSEMBLE / UNSEAL 第一段） */
    CONVERGE,

    /** 从锚点向外扩散（DISSOLVE / INPUT_SPARK） */
    DIVERGE,

    /** 锚点半径内部循环流动（BREATHE） */
    ORBIT,
}

