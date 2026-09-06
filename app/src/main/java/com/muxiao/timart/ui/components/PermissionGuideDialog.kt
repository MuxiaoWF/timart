package com.muxiao.timart.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TimartType

/**
 * 权限引导弹窗：**说明先于系统弹窗**。
 * 用户先看到「这个权限用来做什么 + 拒绝会怎样」，确认后才触发系统权限对话框。
 * 架构红线：拒绝不影响任何判定，仅对应条件显示不满足原因。
 *
 * @param title   说明标题（如「开启位置权限」）
 * @param body    说明正文（建议复用 strings.xml 的 permission_*_desc）
 * @param onConfirm 用户确认后触发系统权限申请（调用方持有 launcher）
 */
@Composable
fun PermissionGuideDialog(
    title: String,
    body: String,
    confirmText: String? = null,
    dismissText: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceRaise,
        title = { Text(text = title, style = TimartType.titleSerif) },
        text = {
            Text(text = body, style = TimartType.caption, color = InkSecondary)
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
            ) {
                Text(text = confirmText ?: LocalStrings.current.goEnable)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissText ?: LocalStrings.current.temporarilyNot, color = InkSecondary)
            }
        },
    )
}
