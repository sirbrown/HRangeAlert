// MainActivity.kt
package com.hrrangealert


import com.hrrangealert.history.HistoryViewModel
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
import androidx.compose.material.icons.filled.History // Example for History
import androidx.compose.material.icons.filled.Home // Specifically for the Home icon
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

// Data class for navigation drawer items
data class NavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

class MainActivity : ComponentActivity() {
    private val bleViewModel: BleViewModel by viewModels()
    // You might want a ViewModel for measurement history later
    private val historyViewModel: HistoryViewModel by viewModels()

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allGranted = true
            permissions.entries.forEach {
                if (!it.value) allGranted = false
            }
            if (allGranted) {
                bleViewModel.init(this) // Initialize after permissions granted
                // You might want to automatically start a scan here or let the user click
                bleViewModel.startScan(this)
            } else {
                // Handle permission denial (e.g., show a message to the user)
                bleViewModel.updateConnectionStatus("Permissions denied. Cannot scan for BLE devices.")
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

                val navItems = listOf(
                    NavItem("Main", Icons.Filled.Home, AppDestinations.MAIN_SCREEN), // Add Home icon if needed
                    NavItem("Devices", Icons.AutoMirrored.Filled.BluetoothSearching, AppDestinations.DEVICES_SCREEN),
                    NavItem("History", Icons.Filled.History, AppDestinations.HISTORY_SCREEN)
                )

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Spacer(Modifier.height(12.dp))
                            navItems.forEach { item ->
                                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                                val currentRoute = currentBackStackEntry?.destination?.route
                                NavigationDrawerItem(
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label) },
                                    selected = currentRoute == item.route,
                                    onClick = {
                                        scope.launch {
                                            drawerState.close()
                                        }
                                        if (currentRoute != item.route) {
                                            navController.navigate(item.route) {
                                                // Pop up to the start destination of the graph to
                                                // avoid building up a large stack of destinations
                                                // on the back stack as users select items
                                                popUpTo(navController.graph.startDestinationId)
                                                launchSingleTop = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                            }
                        }
                    }
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            TopAppBar(
                                title = { Text("HR Range Alert") }, // Or dynamic title
                                navigationIcon = {
                                    IconButton(onClick = {
                                        scope.launch {
                                            if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                        }
                                    }) {
                                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                                    }
                                }
                            )
                        }
                    ) { innerPadding ->
                        AppNavigation(
                            navController = navController,
                            bleViewModel = bleViewModel,
                            historyViewModel = historyViewModel,
                            modifier = Modifier.padding(innerPadding),
                            onRequestPermissions = { requestBlePermissions() }
                        )
                    }
                }
            }
        }
        requestBlePermissions()
    }

    private fun requestBlePermissions() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION // Required for scanning pre-S
            )
        }

        val permissionsToRequest = requiredPermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // Permissions are already granted
            bleViewModel.init(this)
            // Optionally auto-start scan or wait for button
        }
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    bleViewModel: IBleViewModel,
    historyViewModel: HistoryViewModel,
    modifier: Modifier = Modifier,
    onRequestPermissions: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = AppDestinations.MAIN_SCREEN,
        modifier = modifier
    ) {
        composable(AppDestinations.MAIN_SCREEN) {
            // This will be the new main screen with the graph
            NewMainScreen(
                viewModel = bleViewModel,
                // Pass other necessary data, like loaded measurement
            )
        }
        composable(AppDestinations.DEVICES_SCREEN) {
            // This screen will contain the "Scan" and "Disconnect" buttons
            // and the device list logic, extracted from your original HeartRateBleScreen
            DevicesScreen(
                viewModel = bleViewModel,
                onRequestPermissions = onRequestPermissions
            )
        }
        composable(AppDestinations.HISTORY_SCREEN) {
            // This screen will list saved measurements
            HistoryScreen(
                viewModel = historyViewModel,
                onLoadMeasurementClicked = { measurementId ->
                    // Logic to load measurement and navigate back or update main screen
                    // For now, just navigate back to main
                    historyViewModel.loadMeasurementForDisplay(measurementId)
                    navController.navigate(AppDestinations.MAIN_SCREEN) {
                        popUpTo(navController.graph.startDestinationId) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}

