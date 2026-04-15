package org.mmbs.tracker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment

/**
 * Single activity — hosts the nav graph from R.navigation.nav_graph. The
 * start destination is dynamic: if the user is signed in and a spreadsheet is
 * configured, we jump straight to the Home screen; otherwise we land on Sign In.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host) as NavHostFragment
        val graph = navHost.navController.navInflater.inflate(R.navigation.nav_graph)

        graph.setStartDestination(chooseStartDestination())
        navHost.navController.graph = graph
    }

    private fun chooseStartDestination(): Int {
        val prefs = ServiceLocator.prefs
        val hasEmail = !prefs.userEmail.isNullOrBlank()
        val hasSheet = !prefs.spreadsheetId.isNullOrBlank()
        return when {
            !hasEmail -> R.id.signInFragment
            !hasSheet -> R.id.setupFragment
            else -> R.id.homeFragment
        }
    }
}
