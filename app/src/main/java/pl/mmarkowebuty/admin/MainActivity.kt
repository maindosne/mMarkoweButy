package pl.mmarkowebuty.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.mmarkowebuty.admin.ui.AdminApp
import pl.mmarkowebuty.admin.ui.MMarkoweButyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MMarkoweButyTheme {
                val vm: MainViewModel = viewModel()
                AdminApp(vm)
            }
        }
    }
}
