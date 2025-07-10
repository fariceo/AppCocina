package com.elrancho.cocina.compras.offline

data class GastoOffline(
    val id: Int,
    val producto: String,
    val peso: String,
    val total: Double,
    val fecha: String,
    val hora: String,
    val estado: Int,
    val sincronizado: Boolean
)