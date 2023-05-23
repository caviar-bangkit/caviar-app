package com.bangkit.caviar

import com.google.gson.annotations.SerializedName


data class Location (

    @SerializedName("name"      ) var name      : String? = null,
    @SerializedName("heading"   ) var heading   : Double? = null,
    @SerializedName("latitude"  ) var latitude  : Double? = null,
    @SerializedName("longitude" ) var longitude : Double? = null,
    @SerializedName("distance"  ) var distance  : Double? = null

)