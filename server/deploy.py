import os
import sys

# --- CONFIGURAZIONE AMBIENTE BLINDATA ---
# Reindirizza le cartelle di cache di numba/matplotlib dentro la cartella
# del progetto, utile su server con permessi limitati sulla home (es. VM
# cloud free-tier) dove /root o /home/<user> potrebbero non essere scrivibili.
CURRENT_DIR = os.getcwd()
CACHE_DIR = os.path.join(CURRENT_DIR, "local_cache")
os.makedirs(CACHE_DIR, exist_ok=True)

os.environ["HOME"] = CURRENT_DIR
os.environ["XDG_CACHE_HOME"] = CACHE_DIR
os.environ["NUMBA_CACHE_DIR"] = CACHE_DIR
os.environ["MPLCONFIGDIR"] = CACHE_DIR

print(f"🔧 CONFIGURAZIONE: Cache impostata su {CACHE_DIR}")

from fastapi import FastAPI, UploadFile, File
from fastapi.responses import JSONResponse
import uvicorn
import tempfile
import subprocess
import librosa
import numpy as np

app = FastAPI(title="ToneSketch Server - BPM, Key & Chords Analyzer")

# --- FUNZIONE AUSILIARIA PER GLI ACCORDI (Dinamico) ---
def get_chord_from_frame(chroma_col):
    """Indovina l'accordo analizzando le note in un istante specifico"""
    maj_template = [1, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0]
    min_template = [1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0]
    note_names = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B']

    max_score = -1
    best_chord = "N"

    for i in range(12):
        # Test Maggiore
        current_maj = np.roll(maj_template, i)
        score_maj = np.dot(chroma_col, current_maj)
        if score_maj > max_score:
            max_score = score_maj
            best_chord = f"{note_names[i]}:maj"

        # Test Minore
        current_min = np.roll(min_template, i)
        score_min = np.dot(chroma_col, current_min)
        if score_min > max_score:
            max_score = score_min
            best_chord = f"{note_names[i]}:min"

    return best_chord

# --- CERVELLO PRINCIPALE ---
def analyze_logic(file_path):
    print(f"--- Inizio Analisi Ibrida: {file_path} ---")
    try:
        # Carica audio
        y, sr = librosa.load(file_path, sr=22050, mono=True)

        # --- 1. CALCOLO KEY GLOBALE (IL METODO VECCHIO E SICURO) ---
        # Questo guarda TUTTA la canzone insieme per decidere la tonalità
        chroma_global = librosa.feature.chroma_cqt(y=y, sr=sr)
        chroma_mean = np.mean(chroma_global, axis=1)

        # Template Krumhansl-Schmuckler
        major_template = np.array([6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88])
        note_names = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B']

        correlations = []
        for i in range(12):
            corr = np.corrcoef(np.roll(major_template, i), chroma_mean)[0, 1]
            correlations.append((corr, note_names[i]))

        global_key = max(correlations, key=lambda x: x[0])[1]

        # --- 2. CALCOLO BPM ---
        tempo, beat_frames = librosa.beat.beat_track(y=y, sr=sr)
        if isinstance(tempo, (list, np.ndarray)):
            bpm_val = float(np.mean(tempo))
        else:
            bpm_val = float(tempo)

        if bpm_val < 60: bpm_val *= 2
        elif bpm_val > 180: bpm_val /= 2

        beat_times = librosa.frames_to_time(beat_frames, sr=sr)

        # --- 3. CALCOLO ACCORDI DINAMICI (IL METODO NUOVO) ---
        # Separiamo l'armonia per leggere meglio gli accordi
        y_harmonic, y_percussive = librosa.effects.hpss(y)
        chroma_harmonic = librosa.feature.chroma_cqt(y=y_harmonic, sr=sr)

        # Sincronizziamo sui beat
        chroma_sync = librosa.util.sync(chroma_harmonic, beat_frames, aggregate=np.median)

        chord_sequence = []
        for i, col in enumerate(chroma_sync.T):
            chord_name = get_chord_from_frame(col)
            if i < len(beat_times):
                chord_sequence.append({
                    "time": round(beat_times[i], 3),
                    "chord": chord_name
                })

        print(f"✅ Analisi OK: Key {global_key} | BPM {bpm_val} | Chords {len(chord_sequence)}")

        return {
            "bpm": round(bpm_val, 1),
            "key": global_key,
            "beat_times": beat_times.tolist(),
            "chords": chord_sequence
        }

    except Exception as e:
        print(f"❌ Errore Analisi: {e}")
        raise e

# --- SERVER API ---
@app.post("/analyze")
async def analyze_endpoint(file: UploadFile = File(...)):
    suffix = os.path.splitext(file.filename)[1] or ".tmp"
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        tmp.write(await file.read())
        input_path = tmp.name

    wav_path = input_path + "_clean.wav"

    try:
        subprocess.run([
            "ffmpeg", "-y", "-i", input_path,
            "-ar", "22050", "-ac", "1",
            wav_path
        ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

        result = analyze_logic(wav_path)
        return result

    except Exception as e:
        print(f"🔥 ERRORE SERVER: {e}")
        return JSONResponse(status_code=500, content={"error": str(e)})

    finally:
        try:
            if os.path.exists(input_path): os.remove(input_path)
            if os.path.exists(wav_path): os.remove(wav_path)
        except:
            pass

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
