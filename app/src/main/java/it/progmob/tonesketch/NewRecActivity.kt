package it.progmob.tonesketch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.ToneGenerator
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

    // Conteggio battute:
    // Valori negativi (-4, -3...) = Count-In (Preroll)
    // Valori positivi (1, 2...) = Registrazione effettiva
    private var currentBeatCounter = 0

    private var totalBeatsToRecord = 0
    private var beatInterval: Long = 500

    // Generatore di suoni a zero latenza
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

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
                btnRecord.textSize = 40f
            } else {
                containerSettings.visibility = View.GONE
                btnRecord.textSize = 32f
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
                        startMetronomeRecording() // Modalità Nuova (Count-in + Rec)
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

        try {
            mediaRecorder?.start() // Parte subito
            isRecording = true
            btnRecord.text = "STOP"
            btnRecord.setBackgroundColor(getColor(android.R.color.holo_red_dark))
            txtStatus.text = "Registrazione libera..."
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- MODALITÀ 2: REGISTRAZIONE A TEMPO CON COUNT-IN ---
    private fun startMetronomeRecording() {
        val bpmStr = editBpm.text.toString()
        val barsStr = editBars.text.toString()
        if (bpmStr.isEmpty() || barsStr.isEmpty()) return

        val bpm = bpmStr.toInt()
        val bars = barsStr.toInt()

        // Calcoli
        beatInterval = (60000.0 / bpm).toLong()
        totalBeatsToRecord = bars * 4

        // Preparo il recorder MA NON LO AVVIO ANCORA
        if (!setupMediaRecorder()) return

        // Blocca UI
        editBpm.isEnabled = false
        editBars.isEnabled = false
        switchMetronome.isEnabled = false

        isRecording = true

        // IMPOSTO IL COUNT-IN (4 battute a vuoto prima di partire)
        // Partiamo da -4. Quando arriverà a 1, inizierà a registrare.
        currentBeatCounter = -4

        // Avvia Loop Visivo/Sonoro
        handler.post(metronomeRunnable)
    }

    // --- SETUP RECORDER (Comune) ---
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
                // NOTA: Non chiamiamo start() qui per la modalità metronomo,
                // lo chiamiamo nel Runnable quando il conto arriva a 0.
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

            // --- FASE 1: COUNT-IN (Numeri negativi: -4, -3, -2, -1) ---
            if (currentBeatCounter < 0) {
                // Aggiorna UI (Giallo per attesa)
                btnRecord.text = "${Math.abs(currentBeatCounter)}" // Mostra 4, 3, 2, 1
                btnRecord.setBackgroundColor(getColor(android.R.color.holo_orange_light))
                txtStatus.text = "Preparati..."

                // Suono Metronomo (Tick acuto)
                toneGenerator.startTone(ToneGenerator.TONE_SUP_PIP, 50)
            }

            // --- FASE 2: START REGISTRAZIONE (Istante 0) ---
            if (currentBeatCounter == 0) {
                try {
                    mediaRecorder?.start() // ORA PARTE LA REGISTRAZIONE!
                    txtStatus.text = "REGISTRAZIONE IN CORSO"
                } catch (e: Exception) {
                    stopRecording()
                    return
                }
            }

            // --- FASE 3: REGISTRAZIONE ATTIVA (Numeri positivi: 1, 2, 3...) ---
            if (currentBeatCounter >= 0) {
                // Calcolo beat musicale (1, 2, 3, 4 ripetuto)
                val musicalBeat = (currentBeatCounter % 4) + 1
                btnRecord.text = "$musicalBeat"

                // Flash visivo (Rosso scuro sull'1, Chiaro sugli altri)
                if (musicalBeat == 1) {
                    btnRecord.setBackgroundColor(getColor(android.R.color.holo_orange_dark))
                    // Da attivare solo se tieni le cuffie
                    // toneGenerator.startTone(ToneGenerator.TONE_SUP_PIP, 50)
                } else {
                    btnRecord.setBackgroundColor(getColor(android.R.color.holo_orange_light))
                    // Suono Click Debole
                    // toneGenerator.startTone(ToneGenerator.TONE_SUP_RADIO_NOT_AVAIL, 50)
                }

                // NOTA SUI SUONI DURANTE LA REGISTRAZIONE:
                // Ho commentato i suoni durante la fase di registrazione vera e propria (if >= 0).
                // Perché se non usi le cuffie, il microfono registrerà il "BEEP" del telefono
                // e rovinerà la tua traccia vocale.
                // Se voglio il metronomo ANCHE mentre canti, de-commento le righe `toneGenerator` qui sopra.
                // Per ora, lascio il suono SOLO nel Count-In (fase < 0), poi magari metto uno switch ma mi sembra stupido
            }

            // Avanzamento e Controllo Fine
            currentBeatCounter++

            // Se abbiamo finito le battute previste (es. 4 battute * 4 quarti = 16 beat)
            // Nota: totalBeatsToRecord è calcolato solo sulla parte registrata
            if (currentBeatCounter > totalBeatsToRecord) {
                stopRecording()
            } else {
                handler.postDelayed(this, beatInterval)
            }
        }
    }

    private fun stopRecording() {
        try {
            handler.removeCallbacks(metronomeRunnable)

            // Ferma il recorder solo se stava effettivamente registrando
            // (Se premi stop durante il count-in, il recorder non era ancora partito!)
            if (currentBeatCounter > 0) {
                mediaRecorder?.stop()
            }

            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false

            // Reset UI
            btnRecord.text = "REC"
            btnRecord.setBackgroundColor(getColor(android.R.color.holo_red_light))
            txtStatus.text = "File salvato!"

            editBpm.isEnabled = true
            editBars.isEnabled = true
            switchMetronome.isEnabled = true

            Toast.makeText(this, "Fatto!", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
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
        toneGenerator.release() // Rilascia risorse audio
    }
}