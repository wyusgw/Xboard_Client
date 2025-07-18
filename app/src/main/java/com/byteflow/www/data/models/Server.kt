package com.byteflow.www.data.models

data class Server(
    val id: String,
    val name: String,
    val location: String,
    val ping: String,
    val isSelected: Boolean,
    val load: Int // Server load percentage (0-100)
)