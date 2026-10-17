package com.app.tageditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.tageditor.data.Genres
import com.app.tageditor.ui.theme.NeuAccent
import com.app.tageditor.ui.theme.NeuShapes
import com.app.tageditor.ui.theme.NeuSurface
import com.app.tageditor.ui.theme.NeuTextPrimary
import com.app.tageditor.ui.theme.NeuTextSecondary
import com.app.tageditor.viewmodel.BusyState

/** Blocks the screen while files are being processed and shows progress. */
@Composable
fun BusyOverlay(busy: BusyState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC14161A))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        NeuCard(
            modifier = Modifier
                .padding(32.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = busy.message,
                style = MaterialTheme.typography.titleMedium,
                color = NeuTextPrimary,
                maxLines = 2
            )
            Spacer(Modifier.height(16.dp))
            NeuProgressBar(progress = busy.progress)
            if (busy.total > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "${busy.done} of ${busy.total}",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeuTextSecondary
                )
            }
        }
    }
}

/** Dialog styled like the rest of the app. */
@Composable
fun NeuDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = null,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NeuSurface,
        shape = NeuShapes.Large,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = NeuTextPrimary
            )
        },
        text = content,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmLabel, color = NeuAccent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            if (dismissLabel != null) {
                TextButton(onClick = onDismiss) {
                    Text(text = dismissLabel, color = NeuTextSecondary)
                }
            }
        }
    )
}

/** List of common genres; tapping one fills the genre field. */
@Composable
fun GenrePickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    NeuDialog(
        title = "Choose genre",
        confirmLabel = "Close",
        onConfirm = onDismiss,
        onDismiss = onDismiss
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
            items(Genres.all) { genre ->
                Text(
                    text = genre,
                    style = MaterialTheme.typography.bodyLarge,
                    color = NeuTextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(genre) }
                        .padding(horizontal = 6.dp, vertical = 14.dp)
                )
            }
        }
    }
}
