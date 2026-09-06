package com.muxiao.timart.ui.components.particle

/**
 * 粒子预设（架构 §8.1）：引擎发射粒子的行为模板。
 * 预设只描述"怎么动"，不携带业务语义；业务状态经 [CapsuleMotionState] 映射到此枚举。
 */
enum class ParticlePreset {
    /** 首页时尘背景：全屏缓慢漂移，循环重生 */
    DUST_BACKGROUND,

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

/**
 * 一次发射的行为参数（由引擎按预设 + 档位预算生成，UI 不直接构造）。
 * 单位：速度 px/s、尺寸 px、时长 ms；颜色为 ARGB int（避免持有 Compose/android Color 对象）。
 *
 * @param flow        运动方向语义
 * @param count       本次发射粒子数（引擎按池余量截断）
 * @param duration    单次动画时长（循环型为重生周期）
 * @param speedMin    初速下限
 * @param speedMax    初速上限
 * @param sizeMin     尺寸下限
 * @param sizeMax     尺寸上限
 * @param colors      颜色池（ARGB）
 * @param loop        生命尽后是否原地重生（背景 / 呼吸类为 true）
 */
class PresetParams(
    val flow: ParticleFlow,
    val count: Int,
    val duration: Long,
    val speedMin: Float,
    val speedMax: Float,
    val sizeMin: Float,
    val sizeMax: Float,
    val colors: IntArray,
    val loop: Boolean,
) {
    override fun equals(other: Any?): Boolean = other is PresetParams &&
        flow == other.flow && count == other.count && duration == other.duration &&
        speedMin == other.speedMin && speedMax == other.speedMax &&
        sizeMin == other.sizeMin && sizeMax == other.sizeMax &&
        colors.contentEquals(other.colors) && loop == other.loop

    override fun hashCode(): Int = flow.hashCode() * 31 + count
}
