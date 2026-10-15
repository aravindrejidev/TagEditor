package com.app.tageditor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.tageditor.data.FileNameTemplate
import com.app.tageditor.ui.components.ChipFlow
import com.app.tageditor.ui.components.NeuButton
import com.app.tageditor.ui.components.NeuCard
import com.app.tageditor.ui.components.NeuChip
import com.app.tageditor.ui.components.NeuTextField
import com.app.tageditor.ui.components.NeuTopBar
import com.app.tageditor.ui.components.SectionLabel
import com.app.tageditor.ui.theme.NeuAccent
import com.app.tageditor.ui.theme.NeuError
import com.app.tageditor.ui.theme.NeuIcons
import com.app.tageditor.ui.theme.NeuTextHint
import com.app.tageditor.ui.theme.NeuTextPrimary
import com.app.tageditor.ui.theme.NeuTextSecondary
import com.app.tageditor.viewmodel.RenameDraft

private const val PREVIEW_LIMIT = 8

/** Tag-to-filename tool: build a template, check the preview, rename. */
@Composable
fun RenameScreen(
    draft: RenameDraft,
    onBack: () -> Unit,
    onTemplateChange: (String) -> Unit,
    onApply: () -> Unit
) {
    val changing = draft.rows.count { it.newName != null && it.newName != it.item.name }

    Column(modifier = Modifier.fillMaxSize()) {
        NeuTopBar(
            title = "Rename from tags",
            subtitle = "${draft.targets.size} file(s)",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            NeuTextField(
                value = draft.template,
                onValueChange = onTemplateChange,
                label = "File name template",
                hint = FileNameTemplate.DEFAULT,
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Done
            )

            NeuCard(modifier = Modifier.fillMaxWidth()) {
                SectionLabel("Tap to add a placeholder")
                Spacer(Modifier.height(12.dp))
                ChipFlow {
                    for (token in FileNameTemplate.placeholders) {
                        NeuChip(text = token, onClick = { onTemplateChange(draft.template + token) })
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeuChip(text = "Default", onClick = { onTemplateChange(FileNameTemplate.DEFAULT) })
                    NeuChip(text = "Clear", onClick = { onTemplateChange("") })
                }
            }

            NeuCard(modifier = Modifier.fillMaxWidth()) {
                SectionLabel("Preview")
                Spacer(Modifier.height(10.dp))
                if (draft.loading) {
                    Text(
                        text = "Reading tags ...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeuTextSecondary
                    )
                } else if (draft.rows.isEmpty()) {
                    Text(
                        text = "Nothing to preview",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeuTextSecondary
                    )
                } else {
                    for (row in draft.rows.take(PREVIEW_LIMIT)) {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                text = row.newName ?: (row.note ?: "Cannot rename"),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (row.newName != null) NeuAccent else NeuError,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = row.item.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = NeuTextHint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (row.newName != null && row.note != null) {
                                Text(
                                    text = row.note,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeuTextSecondary
                                )
                            }
                        }
                    }
                    if (draft.rows.size > PREVIEW_LIMIT) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "and ${draft.rows.size - PREVIEW_LIMIT} more",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeuTextSecondary
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "$changing of ${draft.rows.size} files will be renamed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeuTextPrimary
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        NeuButton(
            text = if (changing == 1) "Rename 1 file" else "Rename $changing files",
            onClick = onApply,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            icon = NeuIcons.Check,
            primary = true,
            enabled = changing > 0 && !draft.loading
        )
    }
}
