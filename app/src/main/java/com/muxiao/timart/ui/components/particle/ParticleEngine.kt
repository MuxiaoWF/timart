package com.muxiao.timart.ui.components.particle

import android.graphics.Canvas
import com.muxiao.timart.domain.model.AnimationTier
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 粒子引擎单例（AppContainer 持有，架构 §2.18 / §8）。
 *
 * - tick/draw 严格分离：tick 由 Compose 帧循环（ParticleCanvas）驱动，draw 只读状态；
 * - 帧循环零分配：全部可变状态预分配，随机数用 java.util.Random 基础类型；
 * - 业务状态更新永不等待动画回调：序列完成仅经 [onSequenceFinished] 通知；
 * - 同一时刻仅一个主焦点动画：UNSEAL / ASSEMBLE 序列互斥（后到覆盖先到）。
 */
class ParticleEngine(tier: AnimationTier) {

    private val budget = ParticleBudget.of(tier)
    private val pool = ParticlePool(budget.poolCapacity) { p -> onParticleReleased(p) }
    private val random = java.util.Random()

    // ---- 画布 ----
    private var width = 0f
    private var height = 0f
    private var density = 1f

    // ---- 状态 ----
    private var currentState: MotionState = MotionState.IDLE

    var paused: Boolean = false
        private set

    private var backgroundEnabled = false

    /** 背景归属权：多页面共享引擎，只有设置方自己（或引擎重建）能关掉自己的背景 */
    private var backgroundOwner: Any? = null

    /** 循环尘宿主（OWNER_*）：BREATHE/背景漂移粒子打上此标记，绘制时只进宿主画布（防跨页残影） */
    private var loopOwner: Int = OWNER_NONE

    // ---- BREATHE 锚点（预分配，首页多球零分配同步） ----
    private val anchorX = FloatArray(MAX_ORBS)
    private val anchorY = FloatArray(MAX_ORBS)
    private val anchorR = FloatArray(MAX_ORBS)
    private val anchorColor = IntArray(MAX_ORBS)
    private var anchorCount = 0

    // ---- 序列（UNSEAL 3.2s 五阶段 / ASSEMBLE 1.2s 三段） ----
    private var sequencePreset: ParticlePreset? = null
    private var sequenceElapsed = 0L

    /** 序列完成回调：业务侧绝不等待，仅作通知（先取后置空，防重复触发） */
    var onSequenceFinished: (() -> Unit)? = null

    // ---- INPUT_SPARK 250ms 节流 ----
    private var lastSparkElapsed = 0L

    // ================= 生命周期 =================

    /** 画布就绪（ParticleCanvas onSizeChanged 调用；首帧后才初始化） */
    fun attach(w: Float, h: Float, density: Float) {
        width = w
        height = h
        this.density = density
    }

