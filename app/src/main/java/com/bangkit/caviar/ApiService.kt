package com.bangkit.caviar

import com.bangkit.caviar.model.NearbyTrafficLightResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface ApiService
{
    @GET("crossing/nearest")
    fun getNearestCrossing(
        @Query("lat") latitude : Double,
        @Query("lng") longitude : Double,
        @Query("radius") radius : Double,
        @Header("Authorization") token : String
    ) : Call<NearbyTrafficLightResponse>

}