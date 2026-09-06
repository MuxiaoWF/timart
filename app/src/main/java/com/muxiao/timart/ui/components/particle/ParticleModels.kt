package com.muxiao.timart.ui.components.particle

/**
 * 粒子可变数据帧 + 对象池（架构 §2.18）。
 *
 * 硬性约束：帧循环零分配——粒子字段全部 @JvmField 基础类型；
 * 池预分配 [capacity] 个实例 + IntArray 空闲栈，obtain/release 均 O(1) 无对象创建。
 */
class Particle {

    /** 池内下标（release 回收用，避免扫描） */
    @JvmField var index: Int = 0

    // ---- 位置与速度（px, px/s） ----
    @JvmField var x: Float = 0f
    @JvmField var y: Float = 0f
    @JvmField var vx: Float = 0f
    @JvmField var vy: Float = 0f

    // ---- 生命周期 ----

    /** 已存活毫秒 */
    @JvmField var age: Long = 0L

    /** 总寿命毫秒 */
    @JvmField var life: Long = 1000L

    /** 是否在用 */
    @JvmField var active: Boolean = false

    /** 生命尽后是否原地重生（背景 / 呼吸类） */
    @JvmField var loop: Boolean = false

    // ---- 外观 ----
    @JvmField var size: Float = 2f
    @JvmField var alpha: Float = 1f

    /** ARGB 颜色 */
    @JvmField var color: Int = 0xFFFFFFFF.toInt()

    /** 初速模长（speed 在寿命内线性衰减到 0.3 倍，营造"尘埃落定"感） */
    @JvmField var speed0: Float = 0f

    /** 发射起点（CONVERGE 型向心插值用） */
    @JvmField var startX: Float = 0f
    @JvmField var startY: Float = 0f

    /** BREATHE 绑定的锚点下标；-1 = 未绑定 */
    @JvmField var anchorIndex: Int = -1

    /** 锚点半径（ORBIT 型流动范围） */
    @JvmField var anchorRadius: Float = 0f

    /** 运动方向语义（ParticleFlow.ordinal，避免持有枚举对象引用成本可忽略但统一基础类型） */
    @JvmField var flow: Byte = 0

    /** CONVERGE 型的汇聚目标点 */
    @JvmField var targetX: Float = 0f
    @JvmField var targetY: Float = 0f

    /** 是否走预渲染光晕绘制（亮粒子 true / 尘粒 false 走实心圆） */
    @JvmField var glow: Boolean = false

    /** 随机相位种子（呼吸 alpha / 轨道初相） */
    @JvmField var seed: Float = 0f

    /** 归属页（ParticleEngine.OWNER_*）：循环型粒子（背景漂移/内流）只在宿主画布渲染；一次性粒子 = OWNER_NONE 处处可绘 */
    @JvmField var owner: Int = 0
}

/**
 * 粒子对象池：预分配 + 空闲栈，帧循环零分配。
 * 遍历采用全数组扫描（容量上限 900，扫描成本可忽略，换取无迭代器分配）。
 *
 * @param onRelease 粒子真正归还时的回调（release/releaseAll 均触发且仅在 active→inactive
 *   转换时触发一次）——引擎据此增量维护 flow/锚点计数，避免每帧全池扫描（§A6）
 */
class ParticlePool(
    private val capacity: Int,
    private val onRelease: ((Particle) -> Unit)? = null,
) {

    private val all: Array<Particle> = Array(capacity) { Particle() }

    private val freeStack = IntArray(capacity)

    private var freeTop: Int = -1

    /** 当前在用粒子数 */
    var activeCount: Int = 0
        private set

    init {
        for (i in capacity - 1 downTo 0) {
            all[i] = Particle()
            all[i].index = i
            freeTop++
            freeStack[freeTop] = i
        }
    }

    /** 取一个空闲粒子；池满返回 null（发射端按预算自然截断） */
    fun obtain(): Particle? {
        if (freeTop < 0) return null
        val p = all[freeStack[freeTop]]
        freeTop--
        p.active = true
        p.age = 0L
        p.alpha = 1f
        activeCount++
        return p
    }

    /** 归还粒子（幂等：非在用粒子忽略） */
    fun release(p: Particle) {
        if (!p.active) return
        p.active = false
        freeTop++
        freeStack[freeTop] = p.index
        activeCount--
        onRelease?.invoke(p)
    }

    /** 归还全部（skipToEnd / detach 用） */
    fun releaseAll() {
        for (p in all) {
            if (p.active) {
                p.active = false
                freeTop++
                freeStack[freeTop] = p.index
                onRelease?.invoke(p)
            }
        }
        activeCount = 0
    }

    /** 按下标取粒子（遍历用） */
    fun at(index: Int): Particle = all[index]

    /** 池容量 */
    fun size(): Int = capacity
}
