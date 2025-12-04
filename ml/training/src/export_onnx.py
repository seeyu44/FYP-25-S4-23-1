import argparse, torch
from model import MelCNN
from pathlib import Path

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkpoint", type=Path, default=Path("../../ml/model/melcnn.pt"))
    parser.add_argument("--out", type=Path, default=Path("../../ml/model/model.onnx"))
    args = parser.parse_args()

    model = MelCNN()
    model.load_state_dict(torch.load(args.checkpoint, map_location="cpu"))
    model.eval()

    dummy = torch.randn(1, 1, 64, 300)  # adjust time dimension to your mel length
    torch.onnx.export(
        model, dummy, args.out,
        input_names=["mel"], output_names=["logits"],
        dynamic_axes={"mel": {3: "time"}, "logits": {0: "batch"}},
        opset_version=13,
    )
    print(f"Exported {args.out}")

if __name__ == "__main__":
    main()