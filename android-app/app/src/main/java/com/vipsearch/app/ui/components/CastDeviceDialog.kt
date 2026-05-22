package com.vipsearch.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vipsearch.app.dlna.DlnaDevice
import com.vipsearch.app.ui.theme.AppColors

@Composable
fun CastDeviceDialog(
  devices: List<DlnaDevice>,
  isSearching: Boolean,
  onDeviceSelected: (DlnaDevice) -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = AppColors.CardSurface,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Default.Cast,
          contentDescription = null,
          tint = AppColors.ActionBlue,
          modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("选择投屏设备", color = AppColors.TextPrimary, fontSize = 18.sp)
      }
    },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        if (devices.isEmpty() && isSearching) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(120.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              CircularProgressIndicator(
                color = AppColors.ActionBlue,
                modifier = Modifier.size(32.dp),
                strokeWidth = 2.5.dp
              )
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                "正在搜索设备...",
                color = AppColors.TextMuted,
                fontSize = 14.sp
              )
            }
          }
        } else if (devices.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(120.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              "未找到可用设备\n请确保设备与手机在同一 WiFi",
              color = AppColors.TextMuted,
              fontSize = 14.sp,
              lineHeight = 22.sp
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier.heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            items(devices, key = { it.usn }) { device ->
              ListItem(
                headlineContent = {
                  Text(
                    device.friendlyName,
                    color = AppColors.TextPrimary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                },
                leadingContent = {
                  Icon(
                    Icons.Default.Tv,
                    contentDescription = null,
                    tint = AppColors.TextMuted
                  )
                },
                modifier = Modifier.clickable { onDeviceSelected(device) },
                colors = ListItemDefaults.colors(containerColor = AppColors.CardSurface)
              )
            }
          }

          if (isSearching) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically
            ) {
              CircularProgressIndicator(
                color = AppColors.ActionBlue,
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                "继续搜索中...",
                color = AppColors.TextMuted,
                fontSize = 13.sp
              )
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("取消", color = AppColors.TextMuted)
      }
    }
  )
}
