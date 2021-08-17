package io.appwrite.realtimestarter

import com.google.gson.annotations.SerializedName

data class Product(
    val name: String,
    val sku: String,
    val price: Double,
    @SerializedName("image_url") val imageUrl: String
)