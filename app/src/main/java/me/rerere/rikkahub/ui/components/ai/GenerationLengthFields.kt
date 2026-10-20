package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant

/** Custom numbers have no artificial preset cap; the remote API still enforces its model limits. */
@Composable
fun GenerationLengthFields(assistant: Assistant, onUpdate: (Assistant) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TokenLimitField(
            value = assistant.contextTokenLimit,
            label = stringResource(R.string.generation_context_tokens),
            hint = stringResource(R.string.generation_context_hint),
            onChange = { onUpdate(assistant.copy(contextTokenLimit = it)) },
        )
        TokenLimitField(
            value = assistant.maxTokens,
            label = stringResource(R.string.generation_reply_tokens),
            hint = stringResource(R.string.generation_reply_hint),
            onChange = { onUpdate(assistant.copy(maxTokens = it)) },
        )
        Text(stringResource(R.string.generation_unlocked_hint))
    }
}

@Composable
private fun TokenLimitField(value: Int?, label: String, hint: String, onChange: (Int?) -> Unit) {
    var input by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    OutlinedTextField(
        value = input,
        onValueChange = { text ->
            if (text.all(Char::isDigit) && (text.isEmpty() || text.toIntOrNull() != null)) {
                input = text
                if (text.isEmpty()) onChange(null)
                else text.toIntOrNull()?.takeIf { it > 0 }?.let(onChange)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = { Text(hint) },
        isError = input.isNotEmpty() && input.toIntOrNull() == 0,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
