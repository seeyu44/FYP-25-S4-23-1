#!/usr/bin/env python3
"""
Stage ASVspoof 5 metadata into data/raw/{real,fake} as 16 kHz mono WAV files.
"""

from __future__ import annotations

import argparse
import logging
from pathlib import Path
from typing import Dict, Iterable, List

import torchaudio
from torchaudio.functional import resample
from tqdm import tqdm

LABEL_MAP = {
    "bonafide": "real",
    "genuine": "real",
    "real": "real",
    "spoof": "fake",
    "fake": "fake",
}


def parse_metadata(path: Path) -> List[Dict[str, str]]:
    entries: List[Dict[str, str]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split()
            utt_id = next((token for token in parts if token.startswith("T_")), parts[0])
            label = parts[-1].lower()
            entries.append({"utt_id": utt_id, "label": label})
    return entries


def convert_file(src: Path, dst: Path, sample_rate: int) -> None:
    waveform, sr = torchaudio.load(src)
    if waveform.shape[0] > 1:
        waveform = waveform.mean(dim=0, keepdim=True)
    if sr != sample_rate:
        waveform = resample(waveform, orig_freq=sr, new_freq=sample_rate)
    dst.parent.mkdir(parents=True, exist_ok=True)
    torchaudio.save(str(dst), waveform, sample_rate, encoding="PCM_S", bits_per_sample=16)


def stage_subset(
    metadata_path: Path,
    flac_root: Path,
    output_root: Path,
    subset: str,
    sample_rate: int,
    extension: str,
    limit: int | None,
) -> Dict[str, int]:
    stats = {"real": 0, "fake": 0, "skipped": 0}
    entries = parse_metadata(metadata_path)
    target_dir = output_root / subset
    for entry in tqdm(entries, desc=f"Staging {subset}"):
        label_token = entry["label"]
        label = LABEL_MAP.get(label_token)
        if label is None:
            logging.warning("Unknown label %s (line skipped)", label_token)
            stats["skipped"] += 1
            continue
        src = flac_root / f"{entry['utt_id']}{extension}"
        if not src.exists():
            logging.warning("Missing file %s", src)
            stats["skipped"] += 1
            continue
        dst = target_dir / label / f"{entry['utt_id']}.wav"
        convert_file(src, dst, sample_rate)
        stats[label] += 1
        if limit and stats["real"] + stats["fake"] >= limit:
            break
    return stats


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--metadata", type=Path, required=True, help="ASVspoof metadata txt")
    parser.add_argument("--flac-root", type=Path, required=True, help="Directory containing FLAC files")
    parser.add_argument("--output", type=Path, default=Path("data/raw"), help="Root for staged WAVs")
    parser.add_argument("--subset", default="train", help="Name of the split (train/dev/test)")
    parser.add_argument("--sample-rate", type=int, default=16000)
    parser.add_argument("--extension", default=".flac")
    parser.add_argument("--limit", type=int, default=None, help="Optional cap on number of files")
    parser.add_argument("--log-level", default="INFO")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    logging.basicConfig(level=getattr(logging, args.log_level.upper(), logging.INFO))
    stats = stage_subset(
        metadata_path=args.metadata,
        flac_root=args.flac_root,
        output_root=args.output,
        subset=args.subset,
        sample_rate=args.sample_rate,
        extension=args.extension,
        limit=args.limit,
    )
    logging.info("Staged %s subset: %s", args.subset, stats)


if __name__ == "__main__":
    main()
