package com.elrancho.cocina.compras

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.elrancho.cocina.MainActivity
import com.elrancho.cocina.R
import com.elrancho.cocina.compras.offline.GastoOffline
import com.elrancho.cocina.compras.offline.GastoRepository
import org.json.JSONException
import org.json.JSONObject
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GastosActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var gastosAdapter: GastosAdapter
    private lateinit var formulario: LinearLayout
    private lateinit var inputProducto: EditText
    private lateinit var inputCantidad: EditText
    private lateinit var inputPrecio: EditText
    private lateinit var txtGastosEsperados: TextView
    private lateinit var txtGastosCategoria: TextView

    private lateinit var gastoRepo: GastoRepository
    private val listaGastos = mutableListOf<Gasto>()
    private var currentFiltro = "diario"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gastos)

        recyclerView = findViewById(R.id.recyclerGastos)
        formulario = findViewById(R.id.formularioRegistro)
        inputProducto = findViewById(R.id.inputProducto)
        inputCantidad = findViewById(R.id.inputCantidad)
        inputPrecio = findViewById(R.id.inputPrecio)
        txtGastosEsperados = findViewById(R.id.txtGastosEsperados)
        txtGastosCategoria = findViewById(R.id.txtGastosCategoria)

        findViewById<View>(R.id.logoImageView).setOnClickListener {
            startActivity(android.content.Intent(this, MainActivity::class.java))
        }

        gastoRepo = GastoRepository(this)
        recyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.botonDiario).setOnClickListener {
            currentFiltro = "diario"
            formulario.visibility = View.VISIBLE
            cargarGastosConFallback(currentFiltro)
        }
        findViewById<View>(R.id.botonSemanal).setOnClickListener {
            currentFiltro = "semanal"
            formulario.visibility = View.GONE
            cargarGastosConFallback(currentFiltro)
        }
        findViewById<View>(R.id.botonMensual).setOnClickListener {
            currentFiltro = "mensual"
            formulario.visibility = View.GONE
            cargarGastosConFallback(currentFiltro)
        }

        findViewById<View>(R.id.btnRegistrarCompra).setOnClickListener {
            registrarCompra()
        }

        formulario.visibility = View.VISIBLE
        cargarGastosConFallback(currentFiltro)

        // Sincronizar en segundo plano al iniciar
        sincronizarDatosOffline()
    }

    override fun onResume() {
        super.onResume()
        sincronizarDatosOffline()
        cargarGastosConFallback(currentFiltro)
    }

    private fun cargarGastosConFallback(filtro: String) {
        if (hayConexionInternet()) {
            cargarGastosDesdeServidor(filtro)
        } else {
            val gastosOffline = gastoRepo.obtenerGastosOffline(filtro)
            listaGastos.clear()
            listaGastos.addAll(gastosOffline.map {
                Gasto(it.id, it.producto, it.total, it.estado)
            })

            configurarAdapterYVista(listaGastos, offlineMode = true, filtro = filtro)
        }
    }

    private fun cargarGastosDesdeServidor(filtro: String) {
        val url = "https://elpollovolantuso.com/asi_sistema/android/consulta_gastos.php?filtro=$filtro"

        val rq = Volley.newRequestQueue(this)
        val req = object : StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val jsonArray = org.json.JSONArray(response)
                    val lista = mutableListOf<Gasto>()
                    val offlineList = mutableListOf<GastoOffline>()

                    var totalEsperados = 0.0
                    var totalCategoria = 0.0

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val id = obj.getInt("id")
                        val producto = obj.getString("producto")
                        val total = obj.getDouble("total")
                        val estado = obj.getInt("estado")
                        val fecha = obj.optString("fecha", "")
                        val hora = obj.optString("hora", "")

                        if (estado == 0) totalEsperados += total else totalCategoria += total

                        lista.add(Gasto(id, producto, total, estado))

                        Log.d("SYNC_SERVER", "ID: $id | Producto: $producto | Total: $total | Estado: $estado | Fecha: $fecha | Hora: $hora")
                        offlineList.add(
                            GastoOffline(
                                id = id,
                                producto = producto,
                                peso = "",
                                total = total,
                                fecha = fecha,
                                hora = hora,
                                estado = estado,
                                sincronizado = true
                            )
                        )
                    }

                    gastoRepo.sincronizarRemotosConLocales(offlineList, filtro) // Actualiza SQLite con datos remotos

                    listaGastos.clear()
                    listaGastos.addAll(lista)

                    configurarAdapterYVista(lista, offlineMode = false, filtro = filtro, totalEsperados, totalCategoria)

                } catch (e: JSONException) {
                    e.printStackTrace()
                    Toast.makeText(this, "Error procesando datos del servidor", Toast.LENGTH_SHORT).show()
                    cargarFallbackOffline(filtro)
                }
            },
            { error ->
                Log.e("CARGA REMOTA", "Error: ${error.message}")
                Toast.makeText(this, "No se pudo cargar desde el servidor", Toast.LENGTH_SHORT).show()
                cargarFallbackOffline(filtro)
            }
        ) {}
        rq.add(req)
    }

    private fun cargarFallbackOffline(filtro: String) {
        val offline = gastoRepo.obtenerGastosOffline(filtro)
        listaGastos.clear()
        listaGastos.addAll(offline.map { Gasto(it.id, it.producto, it.total, it.estado) })
        configurarAdapterYVista(listaGastos, offlineMode = true, filtro = filtro)
    }

    private fun configurarAdapterYVista(
        lista: List<Gasto>,
        offlineMode: Boolean,
        filtro: String,
        totalEsperados: Double = lista.filter { it.estado == 0 }.sumOf { it.precio },
        totalCategoria: Double = lista.filter { it.estado != 0 }.sumOf { it.precio }
    ) {
        gastosAdapter = GastosAdapter(lista.toMutableList())
        gastosAdapter.onEditarPrecio = { gasto ->
            if (gasto.estado == 1) {
                Toast.makeText(this, "Este producto ya fue adquirido", Toast.LENGTH_SHORT).show()
            } else {
                val et = EditText(this).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    setText(gasto.precio.toString())
                }

                AlertDialog.Builder(this)
                    .setTitle("Editar precio de ${gasto.producto}")
                    .setView(et)
                    .setPositiveButton("Guardar") { _, _ ->
                        val nuevoPrecio = et.text.toString().toDoubleOrNull()
                        if (nuevoPrecio != null) {
                            if (!hayConexionInternet()) {
                                // ✅ ACTUALIZAR EN SQLITE
                                gastoRepo.actualizarPrecioOffline(gasto.id, nuevoPrecio)

                                // ✅ ACTUALIZAR EN LISTA Y ADAPTADOR
                                val index = listaGastos.indexOfFirst { it.id == gasto.id }
                                if (index != -1) {
                                    listaGastos[index].precio = nuevoPrecio
                                    gastosAdapter.notifyItemChanged(index)
                                    Toast.makeText(this, "Precio actualizado offline", Toast.LENGTH_SHORT).show()
                                } else {
                                    Log.w("EditarPrecio", "No se encontró el gasto en listaGastos")
                                }

                                sincronizarDatosOffline()
                            } else {
                                actualizarPrecioServidor(gasto.id, nuevoPrecio, gasto.estado) {
                                    Toast.makeText(this, "Precio actualizado online", Toast.LENGTH_SHORT).show()
                                    cargarGastosConFallback(currentFiltro)
                                }
                            }
                        } else {
                            Toast.makeText(this, "Valor inválido", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }


        gastosAdapter.onCambiarEstadoOffline = { gasto, nuevoEstado ->
            gasto.estado = nuevoEstado
            gastoRepo.actualizarEstadoOffline(gasto.id, nuevoEstado)
            Toast.makeText(this, "Estado guardado offline", Toast.LENGTH_SHORT).show()

            val index = listaGastos.indexOfFirst { it.id == gasto.id }
            if (index != -1) {
                listaGastos[index].estado = nuevoEstado
                gastosAdapter.notifyItemChanged(index)
            }

            sincronizarDatosOffline()
        }

        recyclerView.adapter = gastosAdapter

        txtGastosEsperados.text = "Gastos esperados: $${"%.2f".format(totalEsperados)}"
        val label = if (offlineMode) " (offline)" else ""
        txtGastosCategoria.text = "Gastos $filtro$label: $${"%.2f".format(totalCategoria)}"
    }


    private fun registrarCompra() {
        val prod = inputProducto.text.toString().trim()
        val cnt = inputCantidad.text.toString().trim()
        val prcStr = inputPrecio.text.toString().trim()

        if (prod.isEmpty() || cnt.isEmpty() || prcStr.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }
        val prc = prcStr.toDoubleOrNull() ?: run {
            Toast.makeText(this, "Precio inválido", Toast.LENGTH_SHORT).show()
            return
        }
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(currentFocus?.windowToken, 0)

        if (hayConexionInternet()) {
            val url = "https://elpollovolantuso.com/asi_sistema/android/registro_gasto.php"
            val rq = Volley.newRequestQueue(this)
            val req = object : StringRequest(Request.Method.POST, url,
                { resp ->
                    try {
                        if (JSONObject(resp).getBoolean("success")) {
                            Toast.makeText(this, "Compra registrada online", Toast.LENGTH_SHORT).show()
                            limpiarCampos()
                            cargarGastosConFallback(currentFiltro)
                        } else {
                            guardarCompraOffline(prod, prc)
                        }
                    } catch (_: Exception) {
                        guardarCompraOffline(prod, prc)
                    }
                },
                { _ -> guardarCompraOffline(prod, prc) }
            ) {
                override fun getParams() = mapOf(
                    "producto" to prod,
                    "cantidad" to cnt,
                    "precio" to prcStr
                )
            }
            rq.add(req)
        } else {
            guardarCompraOffline(prod, prc)
        }
    }

    private fun guardarCompraOffline(producto: String, precio: Double) {
        val idTemp = -(System.currentTimeMillis() / 1000).toInt()
        val formatoFecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val formatoHora = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val fechaActual = formatoFecha.format(Date())
        val horaActual = formatoHora.format(Date())

        gastoRepo.guardarGastos(
            listOf(
                GastoOffline(
                    id = idTemp,
                    producto = producto,
                    peso = "",
                    total = precio,
                    fecha = fechaActual,
                    hora = horaActual,
                    estado = 0,
                    sincronizado = false
                )
            )
        )
        Toast.makeText(this, "Guardado offline. Se sincronizará luego.", Toast.LENGTH_LONG).show()
        limpiarCampos()
        cargarGastosConFallback(currentFiltro)
        sincronizarDatosOffline()
    }

    private fun limpiarCampos() {
        inputProducto.text.clear()
        inputCantidad.text.clear()
        inputPrecio.text.clear()
    }

    private fun actualizarPrecioServidor(
        id: Int,
        nuevo: Double,
        estado: Int = 0,
        onSuccess: () -> Unit
    ) {
        val url = "https://elpollovolantuso.com/asi_sistema/android/actualizar_estado_gasto.php"
        val rq = Volley.newRequestQueue(this)

        val req = object : StringRequest(Request.Method.POST, url,
            { resp ->
                try {
                    Log.d("actualizarPrecioServidor", "Respuesta servidor: $resp")
                    val json = JSONObject(resp)
                    if (json.optBoolean("success", false)) {
                        gastoRepo.actualizarPrecioOffline(id, nuevo)  // Actualiza local
                        onSuccess()
                    } else {
                        Toast.makeText(this, "No se pudo actualizar en el servidor", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: JSONException) {
                    Toast.makeText(this, "Error al procesar respuesta JSON", Toast.LENGTH_SHORT).show()
                    Log.e("actualizarPrecioServidor", "Error JSON: ${e.message}")
                }
            },
            { err ->
                Log.e("actualizarPrecioServidor", "Error de red: ${err.message}")
                Toast.makeText(this, "Error de red", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): Map<String, String> = mapOf(
                "id" to id.toString(),
                "precio" to nuevo.toString(),
                "estado" to estado.toString()
            )
        }

        rq.add(req)
    }

    private fun hayConexionInternet(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun sincronizarDatosOffline() {
        if (!hayConexionInternet()) return

        val pendientes = gastoRepo.obtenerGastosNoSincronizados()
        val rq = Volley.newRequestQueue(this)

        pendientes.forEach { gastoOffline ->
            val url = if (gastoOffline.id < 0)
                "https://elpollovolantuso.com/asi_sistema/android/registro_gasto.php"
            else
                "https://elpollovolantuso.com/asi_sistema/android/actualizar_estado_gasto.php"

            val req = object : StringRequest(Request.Method.POST, url,
                { resp ->
                    try {
                        val json = JSONObject(resp)
                        if (json.optBoolean("success", false)) {
                            if (gastoOffline.id < 0) {
                                val nuevoId = json.getInt("id")
                                val fecha = json.optString("fecha", "")
                                val hora = json.optString("hora", "")
                                gastoRepo.reemplazarGastoOfflineConRemoto(
                                    gastoOffline.id, nuevoId, fecha, hora
                                )
                            } else {
                                gastoRepo.marcarComoSincronizado(gastoOffline.id)
                            }
                            cargarGastosConFallback(currentFiltro)
                        }
                    } catch (e: Exception) {
                        Log.e("SYNC", "Error parseando respuesta: ${e.message}")
                    }
                },
                { err -> Log.e("SYNC", "Error sincronizando: ${err.message}") }
            ) {
                override fun getParams(): Map<String, String> = mapOf(
                    "id" to gastoOffline.id.toString(),
                    "producto" to gastoOffline.producto,
                    "cantidad" to "1",
                    "precio" to gastoOffline.total.toString(),
                    "estado" to gastoOffline.estado.toString()
                )
            }
            rq.add(req)
        }
    }
}
