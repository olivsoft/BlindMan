package ch.olivsoft.android.blindman

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val fillFraction = 0.8f
private val stdPadding = 10.dp

@Composable
private fun TitleText(
    modifier: Modifier = Modifier,
    title: String = "Title",
    style: TextStyle = MaterialTheme.typography.headlineSmall.copy(
        fontSize = 20.sp
    )
) {
    Text(
        modifier = modifier,
        text = title,
        style = style
    )
}

@Composable
private fun DialogText(
    modifier: Modifier = Modifier,
    text: String = "Text",
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        modifier = modifier
            .verticalScroll(rememberScrollState()),
        text = text,
        color = color
    )
}

@Composable
private fun ListText(
    modifier: Modifier = Modifier,
    text: String = "Text",
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        modifier = modifier,
        text = text,
        style = style,
        color = color
    )
}

@Composable
private fun OKButton(
    onConfirm: () -> Unit,
    buttonText: String =
        LocalResources.current.getString(android.R.string.ok)
) {
    TextButton(onClick = onConfirm) { Text(buttonText) }
}

@Composable
private fun CancelButton(
    onDismiss: () -> Unit,
    buttonText: String =
        LocalResources.current.getString(android.R.string.cancel)
) {
    TextButton(onClick = onDismiss) { Text(buttonText) }
}

// Dialog templates. List dialogs get larger text style.
@Composable
private fun OKDialog(
    title: String = "Dialog Title",
    text: String = "Dialog Text",
    buttonText: String =
        LocalResources.current.getString(android.R.string.ok),
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onConfirm,
        title = { TitleText(title = title) },
        text = { DialogText(text = text) },
        confirmButton = {
            OKButton(
                onConfirm = onConfirm,
                buttonText = buttonText
            )
        }
    )
}

@Composable
private fun ListSelectionDialog(
    title: String = "List Dialog Title",
    buttonText: String =
        LocalResources.current.getString(android.R.string.cancel),
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit = {}
) {
    AlertDialog(
        title = { TitleText(title = title) },
        confirmButton = {
            CancelButton(
                onDismiss = onDismiss,
                buttonText = buttonText
            )
        },
        onDismissRequest = onDismiss,
        text = {
            LazyColumn {
                items(options) {
                    ListText(
                        text = it,
                        modifier = Modifier
                            .clickable { onOptionSelected(it) }
                            .fillMaxWidth(fillFraction)
                            .padding(stdPadding)
                    )
                }
            }
        }
    )
}

