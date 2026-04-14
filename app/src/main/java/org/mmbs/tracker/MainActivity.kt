package org.mmbs.tracker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.mmbs.tracker.databinding.ActivityMainBinding

/**
 * Scaffold entry point. Phase A will host a Navigation graph here with the
 * full screen set (S-01 … S-16); for now it just renders the boot screen so
 * we can validate the build pipeline end-to-end.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.versionLabel.text = getString(
            R.string.boot_version_fmt,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
        )
    }
}
