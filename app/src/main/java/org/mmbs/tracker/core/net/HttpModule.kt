package org.mmbs.tracker.core.net

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Single OkHttp instance for the whole app. */
object HttpModule {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