@Composable
private fun RadioSelectionDialog(
    title: String = "Radio Dialog Title",
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit = {}
) {
    var radioSelection by remember { mutableStateOf(selectedOption) }
    val coroutineScope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TitleText(title = title) },
        confirmButton = { CancelButton(onDismiss) },
        text = {
            LazyColumn {
                items(options) {
                    Row(
                        modifier = Modifier
                            .selectable(
                                selected = (it == radioSelection),
                                onClick = {
                                    radioSelection = it
                                    coroutineScope.launch {
                                        delay(200)
                                        onOptionSelected(it)
                                    }
                                }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (it == radioSelection),
                            onClick = null // Handled via Row
                        )
                        ListText(
                            text = it,
                            modifier = Modifier
                                .fillMaxWidth(fillFraction)
                                .padding(stdPadding)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun MultiChoiceDialog(
    title: String = "Multi Choice Dialog",
    buttonText: String = "Close",
    options: List<String>,
    selectedOptions: List<String> = options,
    onOptionChanged: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var selOptions by remember { mutableStateOf(selectedOptions) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TitleText(title = title) },
        confirmButton = {
            OKButton(
                buttonText = buttonText,
                onConfirm = onConfirm
            )
        },
        text = {
            LazyColumn {
                items(options) {
                    val isSelected = selOptions.contains(it)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(fillFraction)
                            .selectable(
                                selected = isSelected,
                                onClick = {
                                    selOptions =
                                        if (isSelected) selOptions - it
                                        else selOptions + it
                                    onOptionChanged(it, !isSelected)
                                }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null // Handled via Row
                        )
                        ListText(
                            text = it,
                            modifier = Modifier
                                .padding(stdPadding)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun ColorPickerDialog(
    title: String = "Color Picker Dialog",
    currentColor: Color = Color.Blue,
    onColorSelected: (color: Color) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TitleText(title = title) },
        text = {
            ColorPicker(
                modifier = Modifier
                    .aspectRatio(1f),
                currentColor = currentColor,
                onColorSelected = onColorSelected
            )
        },
        confirmButton = { CancelButton(onDismiss = onDismiss) }
    )
}

// Calling dialogs goes by setting this value
var activeDialogId by mutableIntStateOf(-1)

@Composable
fun BlindManDialogs() {
    val bmViewModel: BlindManViewModel = viewModel()
    val closeDialog: () -> Unit = { activeDialogId = -1 }

    when (activeDialogId) {
        // Somewhat redundant
        -1 -> {}

        R.id.level -> {
            val options = stringArrayResource(R.array.items_level).toList()
            RadioSelectionDialog(
                title = stringResource(R.string.title_level),
                options = options,
                selectedOption = options[bmViewModel.level - 1],
                onOptionSelected = {
                    closeDialog.invoke()
                    bmViewModel.level = options.indexOf(it) + 1
                },
                onDismiss = closeDialog,
            )
        }

        R.id.size -> {
            val options = stringArrayResource(R.array.items_size).toList()
            RadioSelectionDialog(
                title = stringResource(R.string.title_size),
                options = options,
                selectedOption = options[bmViewModel.size - 1],
                onOptionSelected = {
                    closeDialog.invoke()
                    bmViewModel.size = options.indexOf(it) + 1
                },
                onDismiss = closeDialog,
            )
        }

        R.id.lives -> {
            val inf = "∞"
            fun toOption(i: Int): String {
                return when (i) {
                    0 -> inf
                    in 1..9 -> " $i"
                    else -> i.toString()
                }
            }

            val options = BlindManViewModel.ALLOWED_LIVES.map { toOption(it) }
            RadioSelectionDialog(
                title = stringResource(R.string.title_lives),
                options = options,
                selectedOption = toOption(bmViewModel.lives),
                onOptionSelected = {
                    closeDialog.invoke()
                    bmViewModel.lives =
                        if (it == inf) 0
                        else it.trim().toInt()
                },
                onDismiss = closeDialog
            )
        }

        R.id.colors -> {
            val options = stringArrayResource(R.array.items_colors).toList()
            ListSelectionDialog(
                title = stringResource(R.string.title_colors),
                buttonText = stringResource(R.string.title_close),
                options = options,
                onOptionSelected = {
                    val pos = options.indexOf(it)
                    if (pos == ColoredPart.entries.size) {
                        closeDialog.invoke()
                        ColoredPart.resetAll()
                        bmViewModel.invalidateCounter++
                    } else {
                        activeDialogId = pos
                    }
                },
                onDismiss = closeDialog
            )
        }

        in 0..<ColoredPart.entries.size -> {
            val cp = ColoredPart.entries[activeDialogId]
            ColorPickerDialog(
                title = stringArrayResource(R.array.items_colors)[activeDialogId],
                currentColor = Color(cp.color),
                onColorSelected = {
                    closeDialog.invoke()
                    cp.color = it.toArgb()
                    bmViewModel.invalidateCounter++
                },
                onDismiss = closeDialog
            )
        }

        R.id.background -> {
            val options = stringArrayResource(R.array.items_background).toList()
            RadioSelectionDialog(
                title = stringResource(R.string.title_background),
                options = options,
                selectedOption = options[bmViewModel.background],
                onOptionSelected = {
                    closeDialog.invoke()
                    bmViewModel.background = options.indexOf(it)
                },
                onDismiss = closeDialog,
            )
        }

        R.id.sound -> {
            val options = stringArrayResource(R.array.items_effects).toList()
            val selectedOptions = mutableListOf<String>().apply {
                if (bmViewModel.isHapticFeedbackEnabled) add(options[0])
                if (bmViewModel.isSoundEffectsEnabled) add(options[1])
                if (bmViewModel.isMusicEnabled) add(options[2])
            }
            MultiChoiceDialog(
                title = stringResource(R.string.title_sound),
                buttonText = stringResource(R.string.title_close),
                options = options,
                selectedOptions = selectedOptions,
                onOptionChanged = { option, selected ->
                    when (option) {
                        options[0] -> bmViewModel.isHapticFeedbackEnabled = selected
                        options[1] -> bmViewModel.isSoundEffectsEnabled = selected
                        options[2] -> bmViewModel.isMusicEnabled = selected
                    }
                },
                onConfirm = closeDialog,
                onDismiss = closeDialog
            )
        }

        R.id.help -> OKDialog(
            title = stringResource(R.string.title_help),
            text = stringResource(R.string.text_help),
            onConfirm = closeDialog
        )

        R.id.about -> OKDialog(
            title = stringResource(R.string.title_about),
            text = stringResource(R.string.text_about) +
                    " ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})",
            onConfirm = closeDialog
        )

        else -> {}
    }
}
