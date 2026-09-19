package com.muxiao.timart.data.local.db

/**
 * 「胶囊一生」meta key 契约（跨 VM 契约，注释互指——见 docs/pitfalls.md 坑 #26）：
 *
 * - `capsule.condMet.<capsuleId>.<index>`：条件**首次**被观测为满足的时刻（epoch millis），
 *   尘迹生平页「条件逐个达成时刻线」的数据源。
 *   写入点：MainActivity ON_RESUME 全量判定（经 UnlockJudgeUseCase.judgeAllLocked 的
 *   onConditionSatisfied 回调）与 DetailViewModel.judgeOnce（进页/30s 周期/挑战提交复判）；
 *   两处均**只写不覆写**（get == null 才写），先到先记。
 *   读取点：DustRecordsViewModel.biography（生平时间线）。
 * - 既有契约未迁动：`capsule.read.*` / `capsule.readAt.*`（DetailViewModel.markAsRead）、
 *   `capsule.views.*` / `capsule.watch.*`（DetailViewModel init）、`capsule.condEdit.*`（后悔药）。
 * - `capsule.paper.<capsuleId>`：信纸样式（体验储备池 §1，PaperStyle 序号，缺省 0 = 原纸）。
 *   写入点 = CreateViewModel.performCreate（封存成功后落标记）；读取点 = DetailViewModel（解封时传给信笺呈现）。
 * - `capsule.reply.<capsuleId>`：回信（体验储备池 §5，一句附言，纯文本）。写入点 = DetailViewModel.saveReply
 *   （CONTENT 态可写可改，**销毁路径不触碰本 key**——回信随档案留存）；读取点 = DustRecordsViewModel.biography（生平页回信行）。
 * - `capsule.puzzle.<capsuleId>`：拼图分组（体验储备池 §3，值 = `<groupId>|<index>|<total>`，
 *   写入点 = CreateViewModel.performCreate；读取点 = DetailViewModel 合信视图。
 * - `capsule.seedOf.<capsuleId>`：嵌套种子（体验储备池 §3，值 = 父胶囊 id）。写入点 = CreateViewModel.performCreate。
 * - `capsule.sprout.<capsuleId>`：种子已萌芽（父胶囊已读后播种成功）。写入点 = DetailViewModel.markAsRead；
 *   「休眠 = 有 seedOf 且无 sprout」的判定点 = HomeViewModel 过滤 / AppContainer.isSeedDormant（MainActivity、Worker 判定跳过）。
 */
object CapsuleMetaKeys {

    private const val COND_MET_PREFIX = "capsule.condMet."

    private const val PAPER_PREFIX = "capsule.paper."

    private const val REPLY_PREFIX = "capsule.reply."

    private const val PUZZLE_PREFIX = "capsule.puzzle."

    private const val SEED_OF_PREFIX = "capsule.seedOf."

    private const val SPROUT_PREFIX = "capsule.sprout."

    /** 条件达成时刻 key（index = 解锁规则条件列表下标，0 起） */
    fun condMet(capsuleId: String, index: Int): String = "$COND_MET_PREFIX$capsuleId.$index"

    /** 信纸样式 key（值 = PaperStyle 序号字符串；读取失败/缺省按 0 原纸） */
    fun paper(capsuleId: String): String = "$PAPER_PREFIX$capsuleId"

    /** 回信 key（值 = 回信文本；一次一句，可改写） */
    fun reply(capsuleId: String): String = "$REPLY_PREFIX$capsuleId"

    /** 拼图分组 key（值 = [puzzleValue] 编码；groupId 须为无 `|` 的 UUID 形态） */
    fun puzzle(capsuleId: String): String = "$PUZZLE_PREFIX$capsuleId"

    /** 拼图分组值编码（index 0 起、total ≥ 2） */
    fun puzzleValue(groupId: String, index: Int, total: Int): String = "$groupId|$index|$total"

    /** 拼图分组值解析 → (groupId, index, total)；格式不符返回 null（fail-closed，按未分组处理） */
    fun puzzleDecodeValue(value: String): Triple<String, Int, Int>? {
        val parts = value.split('|')
        if (parts.size != 3) return null
        val index = parts[1].toIntOrNull() ?: return null
        val total = parts[2].toIntOrNull() ?: return null
        return Triple(parts[0], index, total)
    }

    /** 拼图分组 key 前缀（DAO 前缀扫描用） */
    const val PUZZLE_KEY_PREFIX = PUZZLE_PREFIX

    /** 嵌套种子 key（值 = 父胶囊 id） */
    fun seedOf(capsuleId: String): String = "$SEED_OF_PREFIX$capsuleId"

    /** 种子萌芽标记 key（值 = "true"；父胶囊已读后写入） */
    fun sprout(capsuleId: String): String = "$SPROUT_PREFIX$capsuleId"

    /** 嵌套种子 key 前缀 / 萌芽标记 key 前缀（DAO 前缀扫描用） */
    const val SEED_OF_KEY_PREFIX = SEED_OF_PREFIX
    const val SPROUT_KEY_PREFIX = SPROUT_PREFIX

    private const val HAPTIC_PREFIX = "capsule.haptic."

    private const val AMBIENT_PREFIX = "capsule.ambient."

    /** 触觉签名 key（体验储备池 §6；值 = 纹样序号 0=无 1=双击 2=长振 3=涟漪）。
     *  写入点 = CreateViewModel.performCreate；播放点 = AppContainer.playCapsuleHaptic（解锁/销毁事件） */
    fun haptic(capsuleId: String): String = "$HAPTIC_PREFIX$capsuleId"

    /** 环境音场景 key（体验储备池 §6；值 = AmbientSoundPlayer.Scene 枚举名，OFF = 不播）。
     *  写入点 = CreateViewModel.performCreate；读取点 = DetailViewModel（解封淡入 / 离场淡出） */
    fun ambient(capsuleId: String): String = "$AMBIENT_PREFIX$capsuleId"
}
