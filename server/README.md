# ToneSketch Server

Backend Python (FastAPI + Librosa) che riceve una registrazione audio
dall'app Android ToneSketch e restituisce BPM, tonalità e una sequenza di
accordi sincronizzata sui beat.

## Requisiti

- Python 3.8+
- [ffmpeg](https://ffmpeg.org/) installato e disponibile nel `PATH`
  (su Ubuntu: `sudo apt install ffmpeg`)

## Installazione

```bash
pip install -r requirements.txt
```

## Avvio

```bash
bash start.sh
```

oppure direttamente:

```bash
python3 deploy.py
```

Il server parte sulla porta **8000** e ascolta su tutte le interfacce
(`0.0.0.0`), quindi è raggiungibile anche da altri dispositivi sulla stessa
rete (o da internet, se il server ha un IP pubblico e la porta è aperta).

## Endpoint

### `POST /analyze`

Riceve un file audio in multipart form-data (campo `file`) e restituisce:

```json
{
  "bpm": 118.5,
  "key": "G",
  "beat_times": [0.12, 0.58, 1.04, ...],
  "chords": [
    { "time": 0.12, "chord": "G:maj" },
    { "time": 1.04, "chord": "D:maj" }
  ]
}
```

## Collegare l'app Android

Nell'app, in `PlayActivity.kt`, imposta `SERVER_URL` con l'indirizzo di
questo server (IP pubblico della macchina + porta), ad esempio:

```kotlin
private const val SERVER_URL = "http://<TUO_IP>"
private const val ANALYZE_ENDPOINT = "$SERVER_URL:8000/analyze"
```
