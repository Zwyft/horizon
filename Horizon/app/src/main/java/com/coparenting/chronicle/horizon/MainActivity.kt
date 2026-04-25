package com.coparenting.chronicle.horizon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.coparenting.chronicle.horizon.ui.navigation.AppNavigation
import com.coparenting.chronicle.horizon.ui.theme.HorizonTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HorizonTheme {
                AppNavigation()
            }
        }
    }
}
