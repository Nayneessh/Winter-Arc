package com.winterarc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.winterarc.app.ui.WinterArcApp
import com.winterarc.app.ui.theme.WinterArcColors
import com.winterarc.app.ui.theme.WinterArcTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge is applied before setContent so the first frame already draws
        // behind the system bars rather than snapping into place.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as WinterArcApplication).container

        setContent {
            WinterArcTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(WinterArcColors.NightDeep),
                    color = WinterArcColors.NightDeep,
                ) {
                    WinterArcApp(container)
                }
            }
        }
    }
}
