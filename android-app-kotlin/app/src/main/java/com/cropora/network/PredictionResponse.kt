package com.cropora.network

import com.google.gson.annotations.SerializedName

data class PredictionResponse(
    @SerializedName("model_label")
    val modelLabel: String,

    @SerializedName("disease")
    val disease: String,

    @SerializedName("confidence")
    val confidence: Float,

    @SerializedName("uncertain")
    val uncertain: Boolean,

    @SerializedName("guidance_available")
    val guidanceAvailable: Boolean,

    @SerializedName("symptoms")
    val symptoms: String,

    @SerializedName("treatment")
    val treatment: String,

    @SerializedName("prevention")
    val prevention: String
)
