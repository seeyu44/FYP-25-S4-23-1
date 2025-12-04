#!/usr/bin/env python3
"""
End-to-end utility to (1) stage ASVspoof audio, (2) train MelCNN, (3) export TFLite.
"""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

from split_asvspoof import stage_subset

REPO_ROOT = Path(__file__).resolve().parents[2]


def run_python(script: Path, extra_args: list[str]) -> None:
    cmd = [sys.executable, str(script), *extra_args]
    print(f"[run] {' '.join(cmd)}")
    subprocess.run(cmd, cwd=str(REPO_ROOT), check=True)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--flac-root", type=Path, required=True)
    parser.add_argument("--subset", default="train")
    parser.add_argument("--data-root", type=Path, default=Path("ml/training/data/raw"))
    parser.add_argument("--sample-rate", type=int, default=16000)
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--epochs", type=int, default=25)
    parser.add_argument("--batch", type=int, default=32)
    parser.add_argument("--lr", type=float, default=1e-3)
    parser.add_argument("--model-dir", type=Path, default=Path("ml/model"))
    parser.add_argument("--quantize", action="store_true")
    parser.add_argument("--skip-stage", action="store_true")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    data_root = REPO_ROOT / args.data_root
    if not args.skip_stage:
        stats = stage_subset(
            metadata_path=REPO_ROOT / args.metadata,
            flac_root=REPO_ROOT / args.flac_root,
            output_root=data_root.parent,
            subset=args.subset,
            sample_rate=args.sample_rate,
            extension=".flac",
            limit=args.limit,
        )
        print(f"Staged audio: {stats}")
    train_script = REPO_ROOT / "ml/training/src/train.py"
    run_python(
        train_script,
        [
            "--data",
            str(data_root / args.subset),
            "--epochs",
            str(args.epochs),
            "--batch",
            str(args.batch),
            "--lr",
            str(args.lr),
            "--out",
            str(REPO_ROOT / args.model_dir),
        ],
    )
    export_script = REPO_ROOT / "ml/training/src/export_tflite.py"
    run_python(
        export_script,
        [
            "--checkpoint",
            str(REPO_ROOT / args.model_dir / "melcnn.pt"),
            "--onnx-path",
            str(REPO_ROOT / args.model_dir / "melcnn.onnx"),
            "--tflite-path",
            str(REPO_ROOT / args.model_dir / "melcnn.tflite"),
            "--quantize" if args.quantize else "",
        ],
    )


if __name__ == "__main__":
    main()
