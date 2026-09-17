package pl.mmarkowebuty.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.mmarkowebuty.admin.ui.AdminApp
import pl.mmarkowebuty.admin.ui.BarcodeWarehouseScreen
import pl.mmarkowebuty.admin.ui.BatchListingScreen
import pl.mmarkowebuty.admin.ui.MMarkoweButyTheme
import pl.mmarkowebuty.admin.ui.MmOrange

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MMarkoweButyTheme {
                val vm: MainViewModel = viewModel()
                var batchMode by rememberSaveable { mutableStateOf(false) }
                var warehouseScannerMode by rememberSaveable { mutableStateOf(false) }

                Box(Modifier.fillMaxSize()) {
                    when {
                        warehouseScannerMode && vm.loggedIn -> BarcodeWarehouseScreen(vm = vm, onBack = { warehouseScannerMode = false })
                        batchMode && vm.loggedIn -> BatchListingScreen(vm = vm, onBack = { batchMode = false })
                        else -> AdminApp(vm)
                    }

                    if (vm.loggedIn && !batchMode && !warehouseScannerMode) {
                        Column(
                            modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            ExtendedFloatingActionButton(
                                onClick = { warehouseScannerMode = true },
                                containerColor = MmOrange,
                                icon = { Icon(Icons.Outlined.QrCodeScanner, contentDescription = null) },
                                text = { Text("MAGAZYN") }
                            )
                            ExtendedFloatingActionButton(
                                onClick = { batchMode = true },
                                containerColor = MmOrange,
                                icon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                                text = { Text("AUTO") }
                            )
                        }
                    }
                }
            }
        }
    }
}
