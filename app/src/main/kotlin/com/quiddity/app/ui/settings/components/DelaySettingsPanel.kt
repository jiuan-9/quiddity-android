package com.quiddity.app.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quiddity.app.ui.components.QuiddityToggleSwitch
import com.quiddity.app.util.QuiddityConstants
// 当前规则：发送延迟独立设置（打字机延迟已移除——流式期间保持 streaming 假状态
// 会让光标在文本显示完后继续闪烁，造成界面闪动，加载动画改为随流式自然结束）。
@Composable
fun DelaySettingsPanel(
    sendDelayEnabled: Boolean,
    sendDelaySeconds: Int,
    onSendDelayEnabledChange: (Boolean) -> Unit,
    onSendDelaySecondsChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
