package com.bangkit.caviar

import com.google.gson.annotations.SerializedName


data class Location (

    @SerializedName("name"      ) var name      : String,
    @SerializedName("heading"   ) var heading   : Double,
    @SerializedName("latitude"  ) var latitude  : Double,
    @SerializedName("longitude" ) var longitude : Double,
    @SerializedName("distance"  ) var distance  : Double

)