package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.rerere.rikkahub.data.export.ImportCompatibilityReport
import me.rerere.rikkahub.data.export.ImportFindingLevel
import me.rerere.rikkahub.utils.writeClipboardText

/** Lives above import sheets so closing a picker or sheet does not discard its result. */
object ImportReportState {
    private val pending = MutableStateFlow<ImportCompatibilityReport?>(null)
    val report = pending.asStateFlow()
    fun show(report: ImportCompatibilityReport) { pending.value = report }
    fun dismiss() { pending.value = null }
}

@Composable
fun ImportReportDialogHost() {
    val report by ImportReportState.report.collectAsStateWithLifecycle()
    val current = report ?: return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = ImportReportState::dismiss,
        title = { Text("导入兼容性报告") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("${current.kind.label}内容：导入 ${current.imported} 项，跳过 ${current.skipped} 项")
                }
                items(current.findings) { finding ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            finding.level.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (finding.level == ImportFindingLevel.ERROR) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                        )
                        Text(finding.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    Text("报告仅覆盖已识别的功能，未列出的第三方扩展不代表完整兼容。", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = ImportReportState::dismiss) { Text("完成") } },
        dismissButton = {
            TextButton(onClick = { context.writeClipboardText(current.toPlainText()) }) { Text("复制报告") }
        },
    )
}
