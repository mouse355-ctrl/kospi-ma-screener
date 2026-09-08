package com.e2s.kospiscreener

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.e2s.kospiscreener.ui.DetailScreen
import com.e2s.kospiscreener.ui.ListScreen
import com.e2s.kospiscreener.ui.ScreenerViewModel
import com.e2s.kospiscreener.ui.theme.KospiScreenerTheme

class MainActivity : ComponentActivity() {
    private val vm: ScreenerViewModel by viewModels()

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            KospiScreenerTheme {
                AppNav(vm)
            }
        }
    }
}

@Composable
private fun AppNav(vm: ScreenerViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            val state by vm.list.collectAsState()
            ListScreen(
                state = state,
                onRefresh = vm::refresh,
                onFilters = vm::updateFilters,
                onSaveBaseUrl = vm::setBaseUrl,
                onOpen = { code -> nav.navigate("detail/$code") },
            )
        }
        composable(
            route = "detail/{code}",
            arguments = listOf(navArgument("code") { type = NavType.StringType }),
        ) { entry ->
            val code = entry.arguments?.getString("code").orEmpty()
            LaunchedEffect(code) { vm.openDetail(code) }
            val state by vm.detail.collectAsState()
            DetailScreen(state = state, onBack = { nav.popBackStack() })
        }
    }
}
