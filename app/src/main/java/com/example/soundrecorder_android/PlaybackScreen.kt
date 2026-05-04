package com.example.soundrecorder_android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PlaybackScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(3) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(2) { col ->
                        Button(
                            onClick = {},
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5C5C)),
                            modifier = Modifier.size(width = 140.dp, height = 64.dp),
                        ) {
                            Text(
                                text = "Button${row * 2 + col + 1}",
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}
