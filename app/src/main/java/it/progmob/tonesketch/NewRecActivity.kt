package it.progmob.tonesketch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.IOException

class NewRecActivity : AppCompatActivity() {

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var outputFile: String = ""

    // UI Elements
    private lateinit var btnRecord: Button
    private lateinit var btnNext: Button
    private lateinit var txtStatus: TextView
    private lateinit var switchMetronome: SwitchMaterial
    private lateinit var containerSettings: LinearLayout
    private lateinit var editBpm: EditText
    private lateinit var editBars: EditText

    // Metronome Logic variables
    private val handler = Handler(Looper.getMainLooper())
    private var currentBeat = 1
    private var totalBeatsToRecord = 0
    private var beatsRecorded = 0
    private var beatInterval: Long = 500

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_rec)

        // Init Views
        btnRecord = findViewById(R.id.btnRecord)
        btnNext = findViewById(R.id.btnNext)
        txtStatus = findViewById(R.id.txtStatus)
        switchMetronome = findViewById(R.id.switchMetronome)
        containerSettings = findViewById(R.id.containerSettings)
        editBpm = findViewById(R.id.editBpm)
        editBars = findViewById(R.id.editBars)

        // Gestione Visibilità pannello impostazioni
        switchMetronome.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                containerSettings.visibility = View.VISIBLE
                btnRecord.textSize = 40f // Più grande per i numeri
            } else {
                containerSettings.visibility = View.GONE
                btnRecord.textSize = 32f // Normale per testo REC
            }
        }

        btnNext.setOnClickListener {
            startActivity(Intent(this, PlayActivity::class.java))
        }

        btnRecord.setOnClickListener {
            if (isRecording) {
                // STOP (Manuale)
                stopRecording()
            } else {
                // START
                if (checkPermissions()) {
                    if (switchMetronome.isChecked) {
                        startMetronomeRecording() // Modalità Nuova
                    } else {
                        startFreeRecording()      // Modalità Classica
                    }
                } else {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 101)
                }
            }
        }
    }

    // --- MODALITÀ 1: REGISTRAZIONE LIBERA ---
    private fun startFreeRecording() {
        if (!setupMediaRecorder()) return

        isRecording = true
        btnRecord.text = "STOP"
        txtStatus.text = "Registrazione libera..."
    }

    // --- MODALITÀ 2: REGISTRAZIONE A TEMPO ---
    private fun startMetronomeRecording() {
        val bpmStr = editBpm.text.toString()
        val barsStr = editBars.text.toString()
        if (bpmStr.isEmpty() || barsStr.isEmpty()) return

        val bpm = bpmStr.toInt()
        val bars = barsStr.toInt()

        // Calcoli
        beatInterval = (60000.0 / bpm).toLong()
        totalBeatsToRecord = bars * 4

        if (!setupMediaRecorder()) return

        // Blocca UI
        editBpm.isEnabled = false
        editBars.isEnabled = false
        switchMetronome.isEnabled = false

        isRecording = true
        currentBeat = 1
        beatsRecorded = 0

        // Avvia Loop Visivo
        handler.post(metronomeRunnable)
    }

    // --- LOGICA COMUNE ---
    private fun setupMediaRecorder(): Boolean {
        outputFile = "${externalCacheDir?.absolutePath}/rec_${System.currentTimeMillis()}.m4a"
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()

        return try {
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile)
                prepare()
                start()
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Errore Mic: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    private val metronomeRunnable = object : Runnable {
        override fun run() {
            if (!isRecording) return

            btnRecord.text = "$currentBeat"

            // Flash visivo
            if (currentBeat == 1) {
                btnRecord.setBackgroundColor(getColor(android.R.color.holo_red_dark))
            } else {
                btnRecord.setBackgroundColor(getColor(android.R.color.holo_red_light))
            }

            beatsRecorded++
            currentBeat++
            if (currentBeat > 4) currentBeat = 1

            if (beatsRecorded > totalBeatsToRecord) {
                stopRecording()
            } else {
                handler.postDelayed(this, beatInterval)
            }
        }
    }

    private fun stopRecording() {
        try {
            handler.removeCallbacks(metronomeRunnable) // Ferma timer se c'era
            mediaRecorder?.apply { stop(); release() }
            mediaRecorder = null
            isRecording = false

            // Reset UI Totale
            btnRecord.text = "REC"
            btnRecord.setBackgroundColor(getColor(android.R.color.holo_red_light))
            txtStatus.text = "Registrazione salvata!"

            // Riabilita controlli
            editBpm.isEnabled = true
            editBars.isEnabled = true
            switchMetronome.isEnabled = true

            Toast.makeText(this, "File salvato!", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkPermissions(): Boolean {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permesso OK, riprova!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if(isRecording) {
            mediaRecorder?.release()
            handler.removeCallbacks(metronomeRunnable)
        }
    }
}