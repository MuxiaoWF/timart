package com.muxiao.timart.utils.time

import com.muxiao.timart.domain.context.TimeProvider
import java.time.LocalDate
import java.time.LocalTime

/** TimeProvider 实现：直接读系统时间（PRD 明示不防篡改） */
class SystemTimeProvider : TimeProvider {

    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun today(): LocalDate = LocalDate.now()

    override fun nowHour(): Int = LocalTime.now().hour
}
