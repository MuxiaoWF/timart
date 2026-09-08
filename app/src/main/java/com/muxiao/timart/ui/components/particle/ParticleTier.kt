package com.muxiao.timart.ui.components.particle

import com.muxiao.timart.domain.model.AnimationTier

/**
 * 三档位粒子预算常量表（架构 §8.2，精确对齐 PRD §3.4.1：HIGH / MEDIUM / LOW）。
 * 全部数值编译期取自 domain 层 [AnimationTier] 预算函数，UI 层只读此表。
 *
 * 出口标准断言锚点：`ParticleBudget.of(AnimationTier.MEDIUM).background == 40`。
 */
class ParticleBudget private constructor(tier: AnimationTier) {

    /** 首页时尘背景漂移粒子数：80 / 40 / 0 */
    val background: Int = AnimationTier.backgroundParticles(tier)

    /** 胶囊 BREATHE 每球粒子上限：20 / 14 / 8 */
    val breathePerOrb: Int = AnimationTier.breathePerOrb(tier)

    /** PENDING 单次向心聚合粒子数上限：20 / 14 / 8 */
    val pending: Int = AnimationTier.pendingMax(tier)

    /** ASSEMBLE 封存聚合粒子数：350 / 160 / 50 */
    val assemble: Int = AnimationTier.assembleParticles(tier)

    /** UNSEAL 揭封序列峰值粒子数：800 / 350 / 80 */
    val unsealPeak: Int = AnimationTier.unsealPeak(tier)

    /** 重读过渡粒子数：40 / 20 / 0 */
    val reread: Int = AnimationTier.rereadParticles(tier)

    /** DISSOLVE 消散粒子数：300 / 150 / 40 */
    val dissolve: Int = AnimationTier.dissolveParticles(tier)

    /** 输入飘粒单次粒子数：6 / 4 / 0（LOW 直接关闭） */
    val inputSpark: Int = AnimationTier.inputSparkParticles(tier)

    /** 对象池按档位上限预分配容量：900 / 420 / 110 */
    val poolCapacity: Int = AnimationTier.poolCapacity(tier)

    companion object {

        /** 档位预算实例缓存（ParticleBudget 无状态只读，全局共享安全） */
        private val cache = HashMap<AnimationTier, ParticleBudget>()

        /** 取某档位的预算表（缓存复用，不重复构造） */
        fun of(tier: AnimationTier): ParticleBudget =
            cache.getOrPut(tier) { ParticleBudget(tier) }
    }
}
