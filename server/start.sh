#!/bin/bash
# Script di avvio del server ToneSketch.
# La gestione della cartella cache (numba/matplotlib) è già gestita
# internamente da deploy.py, quindi qui non serve impostare nulla a mano.

echo "🚀 Avvio server ToneSketch (deploy.py)..."
python3 deploy.py
