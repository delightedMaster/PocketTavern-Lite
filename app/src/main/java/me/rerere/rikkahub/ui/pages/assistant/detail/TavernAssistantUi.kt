package me.rerere.rikkahub.ui.pages.assistant.detail

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.File01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.Message02
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.CardExporter
import me.rerere.ui.toast.ToasterState

@Composable
internal fun GreetingSelectorCard(
    assistant: Assistant,
    onSelectGreeting: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tav = assistant.tavernData ?: return
    val allGreetings = listOfNotNull(tav.firstMessage.takeIf { it.isNotBlank() }) +
        tav.alternateGreetings.filter { it.isNotBlank() }
    if (allGreetings.isEmpty()) return

    val currentGreeting = assistant.presetMessages
        .firstOrNull { it.role == MessageRole.ASSISTANT }
        ?.toText()
        .orEmpty()
    var showSheet by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showSheet = true },
        colors = CardDefaults.cardColors(
            containerColor = CustomColors.listItemColors.containerColor,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                HugeIcons.Message02,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.tavern_greeting_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (currentGreeting.isNotBlank()) {
                        currentGreeting.replace("\n", " ").take(80)
                    } else {
                        stringResource(R.string.tavern_greeting_unselected, allGreetings.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(R.string.tavern_greeting_switch),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                HugeIcons.ArrowRight01,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }

    if (showSheet) {
        GreetingPickerSheet(
            allGreetings = allGreetings,
            currentGreeting = currentGreeting,
            onSelect = {
                onSelectGreeting(it)
                showSheet = false
            },
            onDismiss = { showSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GreetingPickerSheet(
    allGreetings: List<String>,
    currentGreeting: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CustomColors.listItemColors.containerColor,
        dragHandle = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.tavern_greeting_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                }) {
                    Icon(HugeIcons.Cancel01, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        },
    ) {
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(allGreetings) { index, greeting ->
                val isSelected = greeting == currentGreeting
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isSelected) { onSelect(greeting) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                    ),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "G${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .width(28.dp),
                        )
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 20.sp,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.tavern_greeting_current),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.tavern_greeting_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
internal fun ExportCardDialog(
    assistant: Assistant,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val exportScope = remember { CoroutineScope(Dispatchers.Main + SupervisorJob()) }
    var downloadingAvatar by remember { mutableStateOf(false) }
    val avatarUrl = (assistant.avatar as? Avatar.Image)?.url
    val pngImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { pickedUri: Uri? ->
        if (pickedUri == null) return@rememberLauncherForActivityResult
        onDismiss()
        exportPng(context, exportScope, toaster, assistant, pickedUri)
    }
    val avatarUri = runCatching {
        val uri = avatarUrl?.toUri() ?: return@runCatching null
        val scheme = uri.scheme
        if (scheme == "content" || scheme == "file") uri else null
    }.getOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tavern_export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        HugeIcons.Folder01,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.tavern_export_download_dir),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CardGroup {
                    item(
                        onClick = {
                            onDismiss()
                            exportScope.launch {
                                try {
                                    val json = CardExporter.buildV3CardJson(assistant)
                                    val fileName = exportFileName(assistant.name, "json")
                                    saveToDownloads(context, fileName, "application/json", json.toByteArray())
                                    toaster.show(context.getString(R.string.tavern_export_json_success, fileName))
                                } catch (e: Exception) {
                                    toaster.show(context.getString(R.string.tavern_export_failed, e.message.orEmpty()))
                                }
                            }
                        },
                        headlineContent = { Text(stringResource(R.string.tavern_export_json)) },
                        supportingContent = { Text(stringResource(R.string.tavern_export_json_desc)) },
                        leadingContent = { Icon(HugeIcons.File01, contentDescription = null) },
                    )
                    item(
                        onClick = {
                            when {
                                avatarUri != null -> {
                                    onDismiss()
                                    exportPng(context, exportScope, toaster, assistant, avatarUri)
                                }
                                avatarUrl != null && (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) -> {
                                    downloadingAvatar = true
                                    exportScope.launch {
                                        val downloaded = downloadUrlAvatarToCache(context, avatarUrl)
                                        downloadingAvatar = false
                                        onDismiss()
                                        if (downloaded != null) {
                                            exportPng(context, exportScope, toaster, assistant, downloaded)
                                        } else {
                                            toaster.show(context.getString(R.string.tavern_export_png_download_failed))
                                            pngImagePicker.launch("image/*")
                                        }
                                    }
                                }
                                else -> pngImagePicker.launch("image/*")
                            }
                        },
                        headlineContent = { Text(stringResource(R.string.tavern_export_png)) },
                        supportingContent = {
                            Text(
                                when {
                                    downloadingAvatar -> stringResource(R.string.tavern_export_png_downloading)
                                    avatarUri != null -> stringResource(R.string.tavern_export_png_desc_avatar)
                                    avatarUrl != null && (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) ->
                                        stringResource(R.string.tavern_export_png_desc_network)
                                    else -> stringResource(R.string.tavern_export_png_desc_pick)
                                }
                            )
                        },
                        leadingContent = {
                            if (downloadingAvatar) {
                                CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Icon(HugeIcons.Image02, contentDescription = null)
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.tavern_export_close)) }
        },
    )
}

private fun exportPng(
    context: Context,
    scope: CoroutineScope,
    toaster: ToasterState,
    assistant: Assistant,
    imageUri: Uri,
) {
    scope.launch {
        try {
            val json = CardExporter.buildV3CardJson(assistant)
            val pngBytes = CardExporter.embedCardToPng(imageUri, context, json)
                ?: error(context.getString(R.string.tavern_export_png_failed))
            val fileName = exportFileName(assistant.name, "png")
            saveToDownloads(context, fileName, "image/png", pngBytes)
            toaster.show(context.getString(R.string.tavern_export_png_success, fileName))
        } catch (e: Exception) {
            toaster.show(context.getString(R.string.tavern_export_failed, e.message.orEmpty()))
        }
    }
}

private fun exportFileName(name: String, extension: String): String {
    val safe = name.replace(Regex("[^A-Za-z0-9._\\-]+"), "_").ifBlank { "card" }
    return "PocketTavern-Lite_${safe}_${System.currentTimeMillis()}.$extension"
}

private suspend fun downloadUrlAvatarToCache(context: Context, url: String): Uri? {
    return withContext(Dispatchers.IO) {
        runCatching {
            val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "PocketTavern-Lite")
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                val bytes = connection.inputStream.use { it.readBytes() }
                if (bytes.isEmpty()) return@runCatching null
                val file = java.io.File(context.cacheDir, "avatar_download_${System.currentTimeMillis()}.img")
                file.writeBytes(bytes)
                Uri.fromFile(file)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}

private suspend fun saveToDownloads(context: Context, fileName: String, mimeType: String, data: ByteArray) {
    withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: error("unable to create download")
            context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                ?: error("unable to open download")
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            java.io.File(downloadsDir, fileName).writeBytes(data)
        }
    }
}
