package com.zxy.kspdemo

import android.app.Application
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

/**
 * Created by y on 2025/10/3-20:34
 *
 * @author y >_
 * .     _                                           ____  _       _ _
 * .    / \__      _____  ___  ___  _ __ ___   ___  | __ )(_)_ __ | | |_   _
 * .   / _ \ \ /\ / / _ \/ __|/ _ \| '_ ` _ \ / _ \ |  _ \| | '_ \| | | | | |
 * .  / ___ \ V  V /  __/\__ \ (_) | | | | | |  __/ | |_) | | | | | | | |_| |
 * . /_/   \_\_/\_/ \___||___/\___/|_| |_| |_|\___| |____/|_|_| |_|_|_|\__, |
 * .                                                                   |___/
 */

class App : Application() {

    companion object {
        lateinit var retrofitClient: Retrofit
    }


    override fun onCreate() {
        super.onCreate()
        retrofitClient = retrofitClient {
            baseUrl("https://api.github.com")
            timeouts {
                connect = 5_000
                read = 10_000
            }
            addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
        }
    }
}