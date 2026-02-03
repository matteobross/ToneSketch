package it.progmob.tonesketch

import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONObject
import java.io.File

class ArrangerActivity : AppCompatActivity() {

    // UI
    private lateinit var txtKey: TextView
    private lateinit var txtBpm: TextView
    private lateinit var spinnerProgression: Spinner
    private lateinit var radioDrums: RadioGroup
    private lateinit var btnPlay: Button
    private lateinit var btnStop: Button
    private lateinit var txtChordList: TextView
    private lateinit var btnFretboard: Button

    // Audio Engine
    private lateinit var soundPool: SoundPool
    private var voicePlayer: MediaPlayer? = null

    // Suoni
    private val pianoNotes = HashMap<String, Int>()
    private var kickId = 0
    private var snareId = 0
    private var hihatId = 0

    // Loop Logic
    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    private var step = 0
    private var startTime: Long = 0
    private var totalSixteenthsPlayed: Long = 0

    // Dati Musicali
    private var bpm: Double = 120.0
    private var key = "C"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_arranger)

        // Init UI
        txtKey = findViewById(R.id.txtKey)
        txtBpm = findViewById(R.id.txtBpm)
        spinnerProgression = findViewById(R.id.spinnerProgression)
        radioDrums = findViewById(R.id.radioDrums)
        btnPlay = findViewById(R.id.btnPlay)
        btnStop = findViewById(R.id.btnStop)
        txtChordList = findViewById(R.id.txtChordList)
        btnFretboard = findViewById(R.id.btnFretboard)

        // 1. Leggi i dati passati da PlayActivity
        val jsonString = intent.getStringExtra("analysis_json")
        val voicePath = intent.getStringExtra("voice_path")

        if (jsonString != null) {
            try {
                val json = JSONObject(jsonString)
                bpm = json.optDouble("bpm", 120.0)
                key = json.optString("key", "C")
            } catch (e: Exception) {
                Toast.makeText(this, "Errore dati: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        txtKey.text = "Key: $key"
        txtBpm.text = String.format("BPM: %.1f", bpm)
        txtChordList.text = "Tonalità rilevata: $key"

        // Setup Spinner Accordi
        val progressions = listOf("Nessun Piano", "I - V - vi - IV", "ii - V - I", "vi - IV - I - V")
        spinnerProgression.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, progressions)
        spinnerProgression.setSelection(1) // Default Pop

        // 2. Carica Audio (SoundPool e Voice)
        soundPool = SoundPool.Builder().setMaxStreams(10).build()
        loadSounds()

        if (voicePath != null) {
            val file = File(voicePath)
            if (file.exists()) {
                voicePlayer = MediaPlayer().apply {
                    setDataSource(voicePath)
                    isLooping = true // La voce va in loop
                    prepare()
                }
            }
        }

        // Listeners
        btnPlay.setOnClickListener { startLoop() }
        btnStop.setOnClickListener { stopLoop() }
        btnFretboard.setOnClickListener { showFretboard() }
    }

    private fun loadSounds() {
        // Carica Batteria
        kickId = soundPool.load(this, R.raw.kick, 1)
        snareId = soundPool.load(this, R.raw.snare, 1)
        hihatId = soundPool.load(this, R.raw.hihat, 1)

        // Carica Piano (Note cromatiche)
        val noteNames = listOf("c","csharp","d","dsharp","e","f","fsharp","g","gsharp","a","asharp","b")
        val displayNames = listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")

        for (i in noteNames.indices) {
            val resId = resources.getIdentifier("piano_${noteNames[i]}", "raw", packageName)
            if (resId != 0) {
                pianoNotes[displayNames[i]] = soundPool.load(this, resId, 1)
            }
        }
    }

    private fun startLoop() {
        if (isPlaying) return
        isPlaying = true
        step = 0
        totalSixteenthsPlayed = 0

        // Sincronizza voce
        voicePlayer?.seekTo(0)
        voicePlayer?.start()

        startTime = System.currentTimeMillis()
        handler.post(loopRunnable)
    }

    private fun stopLoop() {
        isPlaying = false
        handler.removeCallbacks(loopRunnable)
        if (voicePlayer?.isPlaying == true) {
            voicePlayer?.pause()
        }
    }

    // --- IL MOTORE RITMICO (Metronomo preciso) ---
    private val loopRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying) return

            playDrums(step)
            playHarmony(step)

            step = (step + 1) % 16
            totalSixteenthsPlayed++

            // Calcolo preciso del tempo per evitare drift
            val msPerBeat = 60000.0 / bpm
            val stepDuration = msPerBeat / 4.0 // Sedicesimi
            val nextExpectedTime = startTime + (stepDuration * totalSixteenthsPlayed).toLong()
            val now = System.currentTimeMillis()
            var delay = nextExpectedTime - now
            if (delay < 0) delay = 0

            handler.postDelayed(this, delay)
        }
    }

    private fun playDrums(step: Int) {
        val style = when(radioDrums.checkedRadioButtonId) {
            R.id.radioPop -> "pop"
            R.id.radioRock -> "rock"
            R.id.radioReggae -> "reggae"
            else -> "mute"
        }
        if (style == "mute") return

        when (style) {
            "pop" -> {
                if (step == 0 || step == 8) soundPool.play(kickId, 1f, 1f, 1, 0, 1f)
                if (step == 4 || step == 12) soundPool.play(snareId, 1f, 1f, 1, 0, 1f)
                if (step % 2 == 0) soundPool.play(hihatId, 0.5f, 0.5f, 1, 0, 1f)
            }
            "rock" -> {
                if (step == 0 || step == 10) soundPool.play(kickId, 1f, 1f, 1, 0, 1f)
                if (step == 4 || step == 12) soundPool.play(snareId, 1f, 1f, 1, 0, 1f)
                if (step % 4 == 0) soundPool.play(hihatId, 0.6f, 0.6f, 1, 0, 1f)
            }
            "reggae" -> {
                if (step == 8) { soundPool.play(kickId, 1f, 1f, 1, 0, 1f); soundPool.play(snareId, 1f, 1f, 1, 0, 1f) }
                if (step % 4 == 2) soundPool.play(hihatId, 0.7f, 0.7f, 1, 0, 1f)
            }
        }
    }

    private fun playHarmony(step: Int) {
        val selectedIdx = spinnerProgression.selectedItemPosition
        if (selectedIdx == 0) return // Nessun piano
        if (step % 4 != 0) return // Suona solo sui quarti (ogni 4 sedicesimi)

        val progression = when (selectedIdx) {
            1 -> listOf("I", "V", "vi", "IV") // Pop classico
            2 -> listOf("ii", "V", "I", "I")  // Jazz
            3 -> listOf("vi", "IV", "I", "V") // Emozionale
            else -> listOf("I", "V", "vi", "IV")
        }

        val chordIndex = (step / 4) % progression.size
        val degree = progression[chordIndex]

        val (root, type) = getChordFromDegree(key, degree)
        playChord(root, type)
    }

    private fun playChord(root: String, type: String) {
        val intervals = if (type == "minor") listOf(0, 3, 7) else listOf(0, 4, 7)
        val allNotes = listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        val rootIdx = allNotes.indexOf(root)

        for (interval in intervals) {
            val noteName = allNotes[(rootIdx + interval) % 12]
            val soundId = pianoNotes[noteName]
            soundId?.let { soundPool.play(it, 0.8f, 0.8f, 1, 0, 1f) }
        }
    }

    private fun getChordFromDegree(keyRoot: String, degree: String): Pair<String, String> {
        val allNotes = listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        val majorScaleIntervals = listOf(0, 2, 4, 5, 7, 9, 11)

        val keyIdx = allNotes.indexOf(keyRoot)

        val degreeOffsetMap = mapOf("I" to 0, "ii" to 1, "iii" to 2, "IV" to 3, "V" to 4, "vi" to 5, "vii°" to 6)
        val degreeTypeMap = mapOf("I" to "major", "ii" to "minor", "iii" to "minor", "IV" to "major", "V" to "major", "vi" to "minor")

        val scaleStep = degreeOffsetMap[degree] ?: 0
        val semitoneOffset = majorScaleIntervals[scaleStep]

        val chordRoot = allNotes[(keyIdx + semitoneOffset) % 12]
        val chordType = degreeTypeMap[degree] ?: "major"

        return Pair(chordRoot, chordType)
    }

    // --- FUNZIONI FRETBOARD ---

    private fun showFretboard() {
        try {
            val dialog = BottomSheetDialog(this)
            val view = layoutInflater.inflate(R.layout.bottom_fretboard, null)
            dialog.setContentView(view)

            val txtContent = view.findViewById<TextView>(R.id.txtFretboardContent)
            val btnClose = view.findViewById<Button>(R.id.btnCloseFret)

            txtContent.text = generateFretboardVisual(key)

            btnClose.setOnClickListener { dialog.dismiss() }
            dialog.show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Errore apertura Fretboard: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // NUOVA VERSIONE GEMINI (Quella attiva)
    private fun generateFretboardVisual(key: String): String {
        val majorScales = mapOf(
            "C" to listOf("C","D","E","F","G","A","B"),
            "G" to listOf("G","A","B","C","D","E","F#"),
            "D" to listOf("D","E","F#","G","A","B","C#"),
            "A" to listOf("A","B","C#","D","E","F#","G#"),
            "E" to listOf("E","F#","G#","A","B","C#","D#"),
            "B" to listOf("B","C#","D#","E","F#","G#","A#"),
            "F#" to listOf("F#","G#","A#","B","C#","D#","F"),
            "F" to listOf("F","G","A","A#","C","D","E"),
            "Db" to listOf("C#","D#","F","F#","G#","A#","C"),
            "Ab" to listOf("G#","A#","C","C#","D#","F","G"),
            "Eb" to listOf("D#","F","G","G#","A#","C","D"),
            "Bb" to listOf("A#","C","D","D#","F","G","A")
        )

        val scale = majorScales[key] ?: majorScales["C"]!!
        // Accordatura standard: Low E -> High E
        val tuning = listOf("E","A","D","G","B","E")
        val semitones = listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")

        val sb = StringBuilder()
        sb.append("Note scala di $key:\n$scale\n\n")

        // --- 1. COSTRUZIONE HEADER (Numeri tasti) ---
        sb.append("   ") // Spazio per il nome corda
        sb.append(" 0 ") // Tasto vuoto
        sb.append("||")  // Capotasto
        for (i in 1..12) { // Mostriamo 12 tasti (un'ottava completa)
            sb.append(centerText(i.toString(), 5))
            sb.append("|")
        }
        sb.append("\n")

        // Linea di separazione superiore
        sb.append("===+===++=====+=====+=====+=====+=====+=====+=====+=====+=====+=====+=====+=====+\n")

        // --- 2. COSTRUZIONE CORDE ---
        // Usiamo reversed() per avere la corda acuta (E cantino) in alto, come nelle Tablature
        for (stringNote in tuning.reversed()) {

            // A. Nome della corda (es. "E  ")
            sb.append(centerText(stringNote, 3))

            // B. Nota a vuoto (Tasto 0)
            if (scale.contains(stringNote)) {
                sb.append(centerText(stringNote, 3))
            } else {
                sb.append(" . ")
            }

            // C. Il Capotasto
            sb.append("||")

            // D. I Tasti da 1 a 12
            val startIdx = semitones.indexOf(stringNote)
            for (fret in 1..12) {
                val currentNoteIdx = (startIdx + fret) % 12
                val currentNote = semitones[currentNoteIdx]

                if (scale.contains(currentNote)) {
                    // Se la nota è nella scala, mostrala
                    sb.append(centerText(currentNote, 5))
                } else {
                    // Altrimenti mostra la "corda" vuota
                    sb.append(" --- ")
                }
                sb.append("|") // Barra del tasto
            }
            sb.append("\n") // Fine della corda

            // E. Linea orizzontale della griglia (tranne dopo l'ultima corda)
            if (stringNote != tuning.first()) { // tuning.first() è la Low E (che qui è l'ultima stampata)
                sb.append("---+---++-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+\n")
            }
        }

        // Linea di chiusura inferiore
        sb.append("===+===++=====+=====+=====+=====+=====+=====+=====+=====+=====+=====+=====+=====+\n")

        return sb.toString()
    }

    // Funzione helper per centrare il testo nelle caselle della griglia
    private fun centerText(text: String, length: Int): String {
        if (text.length >= length) return text
        val padding = (length - text.length) / 2
        val sb = StringBuilder()
        repeat(padding) { sb.append(" ") }
        sb.append(text)
        while (sb.length < length) {
            sb.append(" ")
        }
        return sb.toString()
    }

    /* quest è la schermata vecchia funzionante (COMMENTATA PER RIFERIMENTO)
        private fun generateFretboardVisual(key: String): String {
            val majorScales = mapOf(
                "C" to listOf("C","D","E","F","G","A","B"),
                // ... (mappa vecchia) ...
            )

            val scale = majorScales[key] ?: majorScales["C"]!!
            val tuning = listOf("E","A","D","G","B","E")
            val semitones = listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")

            val builder = StringBuilder()
            builder.append("Note scala di $key:\n$scale\n\n")
            builder.append("   0  1  2  3  4  5  6  7  8  9 \n")

            for (stringNote in tuning.reversed()) {
                builder.append("$stringNote |")

                val startIdx = semitones.indexOf(stringNote)

                for (fret in 1..9) {
                    val currentNoteIdx = (startIdx + fret) % 12
                    val currentNote = semitones[currentNoteIdx]

                    if (scale.contains(currentNote)) {
                        builder.append(formatNote(currentNote))
                    } else {
                        builder.append("---")
                    }
                    builder.append("|")
                }
                builder.append("\n")
            }
            return builder.toString()
        }

        private fun formatNote(note: String): String {
            return if (note.length == 2) "$note " else " $note "
        }
    */

    override fun onDestroy() {
        super.onDestroy()
        stopLoop()
        voicePlayer?.release()
        soundPool.release()
    }
}