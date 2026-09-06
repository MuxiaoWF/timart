package com.muxiao.timart.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 设计 Token 常量（ARCHITECTURE §10）。
 * 状态表达靠亮度/密度/粒子速度，禁止绿勾红叉与徽章堆叠；
 * PENDING 用 LockedSlate ↔ TimeGold 插值，不新增第三套高饱和色。
 */
val DeepCharcoal = Color(0xFF121110) // 全局背景（大面积留黑）
val SurfaceRaise = Color(0xFF1C1A17) // 浮层/预览卡底（仅比背景微亮，不做玻璃拟态）
val TrackHairline = Color(0xFF2E2A25) // 时轨极细尘线（低对比，不长期旋转）
val TimeGold = Color(0xFFE8B44A) // 主强调：CTA、UNLOCKED 核心、条件满足
val GlowGold = Color(0xFFF5D08C) // 光晕边缘
val LockedSlate = Color(0xFF5A6B7A) // LOCKED 冷灰青
val PaperCream = Color(0xFFF2EDE3) // 信笺纸色（仅解锁后出现）
val PaperInk = Color(0xFF3A362F) // 纸面正文墨色（纸色上的深字）
val InkPrimary = Color(0xFFEDE6D8) // 主文字（暖白）
val InkSecondary = Color(0xFFA89F92) // 次级文字
val InkDisabled = Color(0xFF6B655C) // 弱化文字
val DustAsh = Color(0xFF7A7268) // DESTROYED 暖灰虚影（不做错误/警告感）
