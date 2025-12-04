#!/usr/bin/env python3
"""
Convert melcnn.pt checkpoints into ONNX + TFLite artifacts for on-device inference.
"""

from __future__ import annotations

import argparse
import shutil
import tempfile
from pathlib import Path

import onnx
import torch
from model import MelCNN


def load_model(checkpoint: Path) -> MelCNN:
    model = MelCNN()
    state = torch.load(checkpoint, map_location="cpu")
    model.load_state_dict(state)
    model.eval()
    return model


def export_onnx(model: MelCNN, onnx_path: Path, mel_bins: int, frames: int) -> None:
    dummy = torch.randn(1, 1, mel_bins, frames)
    dynamic_axes = {"mel": {0: "batch", 3: "frames"}, "logits": {0: "batch"}}
    torch.onnx.export(
        model,
        dummy,
        onnx_path,
        input_names=["mel"],
        output_names=["logits"],
        opset_version=13,
        dynamic_axes=dynamic_axes,
    )


def export_tflite(onnx_path: Path, tflite_path: Path, quantize: bool) -> None:
    from onnx_tf.backend import prepare  # type: ignore
    import tensorflow as tf

    with tempfile.TemporaryDirectory() as tmp:
        saved_model_dir = Path(tmp) / "saved_model"
        tf_rep = prepare(onnx.load(str(onnx_path)))
        tf_rep.export_graph(str(saved_model_dir))
        converter = tf.lite.TFLiteConverter.from_saved_model(str(saved_model_dir))
        if quantize:
            converter.optimizations = [tf.lite.Optimize.DEFAULT]
        tflite_bytes = converter.convert()
        tflite_path.write_bytes(tflite_bytes)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkpoint", type=Path, default=Path("../../ml/model/melcnn.pt"))
    parser.add_argument("--onnx-path", type=Path, default=Path("../../ml/model/melcnn.onnx"))
    parser.add_argument("--tflite-path", type=Path, default=Path("../../ml/model/melcnn.tflite"))
    parser.add_argument("--mel-bins", type=int, default=64)
    parser.add_argument("--frames", type=int, default=200, help="Time frames used for dummy export input")
    parser.add_argument("--quantize", action="store_true", help="Enable default int8 quantization")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    model = load_model(args.checkpoint)
    export_onnx(model, args.onnx_path, mel_bins=args.mel_bins, frames=args.frames)
    export_tflite(args.onnx_path, args.tflite_path, quantize=args.quantize)
    print(f"ONNX saved to {args.onnx_path}")
    print(f"TFLite saved to {args.tflite_path}")


if __name__ == "__main__":
    main()
