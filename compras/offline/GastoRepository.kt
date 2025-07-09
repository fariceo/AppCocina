package com.elrancho.cocina.compras.offline

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

class GastoRepository(context: Context) {

    private val dbHelper = GastoDatabaseHelper(context)

    fun guardarGastos(gastos: List<GastoOffline>) {
        val db = dbHelper.writableDatabase
        for (gasto in gastos) {
            val values = ContentValues().apply {
                put("id", gasto.id)
                put("producto", gasto.producto)
                put("peso", gasto.peso)
                put("total", gasto.total)
                put("fecha", gasto.fecha)
                put("hora", gasto.hora)
                put("estado", gasto.estado)
                put("sincronizado", if (gasto.sincronizado) 1 else 0)
            }
            db.insertWithOnConflict("gastos", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
        db.close()
    }

    fun obtenerGastosOffline(filtro: String): List<GastoOffline> {
        val lista = mutableListOf<GastoOffline>()
        val db = dbHelper.readableDatabase

        val formato = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val hoy = formato.format(Date())

        // Calcular inicio semana (lunes)
        val calSemana = Calendar.getInstance()
        calSemana.firstDayOfWeek = Calendar.MONDAY
        calSemana.set(Calendar.HOUR_OF_DAY, 0)
        calSemana.set(Calendar.MINUTE, 0)
        calSemana.set(Calendar.SECOND, 0)
        calSemana.set(Calendar.MILLISECOND, 0)
        while (calSemana.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            calSemana.add(Calendar.DAY_OF_MONTH, -1)
        }
        val inicioSemana = formato.format(calSemana.time)

        // Calcular inicio mes (primer día del mes)
        val calMes = Calendar.getInstance()
        calMes.set(Calendar.DAY_OF_MONTH, 1)
        calMes.set(Calendar.HOUR_OF_DAY, 0)
        calMes.set(Calendar.MINUTE, 0)
        calMes.set(Calendar.SECOND, 0)
        calMes.set(Calendar.MILLISECOND, 0)
        val inicioMes = formato.format(calMes.time)

        val selection: String
        val selectionArgs: Array<String>

        when (filtro) {
            "diario" -> {
                selection = "fecha = ?"
                selectionArgs = arrayOf(hoy)
            }
            "semanal" -> {
                selection = "fecha >= ?"
                selectionArgs = arrayOf(inicioSemana)
            }
            "mensual" -> {
                selection = "fecha >= ?"
                selectionArgs = arrayOf(inicioMes)
            }
            else -> {
                selection = ""
                selectionArgs = emptyArray()
            }
        }

        val cursor = if (selection.isNotEmpty()) {
            db.query("gastos", null, selection, selectionArgs, null, null, "fecha ASC")
        } else {
            db.query("gastos", null, null, null, null, null, "fecha ASC")
        }

        if (cursor.moveToFirst()) {
            do {
                lista.add(
                    GastoOffline(
                        id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                        producto = cursor.getString(cursor.getColumnIndexOrThrow("producto")),
                        peso = cursor.getString(cursor.getColumnIndexOrThrow("peso")),
                        total = cursor.getDouble(cursor.getColumnIndexOrThrow("total")),
                        fecha = cursor.getString(cursor.getColumnIndexOrThrow("fecha")),
                        hora = cursor.getString(cursor.getColumnIndexOrThrow("hora")),
                        estado = cursor.getInt(cursor.getColumnIndexOrThrow("estado")),
                        sincronizado = cursor.getInt(cursor.getColumnIndexOrThrow("sincronizado")) == 1
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return lista
    }

    fun obtenerGastosNoSincronizados(): List<GastoOffline> {
        val lista = mutableListOf<GastoOffline>()
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM gastos WHERE sincronizado = 0", null)
        while (cursor.moveToNext()) {
            lista.add(
                GastoOffline(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                    producto = cursor.getString(cursor.getColumnIndexOrThrow("producto")),
                    peso = cursor.getString(cursor.getColumnIndexOrThrow("peso")),
                    total = cursor.getDouble(cursor.getColumnIndexOrThrow("total")),
                    fecha = cursor.getString(cursor.getColumnIndexOrThrow("fecha")),
                    hora = cursor.getString(cursor.getColumnIndexOrThrow("hora")),
                    estado = cursor.getInt(cursor.getColumnIndexOrThrow("estado")),
                    sincronizado = false
                )
            )
        }
        cursor.close()
        db.close()
        return lista
    }

    fun marcarComoSincronizado(id: Int) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("sincronizado", 1)
        }
        db.update("gastos", values, "id = ?", arrayOf(id.toString()))
        db.close()
    }

    fun actualizarEstadoOffline(id: Int, nuevoEstado: Int) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("estado", nuevoEstado)
            put("sincronizado", 0)
        }
        val rowsUpdated = db.update("gastos", values, "id = ?", arrayOf(id.toString()))
        Log.d("GastoRepo", "Estado actualizado offline: $rowsUpdated para id=$id")

        db.close()
    }
    fun actualizarPrecioOffline(id: Int, nuevoPrecio: Double) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("total", nuevoPrecio)
            put("sincronizado", 0)
        }
        val rowsUpdated = db.update("gastos", values, "id = ?", arrayOf(id.toString()))
        Log.d("GastoRepo", "Precio actualizado en offline: $rowsUpdated")

        db.close()
    }


