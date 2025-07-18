package com.byteflow.www.data.models

data class Plan(
    val id: String,
    val name: String,
    val price: String,
    val originalPrice: String?,
    val data: String,
    val features: List<String>,
    val isPopular: Boolean,
    val isCurrent: Boolean
)