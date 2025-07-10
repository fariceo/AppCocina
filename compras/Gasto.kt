package com.elrancho.cocina.compras

data class Gasto(
    val id: Int,
    val producto: String,
    var precio: Double,  // ← ahora es mutable
    var estado: Int
)