    /** 画布销毁：清场并复位序列（不动背景开关与画布尺寸——共享引擎下尺寸属于还活着的画布） */
    fun detach() {
        pool.releaseAll()
        sequencePreset = null
        sequenceElapsed = 0L
        anchorCount = 0
        currentState = MotionState.IDLE
        lastFrameNanos = 0L
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    /** dp → px（引擎内尺寸/速度统一以 dp 定义、运行时换算） */
    private fun dp(v: Float): Float = v * density

    /**
     * 有界随机 Long（API 24 安全）：
     * `Random.nextLong(bound)` 是 JDK 17 新增方法，compileSdk 36 可编译、
     * 但 Android ≤13 的 ART（OpenJDK 11）运行时不存在（NoSuchMethodError），禁用。
     * 取无符号化 nextLong 对 bound 取模即可。
     */
    private fun nextLongBound(bound: Long): Long =
        (random.nextLong() and java.lang.Long.MAX_VALUE) % bound.coerceAtLeast(1L)

    // ================= 业务驱动 API =================

    /**
     * 首页背景尘开关（DUST_BACKGROUND）：进页开、离页/ON_PAUSE 关。
     * LOW 档 background=0，开启亦无粒子（Reduced Motion 友好）。
     * 归属权制：开启方记录 owner；关闭仅当 owner 匹配才生效——
     * 否则多页面共享引擎时，其他页面画布销毁（detach/重组）会误关首页背景尘。
     */
    fun setBackground(enabled: Boolean, owner: Any) {
        if (enabled) {
            backgroundEnabled = true
            backgroundOwner = owner
            // 漂移尘归属随 loopOwner（宿主页开启背景时必已通过 setState(BREATHE, owner) 认领）
        } else if (backgroundOwner === owner) {
            backgroundEnabled = false
            backgroundOwner = null
        }
    }

    /** 状态切换：IDLE/CONTENT/ARCHIVED 清空循环粒子（静态光晕由 GlowOrb 承担）。
     *  BREATHE 认领归属 [owner]：循环尘只在该宿主画布渲染，上一宿主残留立即释放（防跨页残影） */
    fun setState(state: MotionState, owner: Int = OWNER_NONE) {
        currentState = state
        if (state == MotionState.BREATHE) {
            loopOwner = owner
            releaseLoopsExcept(owner)
        } else {
            loopOwner = OWNER_NONE
            when (state) {
                MotionState.IDLE, MotionState.CONTENT, MotionState.ARCHIVED -> clearLoopParticles()
                else -> Unit
            }
        }
    }

    /** 同步 BREATHE 锚点数量（首页球数变化时） */
    fun setOrbCount(count: Int) {
        anchorCount = count.coerceIn(0, MAX_ORBS)
    }

    /** 更新某锚点（屏幕坐标 + 状态色） */
    fun setOrbAnchor(index: Int, x: Float, y: Float, radius: Float, colorArgb: Int) {
        if (index < 0 || index >= MAX_ORBS) return
        anchorX[index] = x
        anchorY[index] = y
        anchorR[index] = radius
        anchorColor[index] = colorArgb
    }

    /**
     * 发射一次预设。
     * @param sequence true 表示启动时间轴序列（UNSEAL/ASSEMBLE），期间互斥
     */
    fun fire(preset: ParticlePreset, anchorX: Float, anchorY: Float, anchorRadius: Float, colorArgb: Int, sequence: Boolean = false) {
        if (paused) return
        when {
            sequence && preset == ParticlePreset.UNSEAL -> startSequence(preset, anchorX, anchorY, anchorRadius)
            sequence && preset == ParticlePreset.ASSEMBLE -> startSequence(preset, anchorX, anchorY, anchorRadius)
            preset == ParticlePreset.PENDING -> spawnPending(anchorX, anchorY, anchorRadius, colorArgb)
            preset == ParticlePreset.REREAD -> spawnReread(anchorX, anchorY, anchorRadius, colorArgb)
            preset == ParticlePreset.DISSOLVE -> spawnDissolve(anchorX, anchorY, anchorRadius, colorArgb)
            preset == ParticlePreset.INPUT_SPARK -> spawnInputSpark(anchorX, anchorY)
            else -> Unit // DUST_BACKGROUND/BREATHE 由维持逻辑负责，不经 fire
        }
    }

    /** UNSEAL/ASSEMBLE 任意时刻安全落终态：清场 → 状态归位 → 立即回调 */
    fun skipToEnd() {
        if (sequencePreset == null) return
        val wasUnseal = sequencePreset == ParticlePreset.UNSEAL
        pool.releaseAll()
        sequencePreset = null
        sequenceElapsed = 0L
        currentState = if (wasUnseal) MotionState.CONTENT else MotionState.IDLE
        val cb = onSequenceFinished
        onSequenceFinished = null
        cb?.invoke()
    }

    // ================= tick =================

    /** 上一推进帧的时间戳（tickAt 同帧去重用；0 = 尚未推进过） */
    private var lastFrameNanos = 0L

    /**
     * 帧推进（带**同帧去重**）：多个宿主画布（ParticleCanvas）同时组合时
     * （NavHost 转场交叉期 / HorizontalPager 翻页重叠窗口），每个画布的帧循环
     * 都会在同一渲染帧调用本方法——共享引擎只允许推进一次，否则粒子速度 ×2、
     * 序列时间轴倍速、循环尘按错误画布尺寸重生。Choreographer 对同一帧分发的
     * frameNanos 全局一致，精确相等即判定为同帧重复调用。
     */
    fun tickAt(frameNanos: Long) {
        if (frameNanos == lastFrameNanos) return
        val dtMillis = if (lastFrameNanos == 0L) {
            16L // 引擎刚 attach / 重建后的首帧：按 60fps 标准间隔推进
        } else {
            (frameNanos - lastFrameNanos) / 1_000_000L
        }
        lastFrameNanos = frameNanos
        tick(dtMillis)
    }

    /** 帧推进（帧间隔毫秒，dt 上限 100ms 防跳帧爆量） */
    fun tick(dtMillis: Long) {
        if (paused) return
        val dt = dtMillis.coerceIn(0L, 100L)
        lastSparkElapsed += dt

        sequencePreset?.let { tickSequence(it, dt) }
        maintainLoopParticles()
        updateParticles(dt)
    }

    // ---- 循环粒子增量计数（A6：obtain/release 时维护，maintainLoopParticles 免全池扫描） ----
    private var driftActive = 0
    private val orbitActive = IntArray(MAX_ORBS)

    /** 池释放回调：按释放时的 flow/锚点回扣计数（仅循环型 flow 参与计数） */
    private fun onParticleReleased(p: Particle) {
        when (p.flow) {
            ParticleFlow.DRIFT.ordinal.toByte() -> if (driftActive > 0) driftActive--
            ParticleFlow.ORBIT.ordinal.toByte() -> {
                val a = p.anchorIndex.coerceIn(0, MAX_ORBS - 1)
                if (orbitActive[a] > 0) orbitActive[a]--
            }
        }
    }

    /** 维持循环粒子：背景（全屏）与 BREATHE（每锚点）数量补齐；画布未就绪 / 无需维持时早退 */
    private fun maintainLoopParticles() {
        if (width <= 0f || height <= 0f) return
        val needBackground = backgroundEnabled && budget.background > 0
        val needBreathe = currentState == MotionState.BREATHE && anchorCount > 0
        if (!needBackground && !needBreathe) return
        if (needBackground) {
            // spawn 成功即自增 driftActive；pool 满（返回 null）时停止补齐
            while (driftActive < budget.background) {
                spawnBackgroundParticle() ?: break
            }
        }
        if (needBreathe) {
            // 总环带预算收敛：球多时按锚点数摊薄（20 球 × 20 粒挤在窄环带里是噪声云），
            // 下限每球 4 粒保住"活着"的呼吸感；球少时维持档位满预算
            val totalOrbitBudget = pool.size() / 6 // 150 / 70 / 18（高 / 中 / 低）
            val perOrb = min(budget.breathePerOrb, max(4, totalOrbitBudget / anchorCount))
            for (a in 0 until anchorCount) {
                // spawn 成功即自增 orbitActive[a]；pool 满（返回 false）时停止补齐
                while (orbitActive[a] < perOrb) {
                    if (!spawnBreatheParticle(a)) break
                }
            }
        }
    }

    /** 粒子物理更新（无对象分配） */
    private fun updateParticles(dtMillis: Long) {
        val dtS = dtMillis / 1000f
        for (i in 0 until pool.size()) {
            val p = pool.at(i)
            if (!p.active) continue
            p.age += dtMillis

            if (p.age >= p.life) {
                if (p.loop) {
                    respawnLoop(p)
                    continue
                }
                pool.release(p)
                continue
            }

            val t = p.age.toFloat() / p.life.toFloat()
            when (p.flow) {
                ParticleFlow.DRIFT.ordinal.toByte() -> {
                    p.x += p.vx * dtS
                    p.y += p.vy * dtS
                    // 边界环绕（背景尘永不堆积）
                    if (p.x < -dp(8f)) p.x = width + dp(8f)
                    if (p.x > width + dp(8f)) p.x = -dp(8f)
                    if (p.y < -dp(8f)) p.y = height + dp(8f)
                    if (p.y > height + dp(8f)) p.y = -dp(8f)
                    p.alpha = 0.32f + 0.46f * (0.5f + 0.5f * sin((p.age / 900f) + p.seed))
                }

                ParticleFlow.ORBIT.ordinal.toByte() -> {
                    // 真环绕运动学 + 开普勒式差速：每粒绕锚点公转，角速度随当前半径反比剪切
                    //（内圈快 / 外圈慢，±15% 左右）——环带有剪切层次，不再像刚性转盘，
                    // 但同球同向的连贯性保留。无随机游走、无向心弹簧。
                    // 字段约定（spawnAtAnchorOrbit 赋值）：seed=当前方位角 θ，
                    // speed0=基准角速度 ω(rad/s，已按球径缩放)，vx=径向振荡相位，vy=径向振幅系数
                    val ai = p.anchorIndex.coerceIn(0, MAX_ORBS - 1)
                    val orbR = anchorR[ai].coerceAtLeast(1f)
                    // 半径在球缘外环带内缓慢呼吸（内缘留 10% 球径空隙，外缘 1.56×），
                    // 始终在球外可见且不贴边——大球（详情页 88dp）下环带自然摊宽
                    val rr = orbR * (1.10f + 0.46f * p.vy * (0.5f + 0.5f * sin(p.age / 1400f + p.vx)))
                    // 差速剪切：ω_eff = ω₀ × (1.22R / r)，r 越小转得越快
                    p.seed += p.speed0 * (orbR * 1.22f / rr) * dtS
                    p.x = anchorX[ai] + cos(p.seed) * rr
                    p.y = anchorY[ai] + sin(p.seed) * rr
                    // 明暗：慢呼吸（~5.7s 周期）× 重生淡入淡出包络（循环重生不再跳变）
                    val env = min(1f, p.age / 400f) * min(1f, (p.life - p.age) / 400f)
                    p.alpha = (0.42f + 0.30f * sin(p.age / 900f + p.vx)) * env
                }

                ParticleFlow.CONVERGE.ordinal.toByte() -> {
                    // 由起点向目标缓动插值（先快后慢）
                    val ease = 1f - (1f - t) * (1f - t)
                    p.x = p.startX + (p.targetX - p.startX) * ease
                    p.y = p.startY + (p.targetY - p.startY) * ease
                    p.alpha = if (t < 0.8f) 0.4f + 0.6f * t else (1f - t) * 5f
                }

                ParticleFlow.DIVERGE.ordinal.toByte() -> {
                    val decay = 1f - 0.9f * dtS
                    p.vx *= decay
                    p.vy *= decay
                    p.x += p.vx * dtS
                    p.y += p.vy * dtS
                    p.alpha = (1f - t) * (1f - t)
                }
            }
        }
    }

    /** 循环粒子重生：背景重掷位置，呼吸重掷轨道偏移 */
    private fun respawnLoop(p: Particle) {
        p.age = 0L
        when (p.flow) {
            ParticleFlow.DRIFT.ordinal.toByte() -> {
                p.x = random.nextFloat() * width
                p.y = random.nextFloat() * height
                resetDriftVelocity(p)
            }

            ParticleFlow.ORBIT.ordinal.toByte() -> {
                val a = p.anchorIndex.coerceIn(0, MAX_ORBS - 1)
                spawnAtAnchorOrbit(p, a)
            }

            else -> Unit
        }
    }

    // ================= 发射实现 =================

    private fun spawnBackgroundParticle(): Particle? {
        val p = pool.obtain() ?: return null
        p.flow = ParticleFlow.DRIFT.ordinal.toByte()
        driftActive++
        p.x = random.nextFloat() * width
        p.y = random.nextFloat() * height
        p.size = dp(1.2f + random.nextFloat() * 2f)
        p.life = 6000L + nextLongBound(6000L)
        p.loop = true
        p.color = DUST_COLORS[random.nextInt(DUST_COLORS.size)]
        p.glow = false
        p.owner = loopOwner
        p.seed = random.nextFloat() * 6.28f
        resetDriftVelocity(p)
        return p
    }

    private fun resetDriftVelocity(p: Particle) {
        val angle = random.nextFloat() * 6.28f
        val speed = dp(4f + random.nextFloat() * 10f)
        p.vx = cos(angle) * speed
        p.vy = sin(angle) * speed * 0.6f // 纵向略慢，横向漂移感
        p.speed0 = speed
    }

    private fun spawnBreatheParticle(anchor: Int): Boolean {
        val p = pool.obtain() ?: return false
        p.flow = ParticleFlow.ORBIT.ordinal.toByte()
        p.anchorIndex = anchor
        orbitActive[anchor]++
        val orbR = anchorR[anchor].coerceAtLeast(1f)
        // 同一锚点全部同向公转（按锚点奇偶定方向）→ 每球一个连贯漩涡，相邻球间方向交错；
        // 基础角速度 0.55–1.1 rad/s 宽随机，叠加 ORBIT 更新的差速剪切（内快外慢），
        // 整体"有秩序但不整齐"——避免上一版窄区间（0.4–0.9）读成刚性转盘。
        // 角速度按球径反比缩放：线速度 ≈ ω×r，大球（详情页 88dp）若沿用小球标定的 ω，
        // 尘环线速度会放大 4–5 倍变成赛跑；以 dp(20) 为基准缩放并夹在 0.35–1，
        // 首页小球（12–18dp）不受影响，大球公转周期放缓到 ~16–33s 的沉稳节奏
        val dir = if (anchor % 2 == 0) 1f else -1f
        p.speed0 = dir * (0.55f + random.nextFloat() * 0.55f) * (dp(20f) / orbR).coerceIn(0.35f, 1f)
        spawnAtAnchorOrbit(p, anchor)
        // 粒径随锚点半径缩放：固定 1.2–2.6dp 的尘点在 88dp 大球旁不可见。
        // 基准 = 球径 × 0.048，夹在 1.3–5dp——首页小球落回 1.0–1.8dp 原观感，
        // 详情页大球尘点放大到 ~3.4–5dp，与球体比例相称
        val base = (orbR * 0.048f).coerceIn(dp(1.3f), dp(5f))
        p.size = base * (0.8f + random.nextFloat() * 0.6f)
        p.life = 3500L + nextLongBound(1500L)
        p.loop = true
        p.color = anchorColor[anchor]
        p.owner = loopOwner
        // 大球尘粒走光晕路径：球径 ≥ 36dp 时锚点必然很少（单球 ≤ 满预算 20 粒），
        // 光晕位图成本可承受，且深底上裸点在大球光晕旁更显黯淡；
        // 小球多球场景（首页）维持裸点，多球 × 光晕会拖垮帧率
        p.glow = orbR >= dp(36f)
        return true
    }

    private fun spawnAtAnchorOrbit(p: Particle, anchor: Int) {
        // 球缘外环带生成（内缘 1.10×、外缘 1.56× 球半径）：球体不透明，尘点生成在
        // 球内会被盖住；内缘留 10% 空隙避免"贴边"，外缘摊宽后大球下是环不是描边。
        // 字段约定与 ORBIT 更新的运动学一致：seed=方位角 θ / vx=径向相位 / vy=振幅系数
        p.seed = random.nextFloat() * 6.28f
        p.vx = random.nextFloat() * 6.28f
        p.vy = 0.35f + random.nextFloat() * 0.65f
        val orbR = anchorR[anchor].coerceAtLeast(1f)
        val rr = orbR * (1.10f + 0.46f * p.vy * (0.5f + 0.5f * sin(p.vx)))
        p.x = anchorX[anchor] + cos(p.seed) * rr
        p.y = anchorY[anchor] + sin(p.seed) * rr
    }

    /** PENDING：300–600ms 向心聚合单发 */
    private fun spawnPending(ax: Float, ay: Float, ar: Float, color: Int) {
        val count = budget.pending.coerceAtMost(20)
        // pool 满（obtain 返回 null）时非局部 return 停止发射
        repeat(count) {
            val p = pool.obtain() ?: return
            configureConverge(p, ax, ay, ar, color, 300L + nextLongBound(300L))
        }
    }

    /** REREAD：重读过渡的向心聚合单发（档位预算 reread：40/20/0） */
    private fun spawnReread(ax: Float, ay: Float, ar: Float, color: Int) {
        repeat(budget.reread) {
            val p = pool.obtain() ?: return
            configureConverge(p, ax, ay, ar, color, 350L + nextLongBound(250L))
        }
    }

    /** DISSOLVE：0–1000ms 沿轨道向外散逸 */
    private fun spawnDissolve(ax: Float, ay: Float, ar: Float, color: Int) {
        val count = budget.dissolve
        repeat(count) {
            val p = pool.obtain() ?: return
            val angle = random.nextFloat() * 6.28f
            val speed = dp(30f + random.nextFloat() * 70f)
            p.flow = ParticleFlow.DIVERGE.ordinal.toByte()
            p.x = ax + cos(angle) * ar * 0.4f
            p.y = ay + sin(angle) * ar * 0.4f
            p.vx = cos(angle) * speed
            p.vy = sin(angle) * speed - dp(10f) // 略带上飘
            p.size = dp(1.2f + random.nextFloat() * 1.8f)
            p.life = 800L + nextLongBound(400L)
            p.loop = false
            p.color = color
            p.glow = false
            p.owner = OWNER_NONE
            p.seed = 0f
        }
    }

    /** INPUT_SPARK：光标附近 3–6 粒上飘，250ms 节流 */
    private fun spawnInputSpark(ax: Float, ay: Float) {
        if (budget.inputSpark <= 0) return
        if (lastSparkElapsed < SPARK_THROTTLE_MS) return
        lastSparkElapsed = 0L
        val count = budget.inputSpark
        repeat(count) {
            val p = pool.obtain() ?: return
            val angle = -1.57f + (random.nextFloat() - 0.5f) * 1.2f // 大致向上
            val speed = dp(18f + random.nextFloat() * 22f)
            p.flow = ParticleFlow.DIVERGE.ordinal.toByte()
            p.x = ax + (random.nextFloat() - 0.5f) * dp(18f)
            p.y = ay + (random.nextFloat() - 0.5f) * dp(10f)
            p.vx = cos(angle) * speed
            p.vy = sin(angle) * speed
            p.size = dp(1f + random.nextFloat())
            p.life = 350L + nextLongBound(200L)
            p.loop = false
            p.color = GLOW_GOLD
            p.glow = true // 光晕化：深底上 1dp 裸点不可见，预渲染光晕点位清晰可见
            p.owner = OWNER_NONE
            p.seed = 0f
        }
    }

    private fun configureConverge(p: Particle, ax: Float, ay: Float, ar: Float, color: Int, life: Long) {
        val angle = random.nextFloat() * 6.28f
        val startDist = ar * (2.2f + random.nextFloat() * 1.8f)
        p.flow = ParticleFlow.CONVERGE.ordinal.toByte()
        p.startX = ax + cos(angle) * startDist
        p.startY = ay + sin(angle) * startDist
        p.targetX = ax + (random.nextFloat() - 0.5f) * ar * 0.5f
        p.targetY = ay + (random.nextFloat() - 0.5f) * ar * 0.5f
        p.x = p.startX
        p.y = p.startY
        p.size = dp(1.4f + random.nextFloat() * 1.6f)
        p.life = life
        p.loop = false
        p.color = color
        p.glow = true
        p.owner = OWNER_NONE
        p.seed = 0f
        p.vx = 0f
        p.vy = 0f
    }

    // ================= 序列时间轴 =================

    private fun startSequence(preset: ParticlePreset, ax: Float, ay: Float, ar: Float) {
        // 互斥：后到序列覆盖先到（业务不等待动画，安全）
        pool.releaseAll()
        sequencePreset = preset
        sequenceElapsed = 0L
        seqAnchorX = ax
        seqAnchorY = ay
        seqAnchorR = ar
        seqFiredBatches = 0
    }

    private var seqAnchorX = 0f
    private var seqAnchorY = 0f
    private var seqAnchorR = 0f
    private var seqFiredBatches = 0

    private fun tickSequence(preset: ParticlePreset, dt: Long) {
        sequenceElapsed += dt
        when (preset) {
            // ASSEMBLE 三段：0–350 聚拢 / 350–800 内光悬停 / 800–1200 成球
            ParticlePreset.ASSEMBLE -> {
                if (seqFiredBatches == 0) fireSequenceBatch(budget.assemble * 55 / 100, TIME_GOLD, 1150L)
                if (sequenceElapsed >= 350 && seqFiredBatches == 1) fireSequenceBatch(budget.assemble * 25 / 100, GLOW_GOLD, 850L)
                if (sequenceElapsed >= 800 && seqFiredBatches == 2) fireSequenceBatch(budget.assemble * 20 / 100, TIME_GOLD, 400L)
                if (sequenceElapsed >= ASSEMBLE_TOTAL_MS) finishSequence(toState = MotionState.IDLE)
            }
            // UNSEAL 五阶段：0–400 聚拢 / 400–1000 内光 / 1000–1600 金环 / 1600–2800 峰值喷发 / 2800–3200 归稳
            ParticlePreset.UNSEAL -> {
                if (seqFiredBatches == 0) fireSequenceBatch(budget.unsealPeak * 15 / 100, TIME_GOLD, 1250L)
                if (sequenceElapsed >= 400 && seqFiredBatches == 1) fireSequenceBatch(budget.unsealPeak * 10 / 100, GLOW_GOLD, 900L)
                if (sequenceElapsed >= 1000 && seqFiredBatches == 2) fireSequenceBatch(budget.unsealPeak * 25 / 100, TIME_GOLD, 1100L)
                if (sequenceElapsed >= 1600 && seqFiredBatches == 3) fireSequenceBatch(budget.unsealPeak * 35 / 100, GLOW_GOLD, 1200L)
                if (sequenceElapsed >= 2800 && seqFiredBatches == 4) fireSequenceBatch(budget.unsealPeak * 15 / 100, TIME_GOLD, 400L)
                if (sequenceElapsed >= UNSEAL_TOTAL_MS) finishSequence(toState = MotionState.CONTENT)
            }

            else -> finishSequence(toState = MotionState.IDLE)
        }
    }

    private fun fireSequenceBatch(count: Int, color: Int, life: Long) {
        seqFiredBatches++
        for (i in 0 until count) {
            val p = pool.obtain() ?: return
            p.owner = OWNER_NONE
            // 序列批次一半向心、一半从锚点喷发（随阶段语义由颜色/寿命区分，视觉聚合为整体）
            if (i % 2 == 0) {
                configureConverge(p, seqAnchorX, seqAnchorY, seqAnchorR, color, life)
            } else {
                val angle = random.nextFloat() * 6.28f
                val speed = dp(60f + random.nextFloat() * 160f)
                p.flow = ParticleFlow.DIVERGE.ordinal.toByte()
                p.x = seqAnchorX + cos(angle) * seqAnchorR * 0.3f
                p.y = seqAnchorY + sin(angle) * seqAnchorR * 0.3f
                p.vx = cos(angle) * speed
                p.vy = sin(angle) * speed
                p.size = dp(1.2f + random.nextFloat() * 2f)
                p.life = (life * 0.8f).toLong().coerceAtLeast(200L)
                p.loop = false
                p.color = color
                p.glow = true
                p.seed = 0f
            }
        }
    }

    private fun finishSequence(toState: MotionState) {
        pool.releaseAll()
        sequencePreset = null
        sequenceElapsed = 0L
        seqFiredBatches = 0
        currentState = toState
        val cb = onSequenceFinished
        onSequenceFinished = null
        cb?.invoke()
    }

    /** 清除循环型粒子（保留序列粒子） */
    private fun clearLoopParticles() {
        for (i in 0 until pool.size()) {
            val p = pool.at(i)
            if (p.active && (p.flow == ParticleFlow.DRIFT.ordinal.toByte() || p.flow == ParticleFlow.ORBIT.ordinal.toByte())) {
                pool.release(p)
            }
        }
    }

    // ================= draw =================

    /** 是否有活动粒子（ParticleCanvas 据此决定是否触发重绘；空池时画布静默省电） */
    fun hasActiveParticles(): Boolean {
        for (i in 0 until pool.size()) {
            if (pool.at(i).active) return true
        }
        return false
    }

    /** 释放不属于 [owner] 的循环型粒子（换宿主认领时防跨页残影/陈旧锚点粒子） */
    private fun releaseLoopsExcept(owner: Int) {
        val drift = ParticleFlow.DRIFT.ordinal.toByte()
        val orbit = ParticleFlow.ORBIT.ordinal.toByte()
        for (i in 0 until pool.size()) {
            val p = pool.at(i)
            if (p.active && p.owner != owner && (p.flow == drift || p.flow == orbit)) {
                pool.release(p)
            }
        }
    }

    /** 只读绘制（与 tick 分离；由 ParticleCanvas 在 Compose 帧内调用）。
     *  [viewerOwner]：循环型粒子（背景漂移/内流）只绘给宿主画布——
     *  共享引擎下其他页面在切页重叠窗口不再为他页尘盐买单（残影从源头消除）；
     *  一次性粒子（owner = OWNER_NONE）处处可绘。 */
    fun draw(canvas: Canvas, viewerOwner: Int = OWNER_NONE) {
        val drift = ParticleFlow.DRIFT.ordinal.toByte()
        val orbit = ParticleFlow.ORBIT.ordinal.toByte()
        for (i in 0 until pool.size()) {
            val p = pool.at(i)
            if (!p.active || p.alpha <= 0.02f) continue
            if ((p.flow == drift || p.flow == orbit) && p.owner != viewerOwner) continue
            if (p.glow) {
                GlowPainter.drawGlow(canvas, p.x, p.y, p.size * GLOW_SCALE, p.color, p.alpha)
            } else {
                GlowPainter.drawDot(canvas, p.x, p.y, p.size, p.color, p.alpha)
            }
        }
    }

    companion object {

        /** 首页同屏球数上限（超出部分无锚点，视觉仍由静态绘制兜底） */
        const val MAX_ORBS = 24

        /** 循环尘宿主页标识（ParticleCanvas 绘制过滤用；NONE = 该画布不渲染任何循环尘） */
        const val OWNER_NONE = 0
        const val OWNER_HOME = 1
        const val OWNER_DETAIL = 2

        /** 输入飘粒节流间隔 */
        const val SPARK_THROTTLE_MS = 250L

        /** ASSEMBLE 三段总时长（0–350 / 350–800 / 800–1200ms） */
        const val ASSEMBLE_TOTAL_MS = 1200L

        /** UNSEAL 五阶段总时长（3.2s） */
        const val UNSEAL_TOTAL_MS = 3200L

        /** 光晕直径 / 粒径倍率 */
        private const val GLOW_SCALE = 5f

        /** 时金 ARGB（与 ui.theme.TimeGold 同值，避免引擎依赖 Compose 类型） */
        const val TIME_GOLD = 0xFFE8B44A.toInt()

        /** 光晕金 ARGB（与 ui.theme.GlowGold 同值） */
        const val GLOW_GOLD = 0xFFF5D08C.toInt()

        private val DUST_COLORS = intArrayOf(
            0x99EDE6D8.toInt(), // InkPrimary（暖白，主尘色：深底上更清晰，与金色球体拉开对比）
            0x73A89F92, // InkSecondary（暖灰次尘色）
            0x59F5D08C, // GlowGold（少量金尘点缀）
        )
    }
}
