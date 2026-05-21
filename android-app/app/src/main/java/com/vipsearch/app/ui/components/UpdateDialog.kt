package com.vipsearch.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.vipsearch.app.data.remote.AppUpdate
import com.vipsearch.app.ui.theme.AppColors

@Composable
fun UpdateDialog(
  update: AppUpdate,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = AppColors.CardSurface,
    titleContentColor = AppColors.TextPrimary,
    textContentColor = AppColors.TextPrimary,
    title = { Text("发现新版本 v${update.versionName}") },
    text = {
      Text(
        if (update.releaseNotes.isNotBlank()) update.releaseNotes
        else "有新版本可用，建议更新以获得最新功能和修复。"
      )
    },
    confirmButton = {
      TextButton(
        onClick = {
          val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl))
          context.startActivity(intent)
          onDismiss()
        },
        colors = ButtonDefaults.textButtonColors(contentColor = AppColors.SkyBlue)
      ) {
        Text("立即更新")
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        colors = ButtonDefaults.textButtonColors(contentColor = AppColors.TextMuted)
      ) {
        Text("稍后再说")
      }
    }
  )
}
