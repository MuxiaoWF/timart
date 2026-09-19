package com.muxiao.timart.utils.app

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * 音量键事件总线（VolumeKeyCombo 挑战判定输入）：
 * MainActivity.onKeyDown/onKeyUp 转发音量上/下键的按下/抬起事件，挑战组件在场时收集。
 * 不拦截系统音量行为（转发后仍走 super，系统音量照常生效）。
 */
object VolumeKeyEventBus {

    const val KEY_UP = android.view.KeyEvent.KEYCODE_VOLUME_UP
    const val KEY_DOWN = android.view.KeyEvent.KEYCODE_VOLUME_DOWN

    /** (键位, 是否按下)；false = 抬起 */
    private val _events = MutableSharedFlow<Pair<Int, Boolean>>(extraBufferCapacity = 16)
    val events: SharedFlow<Pair<Int, Boolean>> = _events

    /** MainActivity 转发入口；无订阅者时事件自然丢弃（挑战不在场） */
    fun dispatch(keyCode: Int, isPressed: Boolean) {
        _events.tryEmit(keyCode to isPressed)
    }
}
