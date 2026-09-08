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
    )

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

        // 内容加密（密文形态入库）
        val cipher = crypto.encryptContent(draft.contentText.toByteArray(Charsets.UTF_8))

        // 图片逐张加密落盘（文件本体在 filesDir/images/{id}/）
        draft.imageBytes.forEachIndexed { index, bytes -> imageStore.save(id, index, bytes) }
        val imageFiles = draft.imageBytes.indices.map { "img_$it.bin" }

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
        // 1. 物理删除图片目录
        imageStore.deleteDir(id)
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
    }
}


private fun crudMsgs(lang: Lang) = when (lang) {
    Lang.ZH_HANS -> "口令未设置或未解锁，无法封存"
    Lang.ZH_HANT -> "密碼未設定或未解鎖，無法封存"
    Lang.EN -> "Passphrase not set or not unlocked; cannot seal"
}
