import argparse
from pathlib import Path

import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import DataLoader, random_split, WeightedRandomSampler
from tqdm import tqdm
import torchaudio

from dataset import DeepfakeDataset
from model import MelCNN


class FocalLoss(nn.Module):
    """Binary focal loss to focus training on harder examples."""
    def __init__(self, alpha: float = 0.25, gamma: float = 2.0):
        super().__init__()
        self.alpha = alpha
        self.gamma = gamma

    def forward(self, logits, targets):
        bce = F.binary_cross_entropy_with_logits(logits, targets, reduction="none")
        pt = torch.exp(-bce)
        loss = self.alpha * (1 - pt) ** self.gamma * bce
        return loss.mean()


def evaluate(model, loader, device, epoch, total_epochs, criterion):
    model.eval()
    correct = total = 0
    loss_sum = 0.0
    with torch.no_grad():
        val_iter = tqdm(loader, desc=f"Epoch {epoch}/{total_epochs} [val]", leave=False)
        for mel, label in val_iter:
            mel, label = mel.to(device), label.to(device)
            logits = model(mel)
            loss = criterion(logits, label.float())
            loss_sum += loss.item() * label.size(0)
            preds = (torch.sigmoid(logits) > 0.5).long()
            correct += (preds == label.long()).sum().item()
            total += label.size(0)
    avg_loss = loss_sum / max(total, 1)
    avg_acc = correct / max(total, 1)
    return avg_loss, avg_acc


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--train-data", type=Path, default=Path("data/raw/train"))
    parser.add_argument("--val-data", type=Path, default=Path("data/raw/dev"))
    parser.add_argument("--val-split", type=float, default=0.2)
    parser.add_argument("--epochs", type=int, default=30)
    parser.add_argument("--batch", type=int, default=64)
    parser.add_argument("--lr", type=float, default=2e-5)
    parser.add_argument("--weight-decay", type=float, default=1e-3)
    parser.add_argument("--patience", type=int, default=12)
    parser.add_argument("--label-smoothing", type=float, default=0.0)
    parser.add_argument("--loss", choices=["bce", "focal"], default="focal")
    parser.add_argument("--focal-gamma", type=float, default=2.0)
    parser.add_argument("--out", type=Path, default=Path("../../ml/model"))
    parser.add_argument("--resume", type=Path, default=None, help="Checkpoint to resume from")
    parser.add_argument("--use-specaugment", action="store_true", help="Apply SpecAugment during training")
    args = parser.parse_args()

    train_dataset = DeepfakeDataset(args.train_data, augment=True)

    # Class counts and sampler for imbalance
    pos = sum(1 for _, lbl in train_dataset.items if lbl == 1)
    neg = len(train_dataset) - pos
    sample_weights = torch.tensor([neg if lbl == 1 else pos for _, lbl in train_dataset.items], dtype=torch.double)
    sampler = WeightedRandomSampler(sample_weights, num_samples=len(sample_weights), replacement=True)
    alpha = neg / max(pos + neg, 1)

    if args.val_data and args.val_data.exists():
        print(f"Using validation data at {args.val_data}")
        val_dataset = DeepfakeDataset(args.val_data)
    else:
        val_ratio = min(max(args.val_split, 0.01), 0.5)
        val_len = max(int(len(train_dataset) * val_ratio), 1)
        train_len = len(train_dataset) - val_len
        if train_len <= 0:
            raise ValueError("Training dataset too small for requested validation split")
        train_dataset, val_dataset = random_split(train_dataset, [train_len, val_len])
        print(f"No explicit validation data found; using random split ({val_len} samples).")

    train_loader = DataLoader(train_dataset, batch_size=args.batch, sampler=sampler)
    val_loader = DataLoader(val_dataset, batch_size=args.batch)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = MelCNN().to(device)
    if args.resume and args.resume.exists():
        print(f"Resuming from {args.resume}")
        state = torch.load(args.resume, map_location=device)
        model.load_state_dict(state)

    if args.loss == "focal":
        criterion = FocalLoss(alpha=alpha, gamma=args.focal_gamma)
    else:
        criterion = nn.BCEWithLogitsLoss(pos_weight=torch.tensor([1.0], device=device))
    optim = torch.optim.Adam(model.parameters(), lr=args.lr, weight_decay=args.weight_decay)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optim, T_max=args.epochs)

    spec_aug = None
    if args.use_specaugment:
        spec_aug = nn.Sequential(
            torchaudio.transforms.FrequencyMasking(freq_mask_param=8),
            torchaudio.transforms.TimeMasking(time_mask_param=12),
        ).to(device)

    args.out.mkdir(parents=True, exist_ok=True)
    best_val_loss = float("inf")
    epochs_no_improve = 0

    for epoch in range(1, args.epochs + 1):
        model.train()
        train_loss_sum = 0.0
        train_total = 0
        train_iter = tqdm(train_loader, desc=f"Epoch {epoch}/{args.epochs} [train]", leave=False)
        for mel, label in train_iter:
            mel, label = mel.to(device), label.float().to(device)
            if spec_aug:
                mel = spec_aug(mel)
            optim.zero_grad()
            logits = model(mel)

            if args.label_smoothing > 0:
                label = label * (1 - args.label_smoothing) + 0.5 * args.label_smoothing

            loss = criterion(logits, label)
            loss.backward()
            optim.step()
            train_loss_sum += loss.item() * label.size(0)
            train_total += label.size(0)
            train_iter.set_postfix(loss=f"{loss.item():.4f}")

        val_loss, val_acc = evaluate(model, val_loader, device, epoch, args.epochs, criterion)
        scheduler.step()
        train_loss = train_loss_sum / max(train_total, 1)
        print(f"Epoch {epoch}: train_loss {train_loss:.4f} val_loss {val_loss:.4f} val_acc {val_acc:.3f}")

        if val_loss < best_val_loss:
            best_val_loss = val_loss
            torch.save(model.state_dict(), args.out / "melcnn.pt")
            print("Saved new checkpoint")
            epochs_no_improve = 0
        else:
            epochs_no_improve += 1
            if epochs_no_improve >= args.patience:
                print(f"No improvement for {args.patience} epochs. Early stopping at epoch {epoch}.")
                break


if __name__ == "__main__":
    main()
