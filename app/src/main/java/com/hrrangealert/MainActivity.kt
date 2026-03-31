package com.hrrangealert

import com.hrrangealert.history.HistoryViewModel
import com.hrrangealert.history.HistoryViewModelFactory
import com.hrrangealert.data.AppDatabase
import com.hrrangealert.ui.main.NewMainScreen
import com.hrrangealert.ui.theme.HRRangeAlertTheme
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch

data class NavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

class MainActivity : ComponentActivity() {
    private val bleViewModel: BleViewModel by viewModels()
    private var permissionRequestTriggeredFromUI = false

    private val historyViewModel: HistoryViewModel by viewModels {
        HistoryViewModelFactory(AppDatabase.getDatabase(this).measurementDao())
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allGranted = true
            permissions.entries.forEach {
                if (!it.value) allGranted = false
            }
            if (allGranted) {
                bleViewModel.init(this)
                bleViewModel.startScan(this)
            } else {
                if (permissionRequestTriggeredFromUI) {
                    bleViewModel.updateConnectionStatus("Permissions denied. Cannot scan for BLE devices.")
                }
                permissionRequestTriggeredFromUI = false // Reset the flag
            }
        }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HRRangeAlertTheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                val items = listOf(
                    NavItem("Home", Icons.Default.Home, AppDestinations.MAIN_SCREEN),
                    NavItem("Devices", Icons.AutoMirrored.Filled.BluetoothSearching, AppDestinations.DEVICES_SCREEN),
                    NavItem("History", Icons.Default.History, AppDestinations.HISTORY_SCREEN)
                )

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Spacer(Modifier.height(12.dp))
                            items.forEach { item ->
                                NavigationDrawerItem(
                                    icon = { Icon(item.icon, contentDescription = null) },
                                    label = { Text(item.label) },
                                    selected = currentRoute == item.route,
                                    onClick = {
                                        scope.launch {
                                            drawerState.close()
                                        }
                                        navController.navigate(item.route) {
                                            popUpTo(AppDestinations.MAIN_SCREEN) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                            }
                        }
                    }
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("HR Range Alert") },
                                navigationIcon = {
                                    IconButton(onClick = {
                                        scope.launch {
                                            drawerState.open()
                                        }
                                    }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                                    }
                                }
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        NavGraph(
                            navController = navController,
                            bleViewModel = bleViewModel,
                            historyViewModel = historyViewModel,
                            onRequestPermissions = { checkAndRequestPermissions(fromUI = true) },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions(fromUI: Boolean = false) {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
             permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsToRequest = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionRequestTriggeredFromUI = fromUI
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            bleViewModel.init(this)
            bleViewModel.startScan(this)
        }
    }
}

@Composable
fun NavGraph(
    navController: NavHostController,
    bleViewModel: BleViewModel,
    historyViewModel: HistoryViewModel,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = AppDestinations.MAIN_SCREEN,
        modifier = modifier
    ) {
        composable(AppDestinations.MAIN_SCREEN) {
            val selectedId by historyViewModel.selectedMeasurementId.collectAsState()
            val measurements by historyViewModel.measurements.collectAsState()
            val loadedMeasurement = measurements.find { it.id == selectedId }
            
            NewMainScreen(
                viewModel = bleViewModel,
                loadedMeasurement = loadedMeasurement
            )
        }
        composable(AppDestinations.DEVICES_SCREEN) {
            DevicesScreen(
                viewModel = bleViewModel,
                onRequestPermissions = onRequestPermissions
            )
        }
        composable(AppDestinations.HISTORY_SCREEN) {
            HistoryScreen(
                viewModel = historyViewModel,
                onLoadMeasurementClicked = { id ->
                    historyViewModel.loadMeasurementForDisplay(id)
                    navController.navigate(AppDestinations.MAIN_SCREEN) {
                        popUpTo(AppDestinations.MAIN_SCREEN) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
        //TODO: Add Settings screen to configure age, weight, gender and manage saved devices
        //TODO: Connect to saved device on startup and start data collection. Start to save data
        // only if button "Start measurement" is pressed
    }
}
