package com.zxy.kspdemo

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.zxy.kspdemo.ui.theme.KspDemoTheme

/**
 * Created by y on 2025/9/28-21:57
 *
 * @author y >_
 * .     _                                           ____  _       _ _
 * .    / \__      _____  ___  ___  _ __ ___   ___  | __ )(_)_ __ | | |_   _
 * .   / _ \ \ /\ / / _ \/ __|/ _ \| '_ ` _ \ / _ \ |  _ \| | '_ \| | | | | |
 * .  / ___ \ V  V /  __/\__ \ (_) | | | | | |  __/ | |_) | | | | | | | |_| |
 * . /_/   \_\_/\_/ \___||___/\___/|_| |_| |_|\___| |____/|_|_| |_|_|_|\__, |
 * .                                                                   |___/
 */

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KspDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RequestRepo(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun RequestRepo(modifier: Modifier = Modifier) {

    var sss by remember { mutableStateOf("测试") }

    retrofit {
        client(App.retrofitClient)
        service<GithubService> {
            getRepo("square", "retrofit") {
                onSuccess { repo ->
                    sss = repo.toString()
                    Log.i("->>", sss)
                }
                onFail { error ->
                    sss = error.message ?: "Error"
                    Log.e("->>", error.message ?: "Unknown Error")
                }
            }
        }
    }

    Text(
        text = sss,
        modifier = modifier
    )
}