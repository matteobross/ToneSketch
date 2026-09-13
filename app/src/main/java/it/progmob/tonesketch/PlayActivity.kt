package it.progmob.tonesketch

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
    
companion object{
    // TODO: inserisci qui l'indirizzo del tuo server (es. "http://192.168.1.10" oppure un dominio)
private const val SERVER_URL = "http://url_server"
private const val ANALYZE_ENDPOINT = "$SERVER_URL:8000/analyze"
}
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
            onRename = { file -> showRenameDialog(file) }, // <--- Logica Rinomina
            onDelete = { file ->
                if (file.exists() && file.delete()) {
                    Toast.makeText(this, "File eliminato", Toast.LENGTH_SHORT).show()
                    loadFiles()
                } else {
                    Toast.makeText(this, "Errore eliminazione", Toast.LENGTH_SHORT).show()
                }
            }
        )

        recyclerView.adapter = adapter
    }

    // --- FUNZIONE PER RINOMINARE ---
    private fun showRenameDialog(file: File) {
        val editText = EditText(this)
        // Mostriamo il nome attuale senza l'estensione .m4a per comodità
        editText.setText(file.nameWithoutExtension)

        AlertDialog.Builder(this)
            .setTitle("Rinomina file")
            .setMessage("Inserisci il nuovo nome:")
            .setView(editText)
            .setPositiveButton("Salva") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    // Manteniamo l'estensione .m4a
                    val newFile = File(file.parent, "$newName.m4a")

                    if (file.renameTo(newFile)) {
                        Toast.makeText(this, "Rinominato!", Toast.LENGTH_SHORT).show()
                        loadFiles() // Ricarica la lista per vedere il cambio
                    } else {
                        Toast.makeText(this, "Errore: nome non valido o esistente", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
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

    //caricare al server oracle
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
            .url("http://url_server:8000/analyze")
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
                            val intent = Intent(this@PlayActivity, ArrangerActivity::class.java)

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
