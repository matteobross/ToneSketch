package it.progmob.tonesketch

import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// Piccola classe per memorizzare gli eventi (Tempo -> Accordo)
data class ChordEvent(val timeMs: Long, val name: String)

class ArrangerActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var txtKey: TextView
    private lateinit var txtBpm: TextView
    private lateinit var btnShowChords: Button // Il bottone per il Karaoke
    private lateinit var spinnerProgression: Spinner
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

    // Variabili Mixer
    private var volDrums = 0.8f
    private var volPiano = 0.8f
    private var volVoice = 1.0f

    // Sequencer
    private val drumGrid = Array(3) { BooleanArray(16) }
    private val sequencerButtons = ArrayList<ToggleButton>()

    // Loop Logic
    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    private var step = 0
    private var startTime: Long = 0
    private var totalSixteenthsPlayed: Long = 0

    // Dati Musicali
    private var bpm: Double = 120.0
    private var key = "C"

    // LISTA ACCORDI PER IL KARAOKE
    private val chordEvents = ArrayList<ChordEvent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_arranger)

        // Init UI
        txtKey = findViewById(R.id.txtKey)
        txtBpm = findViewById(R.id.txtBpm)
        btnShowChords = findViewById(R.id.btnShowChords) // Assicurati che questo ID esista nel tuo XML!
        spinnerProgression = findViewById(R.id.spinnerProgression)
        btnPlay = findViewById(R.id.btnPlay)
        btnStop = findViewById(R.id.btnStop)
        txtChordList = findViewById(R.id.txtChordList)
        btnFretboard = findViewById(R.id.btnFretboard)

        // Costruzione Sequencer
        try {
            buildSequencer()
        } catch (e: Exception) {
            Toast.makeText(this, "Errore Sequencer UI", Toast.LENGTH_SHORT).show()
        }

        // 1. Leggi i dati passati da PlayActivity
        val jsonString = intent.getStringExtra("analysis_json")
        val voicePath = intent.getStringExtra("voice_path")

        if (jsonString != null) {
            try {
                val json = JSONObject(jsonString)
                bpm = json.optDouble("bpm", 120.0)
                key = json.optString("key", "C")

                // --- CARICAMENTO DATI PER IL MONITOR KARAOKE ---
                if (json.has("chords")) {
                    chordEvents.clear()
                    val chordsArray = json.getJSONArray("chords")
                    for (i in 0 until chordsArray.length()) {
                        val obj = chordsArray.getJSONObject(i)
                        val timeSec = obj.getDouble("time")
                        val name = obj.getString("chord")
                        // Convertiamo in millisecondi per il confronto col player
                        chordEvents.add(ChordEvent((timeSec * 1000).toLong(), name))
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        txtKey.text = "Key: $key"
        txtBpm.text = String.format("BPM: %.1f", bpm)

        updateChordList(key)

        // Setup Spinner
        val progressions = listOf("Nessun Piano", "I - V - vi - IV", "ii - V - I", "vi - IV - I - V")
        spinnerProgression.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, progressions)
        spinnerProgression.setSelection(1)

        // 2. Carica Audio
        soundPool = SoundPool.Builder().setMaxStreams(10).build()
        loadSounds()

        if (voicePath != null) {
            val file = File(voicePath)
            if (file.exists()) {
                voicePlayer = MediaPlayer().apply {
                    setDataSource(voicePath)
                    isLooping = true // La voce va in loop insieme alla batteria
                    prepare()
                }
            }
        }

        // Listeners
        btnPlay.setOnClickListener { startLoop() }
        btnStop.setOnClickListener { stopLoop() }
        btnFretboard.setOnClickListener { showFretboard() }

        // --- QUI COLLEGHIAMO IL BOTTONE AL MONITOR LIVE ---
        btnShowChords.setOnClickListener {
            showKaraokeMonitor()
        }

        loadDrumPreset("pop")
        setupMixer()
        setupDrumStyleSpinner()
    }

    // --- FUNZIONE MONITOR LIVE (KARAOKE) ---
    private fun showKaraokeMonitor() {
        val dialog = BottomSheetDialog(this)
        // Usa il layout che mi hai mandato prima: bottom_karaoke.xml
        val view = layoutInflater.inflate(R.layout.bottom_karaoke, null)
        dialog.setContentView(view)

        val lblCurrent = view.findViewById<TextView>(R.id.lblCurrentChord)
        val lblNext = view.findViewById<TextView>(R.id.lblNextChord)
        val btnClose = view.findViewById<Button>(R.id.btnCloseKaraoke)

        // Handler per aggiornare il testo mentre la musica suona
        val monitorHandler = Handler(Looper.getMainLooper())
        val monitorRunnable = object : Runnable {
            override fun run() {
                // Se chiudi la finestra, smettiamo di aggiornare il testo
                if (!dialog.isShowing) return

                try {
                    // Chiediamo al player principale a che punto è
                    val currentPos = if (voicePlayer != null && voicePlayer!!.isPlaying) {
                        voicePlayer!!.currentPosition.toLong()
                    } else {
                        0L
                    }

                    // Logica trova accordo
                    val currentEvent = chordEvents.lastOrNull { it.timeMs <= currentPos }
                    val currentIndex = chordEvents.indexOf(currentEvent)
                    val nextEvent = if (currentIndex + 1 < chordEvents.size) chordEvents[currentIndex + 1] else null

                    lblCurrent.text = currentEvent?.name ?: "..."
                    lblNext.text = nextEvent?.name ?: "-"

                } catch (e: Exception) { }

                // Aggiorna ogni 50ms (molto fluido)
                monitorHandler.postDelayed(this, 50)
            }
        }

        // Avvia l'aggiornamento
        monitorHandler.post(monitorRunnable)

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun setupDrumStyleSpinner() {
        val spinnerDrumStyle = findViewById<Spinner>(R.id.spinnerDrumStyle)
        val styles = listOf("Pop", "Rock", "Reggae", "Svuota")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, styles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDrumStyle.adapter = adapter

        spinnerDrumStyle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedStyle = styles[position].lowercase()
                val styleCommand = if (selectedStyle == "svuota") "mute" else selectedStyle
                loadDrumPreset(styleCommand)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupMixer() {
        val seekDrums = findViewById<SeekBar>(R.id.seekVolDrums)
        val seekPiano = findViewById<SeekBar>(R.id.seekVolPiano)
        val seekVoice = findViewById<SeekBar>(R.id.seekVolVoice)

        seekDrums?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                volDrums = progress / 100f
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekPiano?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                volPiano = progress / 100f
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekVoice?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                volVoice = progress / 100f
                try {
                    voicePlayer?.setVolume(volVoice, volVoice)
                } catch (e: Exception) {}
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun buildSequencer() {
        val labelsContainer = findViewById<LinearLayout>(R.id.drumLabelsContainer)
        val gridContainer = findViewById<LinearLayout>(R.id.sequencerGrid)
        if (labelsContainer == null || gridContainer == null) return

        val instruments = listOf("Kick", "Snare", "HiHat")
        labelsContainer.removeAllViews()
        gridContainer.removeAllViews()
        sequencerButtons.clear()

        for (row in 0..2) {
            val label = TextView(this)
            label.text = instruments[row]
            label.textSize = 14f
            label.height = 100
            label.gravity = android.view.Gravity.CENTER_VERTICAL
            labelsContainer.addView(label)

            val rowLayout = LinearLayout(this)
            rowLayout.orientation = LinearLayout.HORIZONTAL

            for (col in 0..15) {
                val btn = ToggleButton(this)
                btn.textOn = ""
                btn.textOff = ""
                btn.text = ""
                val params = LinearLayout.LayoutParams(100, 100)
                params.setMargins(2, 2, 2, 2)
                btn.layoutParams = params
                val baseColor = if (col % 4 == 0) 0xFFCCCCCC.toInt() else 0xFFEEEEEE.toInt()
                btn.setBackgroundColor(baseColor)

                btn.setOnCheckedChangeListener { _, isChecked ->
                    drumGrid[row][col] = isChecked
                    if (isChecked) btn.setBackgroundColor(android.graphics.Color.CYAN)
                    else btn.setBackgroundColor(baseColor)
                }
                sequencerButtons.add(btn)
                rowLayout.addView(btn)
            }
            gridContainer.addView(rowLayout)
        }
    }

    private fun loadDrumPreset(style: String) {
        for (btn in sequencerButtons) btn.isChecked = false
        fun t(row: Int, col: Int) {
            val idx = (row * 16) + col
            if (idx < sequencerButtons.size) sequencerButtons[idx].isChecked = true
        }
        when (style) {
            "pop" -> { t(0, 0); t(0, 8); t(1, 4); t(1, 12); for (i in 0..15 step 2) t(2, i) }
            "rock" -> { t(0, 0); t(0, 10); t(1, 4); t(1, 12); for (i in 0..15 step 4) t(2, i) }
            "reggae" -> { t(0, 8); t(1, 8); for (i in 2..15 step 4) t(2, i) }
        }
    }

    private fun loadSounds() {
        kickId = soundPool.load(this, R.raw.kick, 1)
        snareId = soundPool.load(this, R.raw.snare, 1)
        hihatId = soundPool.load(this, R.raw.hihat, 1)
        val noteNames = listOf("c","csharp","d","dsharp","e","f","fsharp","g","gsharp","a","asharp","b")
        val displayNames = listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        for (i in noteNames.indices) {
            val resId = resources.getIdentifier("piano_${noteNames[i]}", "raw", packageName)
            if (resId != 0) pianoNotes[displayNames[i]] = soundPool.load(this, resId, 1)
        }
    }

    private fun startLoop() {
        if (isPlaying) return
        isPlaying = true
        step = 0
        totalSixteenthsPlayed = 0
        voicePlayer?.seekTo(0)
        voicePlayer?.setVolume(volVoice, volVoice)
        voicePlayer?.start()
        startTime = System.currentTimeMillis()
        handler.post(loopRunnable)
    }

    private fun stopLoop() {
        isPlaying = false
        handler.removeCallbacks(loopRunnable)
        if (voicePlayer?.isPlaying == true) voicePlayer?.pause()
    }

    private val loopRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying) return
            playDrums(step)
            playHarmony(step)
            step = (step + 1) % 16
            totalSixteenthsPlayed++
            val msPerBeat = 60000.0 / bpm
            val stepDuration = msPerBeat / 4.0
            val nextExpectedTime = startTime + (stepDuration * totalSixteenthsPlayed).toLong()
            val now = System.currentTimeMillis()
            var delay = nextExpectedTime - now
            if (delay < 0) delay = 0
            handler.postDelayed(this, delay)
        }
    }

    private fun playDrums(step: Int) {
        if (drumGrid[0][step]) soundPool.play(kickId, volDrums, volDrums, 1, 0, 1f)
        if (drumGrid[1][step]) soundPool.play(snareId, volDrums, volDrums, 1, 0, 1f)
        if (drumGrid[2][step]) soundPool.play(hihatId, volDrums * 0.6f, volDrums * 0.6f, 1, 0, 1f)
    }

    private fun playHarmony(step: Int) {
        val selectedIdx = spinnerProgression.selectedItemPosition
        if (selectedIdx == 0) return
        if (step % 4 != 0) return
        val progression = when (selectedIdx) {
            1 -> listOf("I", "V", "vi", "IV")
            2 -> listOf("ii", "V", "I", "I")
            3 -> listOf("vi", "IV", "I", "V")
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
            pianoNotes[noteName]?.let { soundPool.play(it, volPiano, volPiano, 1, 0, 1f) }
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
            Toast.makeText(this, "Errore Fretboard", Toast.LENGTH_LONG).show()
        }
    }

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
        val tuning = listOf("E","A","D","G","B","E")
        val semitones = listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        val sb = StringBuilder()
        sb.append("Note scala di $key:\n$scale\n\n")
        sb.append("|   | 0 ||")
        for (i in 1..12) { sb.append(centerText(i.toString(), 5)); sb.append("|") }
        sb.append("\n+===+===++=====+=====+=====+=====+=====+=====+=====+=====+=====+=====+=====+=====+\n")
        val reversedTuning = tuning.reversed()
        reversedTuning.forEachIndexed { index, stringNote ->
            sb.append("|"); sb.append(centerText(stringNote, 3)); sb.append("|")
            if (scale.contains(stringNote)) sb.append(centerText(stringNote, 3)) else sb.append(" . ")
            sb.append("||")
            val startIdx = semitones.indexOf(stringNote)
            for (fret in 1..12) {
                val currentNoteIdx = (startIdx + fret) % 12
                val currentNote = semitones[currentNoteIdx]
                if (scale.contains(currentNote)) sb.append(centerText(currentNote, 5)) else sb.append(" --- ")
                sb.append("|")
            }
            sb.append("\n")
            if (index < reversedTuning.size - 1) sb.append("+---+---++-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+\n")
        }
        sb.append("+===+===++=====+=====+=====+=====+=====+=====+=====+=====+=====+=====+=====+=====+\n")
        return sb.toString()
    }

    private fun updateChordList(key: String) {
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
        val suffixes = listOf("", "m", "m", "", "", "m", "dim")
        val chords = StringBuilder()
        for (i in scale.indices) {
            chords.append(scale[i]); chords.append(suffixes[i])
            if (i < scale.size - 1) chords.append(" - ")
        }
        txtChordList.text = "Accordi in $key:\n$chords"
    }

    private fun centerText(text: String, length: Int): String {
        if (text.length >= length) return text
        val padding = (length - text.length) / 2
        val sb = StringBuilder()
        repeat(padding) { sb.append(" ") }
        sb.append(text)
        while (sb.length < length) sb.append(" ")
        return sb.toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLoop()
        voicePlayer?.release()
        soundPool.release()
    }
}