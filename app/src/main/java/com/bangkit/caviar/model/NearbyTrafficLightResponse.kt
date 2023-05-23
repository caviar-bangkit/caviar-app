package com.bangkit.caviar.model

import com.bangkit.caviar.Location
import com.google.gson.annotations.SerializedName

data class NearbyTrafficLightResponse
(
  @SerializedName("status") var status : Boolean? = null,
  @SerializedName("message") var message : String? = null,
  @SerializedName("data") var data : Location? = null
)