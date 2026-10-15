package com.app.tageditor.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.app.tageditor.data.CoverArt
import com.app.tageditor.data.ImageTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decodes embedded cover bytes off the main thread (down-sampled, so big images stay cheap). */
@Composable
fun rememberCoverBitmap(cover: CoverArt?, maxSide: Int = 900): State<ImageBitmap?> {
    return produceState<ImageBitmap?>(null, cover) {
        value = if (cover == null) {
            null
        } else {
            withContext(Dispatchers.Default) {
                ImageTools.decodeSampled(cover.bytes, maxSide)?.asImageBitmap()
            }
        }
    }
}
