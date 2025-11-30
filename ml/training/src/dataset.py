from pathlib import Path

import soundfile as sf
import torch
import torchaudio
import torch.nn.functional as F
from torchaudio.functional import resample
from torch.utils.data import Dataset

SAMPLE_RATE = 16000
CLIP_SECONDS = 3
TARGET_LEN = SAMPLE_RATE * CLIP_SECONDS


class DeepfakeDataset(Dataset):
    def __init__(self, root: Path, sample_rate: int = SAMPLE_RATE, *, items=None, augment: bool = False):
        self.items = items if items is not None else []
        if not self.items:
            for label_name, target in (("real", 0), ("fake", 1)):
                for wav in (root / label_name).glob("*.wav"):
                    self.items.append((wav, target))
        self.sample_rate = sample_rate
        self.augment = augment
        self.melspec = torchaudio.transforms.MelSpectrogram(
            sample_rate=sample_rate, n_fft=1024, hop_length=256, n_mels=64
        )
        self.to_db = torchaudio.transforms.AmplitudeToDB()

    def __len__(self):
        return len(self.items)

    def __getitem__(self, idx):
        path, label = self.items[idx]
        wav_np, sr = sf.read(path, always_2d=True)      # (frames, channels)
        wav = torch.from_numpy(wav_np.T).float()        # (channels, frames)
        if wav.shape[0] > 1:
            wav = wav.mean(dim=0, keepdim=True)         # mono

        if sr != self.sample_rate:
            wav = resample(wav, orig_freq=sr, new_freq=self.sample_rate)

        # Length handling: random crop for training, center crop for eval
        if wav.shape[1] < TARGET_LEN:
            pad = TARGET_LEN - wav.shape[1]
            wav = F.pad(wav, (0, pad))
        else:
            if self.augment:
                start = torch.randint(0, wav.shape[1] - TARGET_LEN + 1, (1,)).item()
            else:
                start = max((wav.shape[1] - TARGET_LEN) // 2, 0)
            wav = wav[:, start:start + TARGET_LEN]

        if self.augment:
            gain = torch.empty(1).uniform_(0.8, 1.2).item()
            wav = wav * gain
            noise = torch.randn_like(wav) * 0.003
            wav = wav + noise

        mel = self.melspec(wav)
        mel_db = self.to_db(mel)
        mel_db = (mel_db - mel_db.mean()) / (mel_db.std() + 1e-5)
        return mel_db, label
