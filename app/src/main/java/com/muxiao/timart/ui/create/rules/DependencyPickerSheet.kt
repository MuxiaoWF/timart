package com.muxiao.timart.ui.create.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muxiao.timart.domain.model.Capsule
import com.muxiao.timart.domain.usecase.DependencyError
import com.muxiao.timart.domain.usecase.DependencySeverity
import com.muxiao.timart.l10n.LocalStrings
import com.muxiao.timart.ui.create.CreateViewModel
import com.muxiao.timart.ui.theme.InkDisabled
import com.muxiao.timart.ui.theme.InkPrimary
import com.muxiao.timart.ui.theme.InkSecondary
import com.muxiao.timart.ui.theme.SurfaceRaise
import com.muxiao.timart.ui.theme.TimartType
import com.muxiao.timart.ui.theme.TimeGold
import com.muxiao.timart.ui.theme.TrackHairline
import kotlinx.coroutines.launch

/**
 * 依赖胶囊选择面板（架构 §2.15）：
 * 候选 = 全部 LOCKED 胶囊；选择即走 [com.muxiao.timart.domain.usecase.DependencyGraphUseCase]
 * 校验——ERROR 禁止选择并提示；WARNING（阅读销毁依赖）弹二次确认；
 * 已设置依赖时显示标题并可清除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DependencyPickerSheet(
    vm: CreateViewModel,
    onDismiss: () -> Unit,
) {
    val L = LocalStrings.current
    var candidates by remember { mutableStateOf<List<Capsule>>(emptyList()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var warning by remember { mutableStateOf<Pair<Capsule, DependencyError>?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        candidates = vm.lockedCandidates()
    }

    fun tryPick(capsule: Capsule) {
        scope.launch {
            val error = vm.validateDependency(capsule.id)
            when {
                error == null -> {
                    vm.setDependency(capsule.id, capsule.title)
                    onDismiss()
                }

                error.severity == DependencySeverity.ERROR -> errorText = error.message
                else -> warning = capsule to error
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceRaise,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .padding(horizontal = 24.dp),
        ) {
            Text(text = L.dependRowTitle, style = TimartType.titleSerif, color = InkPrimary)
            Text(
                text = L.depSheetDesc,
                style = TimartType.caption,
                color = InkSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )

            // 当前依赖
            if (vm.dependTitle != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                ) {
                    Text(
                        text = L.depCurrentFmt.format(vm.dependTitle ?: ""),
                        style = TimartType.body,
                        color = TimeGold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            vm.setDependency(null, null)
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = InkSecondary),
                    ) {
                        Text(text = L.depClear, style = TimartType.caption)
                    }
                }
            }

            // 校验错误（ERROR 禁止选择）
            errorText?.let { message ->
                Text(
                    text = message,
                    style = TimartType.caption,
                    color = InkPrimary,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            LazyColumn(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
            ) {
                items(
                    count = candidates.size,
                    key = { i -> candidates[i].id },
                ) { i ->
                    val capsule = candidates[i]
                    val selected = capsule.id == vm.dependCapsuleId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { tryPick(capsule) }
                            .padding(vertical = 13.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(
                                    color = if (selected) TimeGold else TrackHairline,
                                    shape = CircleShape,
                                ),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                        ) {
                            Text(
                                text = capsule.title,
                                style = TimartType.body,
                                color = if (selected) TimeGold else InkPrimary,
                            )
                            Text(
                                text = com.muxiao.timart.utils.format.TimeFormatter.sealDate(capsule.createTimestamp),
                                style = TimartType.caption,
                                color = InkDisabled,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        if (capsule.autoDestroyAfterRead) {
                            Text(
                                text = L.depDestroyTag,
                                style = TimartType.caption,
                                color = InkSecondary,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(TrackHairline),
                    )
                }
                if (candidates.isEmpty()) {
                    item {
                        Text(
                            text = L.depEmpty,
                            style = TimartType.body,
                            color = InkSecondary,
                            modifier = Modifier.padding(top = 32.dp, bottom = 24.dp),
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    // 自动销毁依赖强警告：二次确认后才允许设置
    warning?.let { (capsule, error) ->
        AlertDialog(
            onDismissRequest = { warning = null },
            containerColor = SurfaceRaise,
            title = {
                Text(text = L.depConfirmTitle, style = TimartType.titleSerif, color = InkPrimary)
            },
            text = {
                Text(text = error.message, style = TimartType.body, color = InkSecondary)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        warning = null
                        vm.setDependency(capsule.id, capsule.title)
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = TimeGold),
                ) {
                    Text(text = L.depStillDepend, style = TimartType.body)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { warning = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = InkSecondary),
                ) {
                    Text(text = L.cancel, style = TimartType.body)
                }
            },
        )
    }
}