    fun reemplazarGastoOfflineConRemoto(idTemporal: Int, idNuevo: Int, fecha: String, hora: String) {
        val db = dbHelper.writableDatabase

        val cursor = db.rawQuery("SELECT * FROM gastos WHERE id = ?", arrayOf(idTemporal.toString()))
        if (cursor.moveToFirst()) {
            val producto = cursor.getString(cursor.getColumnIndexOrThrow("producto"))
            val peso = cursor.getString(cursor.getColumnIndexOrThrow("peso"))
            val total = cursor.getDouble(cursor.getColumnIndexOrThrow("total"))
            val estado = cursor.getInt(cursor.getColumnIndexOrThrow("estado"))

            db.delete("gastos", "id = ?", arrayOf(idTemporal.toString()))

            val nuevoCV = ContentValues().apply {
                put("id", idNuevo)
                put("producto", producto)
                put("peso", peso)
                put("total", total)
                put("fecha", fecha)
                put("hora", hora)
                put("estado", estado)
                put("sincronizado", 1)
            }
            db.insert("gastos", null, nuevoCV)
        }
        cursor.close()
        db.close()
    }

    fun sincronizarRemotosConLocales(remotos: List<GastoOffline>, filtro: String) {
        val db = dbHelper.writableDatabase

        // Crear un set de IDs de los datos remotos que llegaron
        val idsRemotos = remotos.map { it.id }.toSet()

        // Obtener IDs locales para eliminar solo si están sincronizados
        val cursor = db.rawQuery("SELECT id, sincronizado FROM gastos", null)
        val idsLocalesAEliminar = mutableListOf<Int>()

        while (cursor.moveToNext()) {
            val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
            val sincronizado = cursor.getInt(cursor.getColumnIndexOrThrow("sincronizado")) == 1
            if (id > 0 && id !in idsRemotos && sincronizado) {
                idsLocalesAEliminar.add(id)
            }
        }
        cursor.close()

        for (id in idsLocalesAEliminar) {
            db.delete("gastos", "id = ?", arrayOf(id.toString()))
            Log.d("GastoRepo", "Eliminado gasto local con id=$id (no existe en remoto)")
        }

        // Insertar o actualizar registros recibidos del servidor
        for (gasto in remotos) {
            val cursorLocal = db.rawQuery("SELECT sincronizado FROM gastos WHERE id = ?", arrayOf(gasto.id.toString()))
            val localSincronizado = if (cursorLocal.moveToFirst()) cursorLocal.getInt(0) == 1 else true
            cursorLocal.close()

            if (localSincronizado) {
                val cv = ContentValues().apply {
                    put("id", gasto.id)
                    put("producto", gasto.producto)
                    put("peso", gasto.peso)
                    put("total", gasto.total)
                    put("fecha", gasto.fecha)
                    put("hora", gasto.hora)
                    put("estado", gasto.estado)
                    put("sincronizado", 1)
                }

                val rowsUpdated = db.update("gastos", cv, "id = ?", arrayOf(gasto.id.toString()))
                if (rowsUpdated == 0) {
                    db.insert("gastos", null, cv)
                    Log.d("GastoRepo", "Insertado gasto remoto id=${gasto.id}")
                } else {
                    Log.d("GastoRepo", "Actualizado gasto remoto id=${gasto.id}")
                }
            } else {
                Log.d("GastoRepo", "No se sobrescribe gasto local id=${gasto.id} no sincronizado")
            }
        }

        db.close()
    }

    fun sincronizarCambiosOnline(context: Context, onComplete: (() -> Unit)? = null) {
        val gastosNoSincronizados = obtenerGastosNoSincronizados()
        if (gastosNoSincronizados.isEmpty()) {
            onComplete?.invoke()
            return
        }

        val queue = Volley.newRequestQueue(context)
        var sincronizados = 0

        for (gasto in gastosNoSincronizados) {
            val url = "https://elpollovolantuso.com/asi_sistema/android/actualizar_estado_gasto.php"
            val req = object : StringRequest(
                Request.Method.POST, url,
                { response ->
                    try {
                        val json = JSONObject(response)
                        if (json.optBoolean("success", false)) {
                            marcarComoSincronizado(gasto.id)
                        }
                    } catch (e: JSONException) {
                        // manejar error
                    }
                    sincronizados++
                    if (sincronizados == gastosNoSincronizados.size) {
                        onComplete?.invoke()
                    }
                },
                { _ ->
                    sincronizados++
                    if (sincronizados == gastosNoSincronizados.size) {
                        onComplete?.invoke()
                    }
                }
            ) {
                override fun getParams(): MutableMap<String, String> = hashMapOf(
                    "id" to gasto.id.toString(),
                    "estado" to gasto.estado.toString(),
                    "precio" to gasto.total.toString()
                )
            }
            queue.add(req)
        }
    }

    fun limpiarDatosAntiguos(filtro: String) {
        val db = dbHelper.writableDatabase

        val whereClause = when (filtro) {
            "diario" -> "DATE(fecha) != DATE('now', 'localtime')"
            "semanal" -> "strftime('%W', fecha) != strftime('%W', 'now', 'localtime')"
            "mensual" -> "strftime('%m', fecha) != strftime('%m', 'now', 'localtime')"
            else -> null
        }

        whereClause?.let {
            db.delete("gastos", it, null)
        }
        db.close()
    }
}
