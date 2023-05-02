package com.queatz.ailaai.ui.dialogs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.queatz.ailaai.R
import com.queatz.ailaai.ui.theme.PaddingDefault

@Composable
fun ProcessingVideoDialog(
    onDismissRequest: () -> Unit,
    onCancelRequest: () -> Unit,
    progress: Float
) {
    AlertDialog(
        {
            // Non-dismissable
        },
        title = {
            Text(stringResource(R.string.processing_video))
        },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val animatedProgress: Float by animateFloatAsState(progress)
                CircularProgressIndicator(
                    strokeWidth = ProgressIndicatorDefaults.CircularStrokeWidth / 2,
                    modifier = Modifier.size(24.dp),
                    progress = animatedProgress
                )
                Text(stringResource(R.string.please_wait), modifier = Modifier.padding(start = PaddingDefault * 2)) // todo extract string
            }
        },
        confirmButton = {
            TextButton(
                {
                    onCancelRequest()
                    onDismissRequest()
                }
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
