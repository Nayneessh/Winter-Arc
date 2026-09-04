package com.winterarc.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.winterarc.app.ui.RootScreen
import com.winterarc.app.ui.theme.WinterArcTheme

class MainActivity : ComponentActivity() {

    private val repository: Repository
        get() = (application as WinterArcApplication).repository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw behind the system bars. The gradient ground is the point of the design, and it
        // should run to the edges of the screen rather than stopping at a status bar.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            WinterArcTheme {
                RootScreen(
                    repository = repository,
                    onKeepScreenOn = { keep ->
                        if (keep) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    },
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // A debounced save may still be pending; the process can be killed at any point after
        // this, so the current state is committed synchronously here.
        repository.flush()
    }
}
