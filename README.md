# ToneSketch 🎵

**ToneSketch** è un "arrangiatore personale" per Android: registri una
melodia canticchiata, l'app la invia a un backend che ne analizza BPM,
tonalità e accordi, e genera in locale un accompagnamento
(batteria + armonia) sincronizzato con la registrazione, editabile su un
sequencer step-by-step.

> Questo progetto è stato sviluppato come tesi di laurea triennale in
> Informatica presso il **DIBRIS – Dipartimento di Informatica,
> Bioingegneria, Robotica e Ingegneria dei Sistemi, Università di
> Genova**.
>
> **Titolo:** *Progettazione e sviluppo di un sequencer mobile guidato
> da analisi audio: integrazione di DSP remota e generazione
> armonico-ritmica*
> **Relatore:** Prof. Luca Verderame
> **Candidato:** Matteo Colombo
>
> 📑 Le slide della presentazione sono disponibili in [`docs/tesi-slides.pdf`](docs/tesi-slides.pdf).

## Come funziona

1. **Registri** una melodia canticchiata dall'app (modalità libera o "a
   tempo" con metronomo).
2. L'app **carica** la registrazione su un backend Python.
3. Il backend (FastAPI + [Librosa](https://librosa.org/)) **analizza**
   BPM, tonalità e una sequenza di accordi sincronizzata sui beat.
4. L'app **genera in locale** un accompagnamento (batteria + armonia)
   basato sull'analisi, mostrato su un sequencer a 16 step completamente
   editabile, con mixer dei volumi e una fretboard dinamica che si
   aggiorna in base alla tonalità rilevata.

## Architettura

Il progetto è diviso in due parti, ciascuna nella propria cartella:

```
ToneSketch/
├── app/       → App Android (Kotlin)
├── server/    → Backend di analisi audio (Python)
└── docs/      → Materiale della tesi (slide, ecc.)
```

### App Android (`app/`)

| Componente | Ruolo |
|---|---|
| `NewRecActivity` | Registrazione audio (`MediaRecorder`), modalità libera o a tempo con metronomo |
| `PlayActivity` | Gestione dei file registrati, upload al backend (OkHttp, multipart), parsing della risposta JSON |
| `ArrangerActivity` | Sequencer 16-step (kick/snare/hihat), generazione di progressioni armoniche (I–V–vi–IV ecc.), mixer volumi, fretboard dinamica |

### Backend (`server/`)

FastAPI + Librosa: riceve l'audio, lo converte con `ffmpeg`, stima BPM e
beat grid, rileva la tonalità globale (metodo Krumhansl-Schmuckler) e
calcola una sequenza di accordi sincronizzata sui beat. Istruzioni
dettagliate di installazione e avvio in [`server/README.md`](server/README.md).

## Setup rapido

**Backend:**
```bash
cd server
pip install -r requirements.txt
bash start.sh
```

**App Android:**
1. Apri la cartella `app/` in Android Studio.
2. In `PlayActivity.kt`, imposta `SERVER_URL` con l'indirizzo IP/dominio
   del tuo backend (vedi commento nel file).
3. Compila e installa su un dispositivo/emulatore.

## Stato del progetto

Progetto sviluppato a scopo di tesi/didattico. Non è pensato per un uso
in produzione così com'è (il backend, ad esempio, non ha autenticazione
né limiti sulla dimensione dei file caricati).

## Licenza

Distribuito sotto licenza [Unlicense](LICENSE) — pubblico dominio,
nessuna restrizione d'uso.
