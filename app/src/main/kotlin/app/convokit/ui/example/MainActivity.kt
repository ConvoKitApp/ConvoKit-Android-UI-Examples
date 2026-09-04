package app.convokit.ui.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat

internal enum class ExampleScreen(val label: String) {
    STANDARD("Standard"),
    BRANDED("Branded"),
    COMPACT("Compact"),
    LIVE("Live"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { ConvoKitAndroidUiExample() }
    }
}

@Composable
internal fun ConvoKitAndroidUiExample() {
    var screen by remember { mutableStateOf(ExampleScreen.STANDARD) }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        ExampleScreen.entries.forEach { item ->
                            NavigationBarItem(
                                selected = screen == item,
                                onClick = { screen = item },
                                icon = { Text(item.label.take(1)) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                },
            ) { contentPadding ->
                when (screen) {
                    ExampleScreen.STANDARD -> ShowcaseScreen(ShowcaseVariant.STANDARD, contentPadding)
                    ExampleScreen.BRANDED -> ShowcaseScreen(ShowcaseVariant.BRANDED, contentPadding)
                    ExampleScreen.COMPACT -> ShowcaseScreen(ShowcaseVariant.COMPACT, contentPadding)
                    ExampleScreen.LIVE -> LiveChatScreen(contentPadding)
                }
            }
        }
    }
}

