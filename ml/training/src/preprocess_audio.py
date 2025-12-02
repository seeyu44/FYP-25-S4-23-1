import argparse
from pathlib import Path

import numpy as np
import torch
import torchaudio
from torchaudio.functional import resample
from torchaudio.transforms import MelSpectrogram, AmplitudeToDB

SAMPLE_RATE = 16000
CLIP_SECONDS = 3
TARGET_LEN = SAMPLE_RATE * CLIP_SECONDS

mel_transform = MelSpectrogram(
    sample_rate=SAMPLE_RATE,
    n_fft=1024,
    hop_length=256,
    n_mels=64,
)
amp_to_db = AmplitudeToDB()

def simple_vad(waveform, threshold_db=-40.0):
    energy = 20 * torch.log10(torch.clamp(torch.abs(waveform), min=1e-5))
    mask = energy > threshold_db
    if mask.any():
        indices = mask.nonzero(as_tuple=True)[1]
        return waveform[:, indices.min(): indices.max() + 1]
    return waveform

def load_and_process(path: Path):
    wav, sr = torchaudio.load(path)
    if sr != SAMPLE_RATE:
        wav = resample(wav, orig_freq=sr, new_freq=SAMPLE_RATE)
    if wav.shape[0] > 1:
        wav = torch.mean(wav, dim=0, keepdim=True)

    wav = simple_vad(wav)

    # Pad or crop to fixed length
    if wav.shape[1] < TARGET_LEN:
        pad = TARGET_LEN - wav.shape[1]
        wav = torch.nn.functional.pad(wav, (0, pad))
    else:
        wav = wav[:, :TARGET_LEN]

    mel = mel_transform(wav)
    mel_db = amp_to_db(mel)
    mel_db = (mel_db - mel_db.mean()) / (mel_db.std() + 1e-5)
    return mel_db

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=Path("data/raw"))
    parser.add_argument("--output", type=Path, default=Path("data/processed"))
    args = parser.parse_args()

    args.output.mkdir(parents=True, exist_ok=True)
    for label in ("real", "fake"):
        out_dir = args.output / label
        out_dir.mkdir(parents=True, exist_ok=True)
        for wav in (args.input / label).glob("*.wav"):
            mel = load_and_process(wav)
            torch.save(mel, out_dir / f"{wav.stem}.pt")
            print(f"Saved {out_dir / (wav.stem + '.pt')}")

if __name__ == "__main__":
    main()