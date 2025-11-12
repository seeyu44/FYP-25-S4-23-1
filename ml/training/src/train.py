import argparse
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", type=Path, default=Path("data/processed"))
    parser.add_argument("--out", type=Path, default=Path("../../ml/model"))
    args = parser.parse_args()

    args.out.mkdir(parents=True, exist_ok=True)
    # TODO: implement training, export to TFLite/ONNX
    (args.out / "version.txt").write_text("0.0.1\n")
    print("Training stub complete; wrote version.txt")


if __name__ == "__main__":
    main()

