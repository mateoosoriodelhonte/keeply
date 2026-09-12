package app.keeply.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

@Composable
private fun Placeholder() {
    MaterialTheme { Text("Keeply") }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Keeply") { Placeholder() }
}
