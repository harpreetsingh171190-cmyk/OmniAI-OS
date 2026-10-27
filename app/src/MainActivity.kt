package com.harpreet.omniai

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.harpreet.omniai.ui.AppViewModel
import com.harpreet.omniai.ui.OmniApp

class MainActivity : FragmentActivity() {
    private val vm: AppViewModel by viewModels()
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (vm.dark) darkColorScheme() else lightColorScheme()) {
                OmniApp(vm)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        // Re-lock (if a PIN is set) after more than 30 s in the background.
        if (stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > 30_000) vm.lockNow()
    }
}
