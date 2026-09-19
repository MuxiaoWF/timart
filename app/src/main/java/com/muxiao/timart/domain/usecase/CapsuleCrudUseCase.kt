package com.muxiao.timart.domain.usecase

import com.muxiao.timart.domain.model.Lang
import com.muxiao.timart.data.local.crypto.ContentCryptoManager
import com.muxiao.timart.data.local.crypto.CryptoException
import com.muxiao.timart.data.local.crypto.ImageCipherStore
import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.DestroyRecord
import com.muxiao.timart.domain.model.WeatherSnapshot
import com.muxiao.timart.domain.model.unlock.UnlockRule
import com.muxiao.timart.domain.repository.CapsuleRepository
import com.muxiao.timart.domain.repository.DestroyedRepository
import java.util.UUID

/**
 * 胶囊 CRUD 编排（封存提交链路：口令校验 → 内容加密 → 图片加密落盘 → 规则序列化 → 插入）。
 * 不可 mock 的部分全部经接口注入；必须在 IO/Default 协程中调用。
 */
class CapsuleCrudUseCase(
    private val capsules: CapsuleRepository,
    private val destroyedRecords: DestroyedRepository,
    private val crypto: ContentCryptoManager,
    private val imageStore: ImageCipherStore,
    /** 加密语音仓库（体验储备池 §1 声音留言；null = 未装配，跳过语音落盘与清理） */
    private val audioStore: com.muxiao.timart.data.local.crypto.AudioCipherStore? = null,
    /** meta DAO（null = 未装配，跳过删除时的 meta 清理） */
    private val metaDao: com.muxiao.timart.data.local.db.MetaDao? = null,
) {

    /** 新建草稿（创建流程三步共享状态最终提交形态） */
    data class CapsuleDraft(
        val title: String,
        val contentText: String,
        val imageBytes: List<ByteArray> = emptyList(),
        val snapshot: WeatherSnapshot? = null,
        val rule: UnlockRule,
        val tags: List<String> = emptyList(),
        val note: String = "",
        val dependCapsuleId: String? = null,
        val autoDestroyAfterRead: Boolean = false,
        /** 盲盒封存：解锁前预览面遮蔽标题与条件句（连自己也保密） */
        val blindBox: Boolean = false,
        /** 声音留言（体验储备池 §1；null = 无语音，封存时加密落 filesDir/audio/） */
        val voiceBytes: ByteArray? = null,
        /**
         * 口令分片计划（体验储备池 §7.1；null = 普通胶囊）。
         * 内层密文已由创建侧用 k2 加密完毕；入库时再套一层现有会话密钥（双层：外层会话、内层分片重构）。
         * 分片胶囊仅支持纯文本（照片/语音不参与内层加密，为守住「集齐分片才可见」一并禁用）。
         */
        val shardPlan: ShardPlan? = null,
    ) {
        override fun equals(other: Any?): Boolean =
            other is CapsuleDraft &&
                other.title == title &&
                other.contentText == contentText &&
                other.imageBytes.size == imageBytes.size &&
                other.imageBytes.zip(imageBytes).all { (a, b) -> a.contentEquals(b) } &&
                other.snapshot == snapshot &&
                other.rule == rule &&
                other.tags == tags &&
                other.note == note &&
                other.dependCapsuleId == dependCapsuleId &&
                other.autoDestroyAfterRead == autoDestroyAfterRead &&
                other.blindBox == blindBox &&
                other.voiceBytes.contentEquals(voiceBytes) &&
                other.shardPlan == shardPlan

        override fun hashCode(): Int = title.hashCode() * 31 + contentText.hashCode()
    }

    /** 分片胶囊封存计划（k2 派生材料随胶囊列存，分片份额永不落设备） */
    data class ShardPlan(
        val saltB64: String,
        val kdfParamsJson: String,
        val verifierHex: String,
        val threshold: Int,
        val total: Int,
        val innerCipher: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean =
            other is ShardPlan &&
                other.saltB64 == saltB64 &&
                other.kdfParamsJson == kdfParamsJson &&
                other.verifierHex == verifierHex &&
                other.threshold == threshold &&
                other.total == total &&
                other.innerCipher.contentEquals(innerCipher)

        override fun hashCode(): Int = saltB64.hashCode() * 31 + verifierHex.hashCode()
    }

    /**
     * 封存胶囊。口令未解锁抛 [CryptoException]（UI 应先引导 PASSWORD_SETUP / 解锁）。
     * @return 持久化完成的胶囊（含生成 id 与创建时间）
     */
    suspend fun create(draft: CapsuleDraft, lang: Lang = Lang.ZH_HANS): Capsule {
        if (!crypto.isUnlocked) {
            throw CryptoException(crudMsgs(lang))
        }
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // 分片胶囊仅纯文本：照片/语音无法参与内层加密，封存侧禁用之外这里再兜底（fail-closed）
        if (draft.shardPlan != null) {
            if (draft.imageBytes.isNotEmpty() || draft.voiceBytes != null) {
                throw IllegalStateException(shardTextOnlyMsgs(lang))
            }
        }

        // 内容加密（密文形态入库）。分片胶囊：明文先由创建侧用 k2 加成内层密文，
        // 这里把内层密文当作「明文」套外层会话密钥——形成双层（外层会话、内层分片重构）
        val cipher = if (draft.shardPlan != null) {
            crypto.encryptContent(draft.shardPlan.innerCipher)
        } else {
            crypto.encryptContent(draft.contentText.toByteArray(Charsets.UTF_8))
        }

        // 图片逐张加密落盘（文件本体在 filesDir/images/{id}/）
        draft.imageBytes.forEachIndexed { index, bytes -> imageStore.save(id, index, bytes) }
        val imageFiles = draft.imageBytes.indices.map { "img_$it.bin" }

        // 语音留言加密落盘（filesDir/audio/{id}/audio_0.bin；当前限一条）
        draft.voiceBytes?.let { bytes -> audioStore?.save(id, 0, bytes) }

        val capsule = Capsule(
            id = id,
            title = draft.title,
            contentCipher = cipher,
            imageFiles = imageFiles,
            createTimestamp = now,
            weather = draft.snapshot,
            unlockRule = draft.rule,
            state = CapsuleState.LOCKED,
            autoDestroyAfterRead = draft.autoDestroyAfterRead,
            dependCapsuleId = draft.dependCapsuleId,
            tags = draft.tags,
            createNote = draft.note,
            blindBox = draft.blindBox,
            shardSalt = draft.shardPlan?.saltB64,
            shardParams = draft.shardPlan?.kdfParamsJson,
            shardVerifier = draft.shardPlan?.verifierHex,
            shardThreshold = draft.shardPlan?.threshold,
            shardTotal = draft.shardPlan?.total,
        )
        capsules.insert(capsule)
        return capsule
    }

    /** 更新非加密元信息（标题/标签/备注/规则等，密文不动） */
    suspend fun update(capsule: Capsule) {
        capsules.update(capsule)
    }

    /**
     * 销毁：立即物理删除密文与图片文件（不可逆）→ 状态 DESTROYED + 销毁时间 → 写入销毁元记录。
     * 调用前 UI 必须完成二次确认，不存在静默销毁路径。
     */
    suspend fun markDestroyed(id: String) {
        val capsule = capsules.byIdSync(id) ?: return
        // 1. 物理删除图片目录与语音目录（不可逆）
        imageStore.deleteDir(id)
        audioStore?.deleteDir(id)
        // 2. 密文置空 + 状态 DESTROYED（销毁时间经 updateState 回填）
        capsules.update(capsule.copy(contentCipher = null, state = CapsuleState.DESTROYED))
        capsules.updateState(id, CapsuleState.DESTROYED, System.currentTimeMillis())
        // 3. 写入销毁元记录（仅标题与时间）
        destroyedRecords.record(
            DestroyRecord(
                id = capsule.id,
                title = capsule.title,
                createdAt = capsule.createTimestamp,
                destroyedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** 删除胶囊本体（不写销毁档案，用于清理未完成流程的草稿残留） */
    suspend fun delete(id: String) {
        capsules.delete(id)
        imageStore.deleteDir(id)
        audioStore?.deleteDir(id)
        // 物理删除时清掉影响分组语义的 meta：拼图占位（防死组）、嵌套归属（防幽灵休眠引用）。
        // 回信 / 生平刻度 / 信纸样式 / 已读标记刻意保留（与既有行为一致，删除不留档案但元痕迹无害）
        runCatching {
            val dao = metaDao ?: return
            val keys = com.muxiao.timart.data.local.db.CapsuleMetaKeys
            listOf(
                keys.puzzle(id),
                keys.seedOf(id),
                keys.sprout(id),
            ).forEach { key ->
                runCatching { dao.delete(key) }
            }
        }
    }
}


private fun crudMsgs(lang: Lang) = when (lang) {
    Lang.ZH_HANS -> "口令未设置或未解锁，无法封存"
    Lang.ZH_HANT -> "密碼未設定或未解鎖，無法封存"
    Lang.EN -> "Passphrase not set or not unlocked; cannot seal"
}

private fun shardTextOnlyMsgs(lang: Lang) = when (lang) {
    Lang.ZH_HANS -> "分片胶囊仅支持纯文本，请移除照片与声音留言"
    Lang.ZH_HANT -> "分片膠囊僅支援純文字，請移除照片與聲音留言"
    Lang.EN -> "Shard capsules support text only; remove photos and voice notes"
}
