package sharma.ai.chat.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import sharma.ai.chat.ui.ChatScreen
import sharma.ai.chat.ui.SplashScreen
import sharma.ai.chat.ui.theme.AI_chat_bot_Aman_SharmaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AI_chat_bot_Aman_SharmaTheme {
                var showSplash by rememberSaveable { mutableStateOf(true) }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (showSplash) {
                        Color(0xFF1C122C)
                    } else {
                        MaterialTheme.colorScheme.background
                    },
                ) {
                    if (showSplash) {
                        SplashScreen(onFinished = { showSplash = false })
                    } else {
                        ChatScreen()
                    }
                }
            }
        }
    }
}