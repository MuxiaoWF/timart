package com.muxiao.timart.data.local.db.mapper

import com.muxiao.timart.data.local.db.Converters
import com.muxiao.timart.data.local.db.entity.CapsuleEntity
import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.model.CapsuleState
import com.muxiao.timart.domain.model.WeatherSnapshot
import com.muxiao.timart.domain.model.WeatherType

/**
 * CapsuleEntity ↔ Capsule 双向映射。
 * contentCipher 以密文形态进出领域模型；imageFiles/tags 列经 JSON 解析。
 */
object CapsuleMapper {

    /** 实体 → 领域模型（规则解析失败时回退为空 AND 规则，不抛异常） */
    fun toDomain(entity: CapsuleEntity): Capsule = Capsule(
        id = entity.id,
        title = entity.title,
        contentCipher = entity.contentCipher,
        imageFiles = Converters.jsonToStrings(entity.imageFiles),
        createTimestamp = entity.createTimestamp,
        unlockTimestamp = entity.unlockTimestamp,
        weather = entity.toWeatherSnapshot(),
        unlockRule = UnlockRuleJson.fromJson(entity.unlockRuleJson),
        state = runCatching { CapsuleState.valueOf(entity.state) }.getOrDefault(CapsuleState.LOCKED),
        autoDestroyAfterRead = entity.autoDestroyAfterRead,
        dependCapsuleId = entity.dependCapsuleId,
        tags = Converters.jsonToStrings(entity.tags),
        createNote = entity.createNote,
        layoutX = entity.layoutX,
        layoutY = entity.layoutY,
    )

    /**
     * 领域模型 → 实体。
     * layoutX/Y 随领域模型往返（用户自定义星图坐标）；
     * destroyTimestamp 不在领域模型上（销毁经 updateState 的 SQL CASE 回填），
     * 按调用方传入值保留，避免内容更新抹掉既有列。
     */
    fun toEntity(
        capsule: Capsule,
        layoutX: Float? = capsule.layoutX,
        layoutY: Float? = capsule.layoutY,
        destroyTimestamp: Long? = null,
    ): CapsuleEntity = CapsuleEntity(
        id = capsule.id,
        title = capsule.title,
        contentCipher = capsule.contentCipher,
        imageFiles = Converters.stringsToJson(capsule.imageFiles),
        createTimestamp = capsule.createTimestamp,
        unlockTimestamp = capsule.unlockTimestamp,
        weatherCityId = capsule.weather?.cityId,
        weatherCityName = capsule.weather?.cityName,
        weatherType = capsule.weather?.weatherType?.name,
        weatherTempC = capsule.weather?.tempC,
        unlockRuleJson = UnlockRuleJson.toJson(capsule.unlockRule),
        state = capsule.state.name,
        autoDestroyAfterRead = capsule.autoDestroyAfterRead,
        dependCapsuleId = capsule.dependCapsuleId,
        tags = Converters.stringsToJson(capsule.tags),
        createNote = capsule.createNote,
        layoutX = layoutX,
        layoutY = layoutY,
        destroyTimestamp = destroyTimestamp,
    )

    /** 天气快照四列 → WeatherSnapshot（任一缺失视为空快照） */
    private fun CapsuleEntity.toWeatherSnapshot(): WeatherSnapshot? {
        val cityId = weatherCityId ?: return null
        val typeName = weatherType ?: return null
        val type = runCatching { WeatherType.valueOf(typeName) }.getOrNull() ?: return null
        val temp = weatherTempC ?: return null
        return WeatherSnapshot(
            cityId = cityId,
            cityName = weatherCityName ?: "",
            weatherType = type,
            tempC = temp,
            capturedAt = createTimestamp,
        )
    }
}
