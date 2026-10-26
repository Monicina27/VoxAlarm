package com.voxalarm.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.voxalarm.container
import com.voxalarm.data.AppSettings
import com.voxalarm.ui.components.VoiceSetupDialog
import com.voxalarm.ui.components.neonGlow
import com.voxalarm.ui.edit.EditAlarmScreen
import com.voxalarm.ui.history.HistoryScreen
import com.voxalarm.ui.home.HomeScreen
import com.voxalarm.ui.settings.SettingsScreen
import com.voxalarm.ui.theme.VoxBlack
import com.voxalarm.ui.theme.VoxCard
import com.voxalarm.ui.theme.VoxOutline
import com.voxalarm.ui.theme.VoxTextFaint
import com.voxalarm.ui.theme.VoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsFlow = container.settings.settings
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
            VoxTheme(accentIndex = settings.accentIndex) {
                VoxApp()
            }
        }
    }
}

private object Routes {
    const val ALARMS = "alarms"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val EDIT = "edit/{id}"
    fun edit(id: Long) = "edit/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val color: Color)

private val tabs = listOf(
    Tab(Routes.ALARMS, "Alarms", Icons.Filled.Alarm, Color(0xFF00F5D4)),
    Tab(Routes.HISTORY, "History", Icons.Filled.History, Color(0xFFB967FF)),
    Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings, Color(0xFFFF8A00)),
)

@Composable
fun VoxApp() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route in tabs.map { it.route }

    // First-launch prompt to install Google's speech engine when it's missing.
    val context = LocalContext.current
    val previewTts = remember { context.container.previewTts }
    val ttsState by previewTts.state.collectAsStateWithLifecycle()
    VoiceSetupDialog(ttsState, onRefresh = previewTts::refresh)

    Scaffold(
        containerColor = VoxBlack,
        bottomBar = {
            AnimatedVisibility(
                visible = showBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) { BottomBar(route, nav) }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.ALARMS,
            modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
            enterTransition = { fadeIn(tween(250)) },
            exitTransition = { fadeOut(tween(200)) },
        ) {
            composable(Routes.ALARMS) {
                HomeScreen(
                    onAddAlarm = { nav.navigate(Routes.edit(-1)) },
                    onEditAlarm = { nav.navigate(Routes.edit(it)) },
                )
            }
            composable(Routes.HISTORY) { HistoryScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(
                Routes.EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
                enterTransition = { slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(300)) },
                popExitTransition = { slideOutHorizontally(tween(250)) { it / 3 } + fadeOut(tween(250)) },
            ) {
                EditAlarmScreen(onDone = { nav.popBackStack() })
            }
        }
    }
}

@Composable
private fun BottomBar(route: String?, nav: NavHostController) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, VoxBlack)))
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(VoxCard)
                .border(1.dp, VoxOutline, RoundedCornerShape(28.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tabs.forEach { tab ->
                val selected = route == tab.route
                val tint by animateColorAsState(if (selected) tab.color else VoxTextFaint, label = "tab")
                val bg by animateColorAsState(if (selected) tab.color.copy(alpha = 0.14f) else Color.Transparent, label = "tabBg")
                Column(
                    Modifier
                        .weight(1f)
                        .neonGlow(tab.color, radius = 14.dp, corner = 22.dp, alpha = if (selected) 0.35f else 0f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(bg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) {
                            if (!selected) nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(tab.icon, tab.label, tint = tint, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(tab.label, color = tint, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
