package com.cropora.network

import android.content.Context
import com.cropora.BuildConfig
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private val httpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    @Volatile
    private var cachedBaseUrl: String? = null

    @Volatile
    private var cachedApiService: ApiService? = null

    fun apiService(context: Context): ApiService {
        val baseUrl = ServerPreferences.getBaseUrl(context).trimEnd('/') + "/"

        cachedApiService?.let { existing ->
            if (baseUrl == cachedBaseUrl) {
                return existing
            }
        }

        val service = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)

        cachedBaseUrl = baseUrl
        cachedApiService = service
        return service
    }
}
