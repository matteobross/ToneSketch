package it.progmob.tonesketch

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

class PlayActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var recyclerView: RecyclerView
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play)

        recyclerView = findViewById(R.id.rvFiles)
        recyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        loadFiles()
    }

    private fun loadFiles() {
        val dir = externalCacheDir
        val files = dir?.listFiles { f -> f.name.endsWith(".m4a") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        if (files.isEmpty()) {
            Toast.makeText(this, "Nessuna registrazione trovata", Toast.LENGTH_SHORT).show()
        }

        val adapter = FileAdapter(
            files,
            onPlay = { file -> playRecording(file) },
            onUpload = { file -> uploadAudio(file) },
            onDelete = { file ->
                // LOGICA DI ELIMINAZIONE
                if (file.exists() && file.delete()) {
                    Toast.makeText(this, "File eliminato", Toast.LENGTH_SHORT).show()
                    loadFiles() // Ricarica la lista
                } else {
                    Toast.makeText(this, "Errore eliminazione", Toast.LENGTH_SHORT).show()
                }
            }
        )

        recyclerView.adapter = adapter
    }

    private fun playRecording(file: File) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
            Toast.makeText(this, "▶️ Riproduzione: ${file.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Errore riproduzione", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadAudio(file: File) {
        Toast.makeText(this, "⏳ Analisi in corso...", Toast.LENGTH_SHORT).show()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody("audio/m4a".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url("http://84.8.250.185:8000/analyze")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@PlayActivity, "❌ Errore Server: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val jsonString = response.body?.string()

                runOnUiThread {
                    if (response.isSuccessful && jsonString != null) {
                        Toast.makeText(this@PlayActivity, "✅ Analisi Completata!", Toast.LENGTH_SHORT).show()

                        try {
                            val json = JSONObject(jsonString)
                            // Prepariamo l'intent per cambiare activity
                            val intent = Intent(this@PlayActivity, ArrangerActivity::class.java)

                            // Passiamo i dati: JSON completo e percorso audio
                            intent.putExtra("analysis_json", jsonString)
                            intent.putExtra("voice_path", file.absolutePath)

                            startActivity(intent)

                        } catch (e: Exception) {
                            Toast.makeText(this@PlayActivity, "Errore dati server", Toast.LENGTH_SHORT).show()
                        }

                    } else {
                        Toast.makeText(this@PlayActivity, "Errore dal server: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}