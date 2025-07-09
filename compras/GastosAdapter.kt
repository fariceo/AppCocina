package com.elrancho.cocina.compras

import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.elrancho.cocina.R
import org.json.JSONException
import org.json.JSONObject

class GastosAdapter(
    private val listaGastos: MutableList<Gasto>
) : RecyclerView.Adapter<GastosAdapter.GastoViewHolder>() {

    var onCambiarEstadoOffline: ((Gasto, Int) -> Unit)? = null
    var onEditarPrecio: ((Gasto) -> Unit)? = null

    inner class GastoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtProducto: TextView = view.findViewById(R.id.txtProducto)
        val txtPrecio: TextView = view.findViewById(R.id.txtPrecio)
        val checkBox: CheckBox = view.findViewById(R.id.checkBoxGasto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GastoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gasto, parent, false)
        return GastoViewHolder(view)
    }

    override fun onBindViewHolder(holder: GastoViewHolder, position: Int) {
        val gasto = listaGastos[position]
        holder.txtProducto.text = gasto.producto
        holder.txtPrecio.text = "S/. ${gasto.precio}"

        if (gasto.estado == 1) {
            holder.itemView.alpha = 0.75f
            holder.txtProducto.setTextColor(Color.parseColor("#388E3C"))
            holder.txtPrecio.setTextColor(Color.parseColor("#388E3C"))
        } else {
            holder.itemView.alpha = 1.0f
            holder.txtProducto.setTextColor(Color.parseColor("#D32F2F"))
            holder.txtPrecio.setTextColor(Color.parseColor("#D32F2F"))
        }

        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = gasto.estado == 1

        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            val nuevoEstado = if (isChecked) 1 else 0

            if (hasInternet(holder.itemView.context)) {
                actualizarEstadoGasto(holder.itemView.context, gasto.id, nuevoEstado, gasto.precio) {
                    gasto.estado = nuevoEstado
                    notifyItemChanged(position)
                }
            } else {
                gasto.estado = nuevoEstado
                onCambiarEstadoOffline?.invoke(gasto, nuevoEstado)
                notifyItemChanged(position)
            }
        }

        holder.txtPrecio.setOnClickListener {
            if (gasto.estado == 0) {
                onEditarPrecio?.invoke(gasto)
            } else {
                Toast.makeText(holder.itemView.context, "Este producto ya fue adquirido", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun getItemCount(): Int = listaGastos.size

    private fun hasInternet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun actualizarEstadoGasto(
        context: Context,
        id: Int,
        nuevoEstado: Int,
        precio: Double,
        onSuccess: () -> Unit
    ) {
        val url = "https://elpollovolantuso.com/asi_sistema/android/actualizar_estado_gasto.php"
        val queue = Volley.newRequestQueue(context)
        val req = object : StringRequest(
            Request.Method.POST, url,
            { resp ->
                try {
                    val json = JSONObject(resp)
                    if (json.optBoolean("success", false)) {
                        onSuccess()
                    } else {
                        Toast.makeText(context, "Error al actualizar estado online", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: JSONException) {
                    Toast.makeText(context, "Respuesta inválida del servidor", Toast.LENGTH_SHORT).show()
                }
            },
            { err ->
                Log.e("ActualizarEstado", "Error de conexión: ${err.message}")
                Toast.makeText(context, "No se pudo conectar al servidor", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): MutableMap<String, String> = hashMapOf(
                "id" to id.toString(),
                "estado" to nuevoEstado.toString(),
                "precio" to precio.toString()
            )
        }
        queue.add(req)
    }
}