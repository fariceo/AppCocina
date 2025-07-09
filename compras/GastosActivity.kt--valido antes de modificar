package com.elrancho.cocina.compras

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.elrancho.cocina.R
import org.json.JSONException
import org.json.JSONObject
import android.util.Log
import com.elrancho.cocina.MainActivity
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.content.Context


class GastosActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var gastosAdapter: GastosAdapter
    private lateinit var formulario: LinearLayout
    private lateinit var inputProducto: EditText
    private lateinit var inputCantidad: EditText
    private lateinit var inputPrecio: EditText
    private lateinit var btnRegistrar: Button
    private val listaGastos = mutableListOf<Gasto>()

    private lateinit var txtGastosEsperados: TextView
    private lateinit var txtGastosCategoria: TextView
    private lateinit var logoImageView: ImageView  // Aquí obtenemos la referencia del ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gastos)

        recyclerView = findViewById(R.id.recyclerGastos)
        recyclerView.layoutManager = LinearLayoutManager(this)

        formulario = findViewById(R.id.formularioRegistro)
        inputProducto = findViewById(R.id.inputProducto)
        inputCantidad = findViewById(R.id.inputCantidad)
        inputPrecio = findViewById(R.id.inputPrecio)
        btnRegistrar = findViewById(R.id.btnRegistrarCompra)

        txtGastosEsperados = findViewById(R.id.txtGastosEsperados)
        txtGastosCategoria = findViewById(R.id.txtGastosCategoria)

        // Obtener el ImageView por su ID
        logoImageView = findViewById(R.id.logoImageView)

        val botonDiario: Button = findViewById(R.id.botonDiario)
        val botonSemanal: Button = findViewById(R.id.botonSemanal)
        val botonMensual: Button = findViewById(R.id.botonMensual)

        // Establecer el formulario visible por defecto
        formulario.visibility = View.VISIBLE


        botonDiario.setOnClickListener {
            obtenerGastos("diario")
            formulario.visibility = View.VISIBLE
        }
        botonSemanal.setOnClickListener {
            obtenerGastos("semanal")
            formulario.visibility = View.GONE
        }
        botonMensual.setOnClickListener {
            obtenerGastos("mensual")
            formulario.visibility = View.GONE
        }

        btnRegistrar.setOnClickListener { registrarCompra() }

        // Cargar los gastos diarios por defecto
        obtenerGastos("diario")

        // Establecer el OnClickListener
        logoImageView.setOnClickListener {
            // Crear el Intent para abrir MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)  // Iniciar MainActivity
        }

    }

    private fun obtenerGastos(filtro: String) {
        //val url = "https://elpollovolantuso.com/asi_sistema/android/consulta_gastos.php?filtro=$filtro"
        val url = "http://35.223.94.102/asi_sistema/android/consulta_gastos.php?filtro=$filtro"
        val requestQueue = Volley.newRequestQueue(this)

        val jsonArrayRequest = JsonArrayRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    Log.d("GastosActivity", "Respuesta JSON: $response")

                    listaGastos.clear()

                    var totalEsperados = 0.0
                    var totalCategoria = 0.0

                    for (i in 0 until response.length()) {
                        val item = response.getJSONObject(i)
                        val id = if (item.has("id")) item.getInt("id") else -1
                        val producto = item.optString("producto", "Desconocido")
                        val precio = item.optDouble("total", 0.0)
                        val estado = item.optInt("estado", -1)

                        if (estado == 0) totalEsperados += precio
                        if (estado == 1) totalCategoria += precio

                        listaGastos.add(Gasto(id, producto, precio, estado))
                    }

                    val listaOrdenada = listaGastos.sortedWith(compareBy { it.estado }).toMutableList()
                    gastosAdapter = GastosAdapter(listaOrdenada)

                    // Aquí se asigna el callback del botón de editar
                    gastosAdapter.onEditarPrecio = { gasto ->
                        val editText = EditText(this)
                        editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                        editText.setText(gasto.precio.toString())

                        val dialog = AlertDialog.Builder(this)
                            .setTitle("Editar precio de ${gasto.producto}")
                            .setView(editText)
                            .setPositiveButton("Guardar") { _, _ ->
                                val nuevoPrecio = editText.text.toString().toDoubleOrNull()
                                if (nuevoPrecio != null) {
                                    actualizarPrecioGasto(gasto.id, nuevoPrecio)
                                    gasto.precio = nuevoPrecio
                                    gastosAdapter.notifyDataSetChanged()
                                } else {
                                    Toast.makeText(this, "Precio inválido", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton("Cancelar", null)
                            .create()

                        dialog.show()
                    }

                    recyclerView.adapter = gastosAdapter

                    txtGastosEsperados.text = "Gastos esperados: $${"%.2f".format(totalEsperados)}"
                    txtGastosCategoria.text = "Gastos $filtro: $${"%.2f".format(totalCategoria)}"

                } catch (e: JSONException) {
                    Log.e("GastosActivity", "Error al procesar datos", e)
                    Toast.makeText(this, "Error al procesar datos", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Toast.makeText(this, "Error en la conexión: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        )

        requestQueue.add(jsonArrayRequest)
    }

    private fun registrarCompra() {
        val producto = inputProducto.text.toString().trim()
        val cantidad = inputCantidad.text.toString().trim()
        val precio = inputPrecio.text.toString().trim()

        if (producto.isEmpty() || cantidad.isEmpty() || precio.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        //val url = "https://elpollovolantuso.com/asi_sistema/android/registro_gasto.php"
        val url = "http://35.223.94.102/asi_sistema/android/registro_gasto.php"
        val requestQueue = Volley.newRequestQueue(this)
        // Cerrar el teclado
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
        Toast.makeText(this, "Teclado cerrado", Toast.LENGTH_SHORT).show()

        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            { response ->
                try {
                    val jsonResponse = JSONObject(response)
                    val success = jsonResponse.getBoolean("success")
                    if (success) {
                        Toast.makeText(this, "Compra registrada con éxito", Toast.LENGTH_SHORT).show()
                        inputProducto.text.clear()
                        inputCantidad.text.clear()
                        inputPrecio.text.clear()
                        obtenerGastos("diario")
                    } else {
                        Toast.makeText(this, "Error al registrar compra", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: JSONException) {
                    Toast.makeText(this, "Error en el servidor", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Toast.makeText(this, "Error en la conexión: ${error.message}", Toast.LENGTH_SHORT).show()
            }) {
            override fun getParams(): MutableMap<String, String> {
                return hashMapOf(
                    "producto" to producto,
                    "cantidad" to cantidad,
                    "precio" to precio
                )
            }
        }

        requestQueue.add(stringRequest)
    }

    private fun actualizarPrecioGasto(id: Int, nuevoPrecio: Double) {
       // val url = "https://elpollovolantuso.com/asi_sistema/android/registro_gasto.php"
        val url = "http://35.223.94.102/asi_sistema/android/registro_gasto.php"
        val requestQueue = Volley.newRequestQueue(this)

        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            { response ->
                try {
                    val jsonResponse = JSONObject(response)
                    if (jsonResponse.getBoolean("success")) {
                        Toast.makeText(this, "Precio actualizado correctamente", Toast.LENGTH_SHORT).show()
                        obtenerGastos("diario")
                    } else {
                        Toast.makeText(this, "Error al actualizar precio", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: JSONException) {
                    Toast.makeText(this, "Respuesta inválida del servidor", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Toast.makeText(this, "Error de red: ${error.message}", Toast.LENGTH_SHORT).show()
            }) {
            override fun getParams(): MutableMap<String, String> {
                return hashMapOf(
                    "id" to id.toString(),
                    "precio" to nuevoPrecio.toString()
                )
            }
        }

        requestQueue.add(stringRequest)
    }
}
