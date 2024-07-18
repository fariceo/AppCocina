package com.elrancho.cocina

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var usuario: EditText
    private lateinit var producto: EditText
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        usuario = findViewById(R.id.usuario)
        producto = findViewById(R.id.producto)
    }

    fun guardar(view: View) {
        val url = "https://elpollovolantuso.com/testing.php"
        val usuarioText = usuario.text.toString()
        val productoText = producto.text.toString()

        val formBody = FormBody.Builder()
            .add("usuario", usuarioText)
            .add("producto", productoText)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()
///fwewfwefwedfewfdewfwefewfew

        ///mañana tendre mucho dinero


        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(applicationContext, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                response.body?.let { responseBody ->
                    val responseData = responseBody.string()
                    val json = JSONObject(responseData)
                    runOnUiThread {
                        val message = if (json.getBoolean("success")) {
                            "Datos insertados correctamente"
                        } else {
                            "Error: ${json.getString("message")}"
                        }
                        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()


                        //josjadjaoj
                    }
                }
            }
        })
    }
}
