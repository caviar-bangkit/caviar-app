package com.bangkit.caviar

import com.bangkit.caviar.model.NearbyTrafficLightResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService
{
    @GET("crossing/nearest")
    fun getNearestCrossing(
        @Query("latitude") latitude : Double,
        @Query("longitude") longitude : Double,
        @Query("radius") radius : Double
    ) : Call<NearbyTrafficLightResponse>

}