package com.bangkit.caviar

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class NetworkConfig(token : String? = null) {
    // set interceptor
    private  val url = "https://caviar-api-qyyuck654a-et.a.run.app/api/"
    private fun getInterceptor() : OkHttpClient {
        val logging = HttpLoggingInterceptor()
        logging.level = HttpLoggingInterceptor.Level.BODY

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $this.token")
                    .build()
                chain.proceed(request)
            })
            .build()
        return  okHttpClient
    }
    private fun getRetrofit() : Retrofit {
        return Retrofit.Builder()
            .baseUrl(url)

            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    fun getService() = getRetrofit().create(ApiService::class.java)


}