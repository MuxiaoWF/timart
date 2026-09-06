package com.muxiao.timart.domain.model

/**
 * 动效三档位（PRD §3.4.1）。
 * 首启按机型内存自动选档，用户可在设置中覆盖。
 */
enum class AnimationTier {
    HIGH,
    MEDIUM,
    LOW;

    companion object {
        /** ≥6GB 内存 → HIGH */
        private const val HIGH_MIN_BYTES: Long = 6L * 1024 * 1024 * 1024

        /** ≥3GB 内存 → MEDIUM */
        private const val MEDIUM_MIN_BYTES: Long = 3L * 1024 * 1024 * 1024

        /** 纯函数自动选档：低内存设备强制 LOW，其余按内存分档 */
        fun autoDetect(totalMemBytes: Long, isLowRam: Boolean): AnimationTier = when {
            isLowRam -> LOW
            totalMemBytes >= HIGH_MIN_BYTES -> HIGH
            totalMemBytes >= MEDIUM_MIN_BYTES -> MEDIUM
            else -> LOW
        }

        // ---- 粒子预算表（架构 §8.2，精确对齐 PRD §3.4.1：HIGH / MEDIUM / LOW）----

        /** 首页时尘背景：漂移粒子数 */
        fun backgroundParticles(tier: AnimationTier): Int = when (tier) {
            HIGH -> 80
            MEDIUM -> 40
            LOW -> 0
        }

        /** 胶囊 BREATHE：每球粒子上限 */
        fun breathePerOrb(tier: AnimationTier): Int = when (tier) {
            HIGH -> 20
            MEDIUM -> 14
            LOW -> 8
        }

        /** PENDING：单次向心聚合粒子数上限 */
        fun pendingMax(tier: AnimationTier): Int = when (tier) {
            HIGH -> 20
            MEDIUM -> 14
            LOW -> 8
        }

        /** UNSEAL：揭封序列峰值粒子数 */
        fun unsealPeak(tier: AnimationTier): Int = when (tier) {
            HIGH -> 800
            MEDIUM -> 350
            LOW -> 80
        }

        /** DISSOLVE：消散粒子数 */
        fun dissolveParticles(tier: AnimationTier): Int = when (tier) {
            HIGH -> 300
            MEDIUM -> 150
            LOW -> 40
        }

        /** ASSEMBLE：封存聚合粒子数 */
        fun assembleParticles(tier: AnimationTier): Int = when (tier) {
            HIGH -> 350
            MEDIUM -> 160
            LOW -> 50
        }

        /** 重读过渡粒子数（450ms 简化转场） */
        fun rereadParticles(tier: AnimationTier): Int = when (tier) {
            HIGH -> 40
            MEDIUM -> 20
            LOW -> 0
        }

        /** 输入飘粒：单次粒子数（低档为 0，直接关闭） */
        fun inputSparkParticles(tier: AnimationTier): Int = when (tier) {
            HIGH -> 6
            MEDIUM -> 4
            LOW -> 0
        }

        /** 对象池按档位上限预分配容量 */
        fun poolCapacity(tier: AnimationTier): Int = when (tier) {
            HIGH -> 900
            MEDIUM -> 420
            LOW -> 110
        }
    }
}
