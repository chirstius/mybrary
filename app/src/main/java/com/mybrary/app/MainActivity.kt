package com.mybrary.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavController
import com.mybrary.app.ui.MybraryApp
import com.mybrary.app.ui.theme.MybraryTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Set by MybraryApp once the NavHost is composed
    internal var navController: NavController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MybraryTheme {
                MybraryApp(
                    startIsbn = intent.extractShareIsbn(),
                    onNavControllerReady = { navController = it },
                )
            }
        }
    }

    /** Called when app is already running and another mybrary:// link is tapped. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val isbn = intent.extractShareIsbn()
        if (isbn.isNotBlank()) {
            navController?.navigate("add?isbn=$isbn")
        }
    }

    private fun Intent.extractShareIsbn(): String =
        data?.takeIf { it.scheme == "mybrary" && it.host == "book" }
            ?.getQueryParameter("isbn").orEmpty()
}
