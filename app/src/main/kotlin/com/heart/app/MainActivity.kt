package com.heart.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.heart.app.ui.HeartTheme
import com.heart.app.ui.screen.HistoryScreen
import com.heart.app.ui.screen.HomeScreen
import com.heart.app.ui.screen.MeasureScreen
import com.heart.app.ui.screen.ResultScreen
import com.heart.core.model.MeasurementResult

sealed interface Screen {
    data object Home : Screen
    data object Measure : Screen
    data class Result(val result: MeasurementResult) : Screen
    data object History : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HeartTheme { AppRoot() } }
    }
}

@Composable
private fun AppRoot() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    when (val s = screen) {
        is Screen.Home -> HomeScreen(
            onMeasure = { screen = Screen.Measure },
            onHistory = { screen = Screen.History },
        )
        is Screen.Measure -> MeasureScreen(
            onResult = { screen = Screen.Result(it) },
            onCancel = { screen = Screen.Home },
        )
        is Screen.Result -> ResultScreen(
            result = s.result,
            onDone = { screen = Screen.Home },
            onRemeasure = { screen = Screen.Measure },
        )
        is Screen.History -> HistoryScreen(onBack = { screen = Screen.Home })
    }
}
