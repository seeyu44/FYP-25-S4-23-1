## ML Training Pipeline

1. **Collect data**
   - Store public/consented recordings in `data/raw/real/` and generated voices in `data/raw/deepfake/`.
   - Keep metadata CSV with speaker id, source, generation model, transcript.

2. **Preprocess** (`src/preprocess/`)
   - Resample to 16 kHz mono, normalize loudness.
   - Run voice activity detection; split into 2–3 s chunks.
   - Export log-mel tensors (e.g., 40×300) into `data/processed/` plus labels file.

3. **Train** (`src/train.py`)
   - Configure experiment via CLI flags (`--data`, `--epochs`, `--model mobilenet` etc.).
   - Use speaker-disjoint train/val/test splits to avoid leakage.
   - Record metrics (ROC-AUC, EER) under `experiments/<timestamp>/`.

4. **Export**
   - Convert best checkpoint to TFLite/ONNX using `export_tflite.py` or `export_onnx.py` (to add under `src/`).
   - Drop artifacts (`model.tflite`, `labels.json`, `config.json`) inside `../../ml/model/` and run `scripts/sync_model.ps1` to copy into the Android assets.

5. **On-device validation**
   - Use the new call lab to place/receive calls.
   - Monitor detection logs (notifications + dashboard) to confirm threshold.

> Tip: keep models <5 MB and prefer int8 quantization so inference works in real time on-device.
