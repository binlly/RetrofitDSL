package com.zxy.kspdemo

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Created by y on 2025/10/3-20:33
 *
 * @author y >_
 * .     _                                           ____  _       _ _
 * .    / \__      _____  ___  ___  _ __ ___   ___  | __ )(_)_ __ | | |_   _
 * .   / _ \ \ /\ / / _ \/ __|/ _ \| '_ ` _ \ / _ \ |  _ \| | '_ \| | | | | |
 * .  / ___ \ V  V /  __/\__ \ (_) | | | | | |  __/ | |_) | | | | | | | |_| |
 * . /_/   \_\_/\_/ \___||___/\___/|_| |_| |_|\___| |____/|_|_| |_|_|_|\__, |
 * .                                                                   |___/
 */

@DslMarker
annotation class RetrofitDsl

@RetrofitDsl
class HttpClientConfig {
    internal var baseUrl: String = ""
    internal val interceptors = mutableListOf<Interceptor>()
    internal var connectTimeout: Long = 10_000
    internal var readTimeout: Long = 10_000
    internal var converterFactory: Converter.Factory = GsonConverterFactory.create()

    fun baseUrl(url: String) {
        this.baseUrl = url
    }

    fun timeouts(block: TimeoutConfig.() -> Unit) {
        TimeoutConfig().apply(block).also {
            connectTimeout = it.connect
            readTimeout = it.read
        }
    }

    fun addInterceptor(interceptor: Interceptor) {
        interceptors.add(interceptor)
    }

    fun converter(factory: Converter.Factory) {
        converterFactory = factory
    }
}

@RetrofitDsl
class RetrofitServiceDsl() {
    lateinit var retrofitService: BaseRetrofitService
    lateinit var retrofitClient: Retrofit

    fun client(client: Retrofit) {
        if (!::retrofitClient.isInitialized) {
            retrofitClient = client
        } else {
            throw Exception("retrofitClient has initialized!")
        }
    }

    inline fun <reified T : BaseRetrofitService> service(block: RetrofitServiceDsl.() -> Unit): T {
        val service: T = retrofitClient.createService()
        this.retrofitService = service
        this.block()
        return service
    }
}

// 全局 loading 控制（可替换为接口）
var showLoading: (Boolean) -> Unit = {
    // TODO
}

class TimeoutConfig {
    var connect: Long = 10_000
    var read: Long = 10_000
}

fun retrofitClient(block: HttpClientConfig.() -> Unit): Retrofit {
    val config = HttpClientConfig().apply(block)
    return Retrofit.Builder()
        .baseUrl(config.baseUrl)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(config.connectTimeout, TimeUnit.MILLISECONDS)
                .readTimeout(config.readTimeout, TimeUnit.MILLISECONDS)
                .apply {
                    config.interceptors.forEach { addInterceptor(it) }
                }
                .build()
        )
        .addConverterFactory(config.converterFactory)
        .build()
}

fun retrofit(block: RetrofitServiceDsl.() -> Unit): RetrofitServiceDsl {
    val retrofitService = RetrofitServiceDsl().apply(block)
    return retrofitService
}

inline fun <reified T> Retrofit.createService(): T = create(T::class.java)

class DslResult<T> {
    var onSuccessBlock: ((T) -> Unit)? = null
    var onFailBlock: ((Throwable) -> Unit)? = null

    fun onSuccess(block: (T) -> Unit) {
        onSuccessBlock = block
    }

    fun onFail(block: (Throwable) -> Unit) {
        onFailBlock = block
    }
}