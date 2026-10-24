#!/usr/bin/env python3
"""Equivalence PyTorch (EdgeFace-S gamma=0.5) <-> ONNX (edgeface_s.onnx).

Compare les embeddings sur 20 images de visages PUBLICS (LFW, personnalites publiques),
crop 112x112 issu du jeu LFW (aucune image de judoka, aucune donnee du club).

Critere : cosinus >= 0.99 pour CHAQUE image.

Dependances (venv recommande) :
    pip install -r tools/face/requirements-equivalence.txt

Usage :
    python tools/face/verify_edgeface_equivalence.py
    python tools/face/verify_edgeface_equivalence.py --images-dir ./mes_20_visages

Sortie : tableau par image + verdict. Aucune image n'est ecrite dans le depot.
"""
from __future__ import annotations

import argparse
import io
import sys
from pathlib import Path

import numpy as np

REPO = Path(__file__).resolve().parents[2]
ONNX_PATH = REPO / "app" / "src" / "main" / "assets" / "edgeface_s.onnx"
HF_REPO = "anjith2006/edgeface"
HF_SUBFOLDER = "edgeface-s-gamma-05"
N_IMAGES = 20
COSINE_MIN = 0.99


def load_images(images_dir: str | None) -> list[np.ndarray]:
    """20 images RGB uint8 112x112 (deja recadrees/alignees)."""
    if images_dir:
        try:
            from PIL import Image
        except ImportError:
            sys.exit("Pillow manquant : pip install pillow")
        paths = sorted(
            p for p in Path(images_dir).iterdir() if p.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp"}
        )[:N_IMAGES]
        if len(paths) < N_IMAGES:
            sys.exit(f"Il faut {N_IMAGES} images dans {images_dir} (trouvees: {len(paths)}).")
        out = []
        for p in paths:
            with Image.open(p) as im:
                out.append(np.asarray(im.convert("RGB").resize((112, 112), Image.BILINEAR)))
        return out

    # LFW funneled : visages de personnalites publiques, 250x250, hors depot.
    lfw_dir = Path.home() / "scikit_learn_data" / "lfw_home" / "lfw_funneled"
    if not lfw_dir.is_dir():
        sys.exit(
            "LFW introuvable. Lancez une fois : python -c \"from sklearn.datasets import fetch_lfw_people; "
            "fetch_lfw_people(color=True, resize=0.5)\""
        )
    from PIL import Image

    images: list[np.ndarray] = []
    for person in sorted(p for p in lfw_dir.iterdir() if p.is_dir()):
        jpgs = sorted(person.glob("*.jpg"))
        if not jpgs:
            continue
        with Image.open(jpgs[0]) as im:
            images.append(np.asarray(im.convert("RGB").resize((112, 112), Image.BILINEAR)))
        if len(images) == N_IMAGES:
            break
    if len(images) < N_IMAGES:
        sys.exit(f"Pas assez d'images LFW ({len(images)}).")
    return images


def preprocess_batch(images: list[np.ndarray]) -> np.ndarray:
    """NCHW float32, RGB, (v - 127.5) / 128 : normalisation d'entrainement EdgeFace."""
    arr = np.stack(images).astype(np.float32)
    arr = (arr - 127.5) / 128.0
    return np.transpose(arr, (0, 3, 1, 2))


def onnx_embeddings(batch: np.ndarray):
    import onnxruntime as ort

    if not ONNX_PATH.exists():
        sys.exit(f"Modele ONNX absent : {ONNX_PATH}")
    session = ort.InferenceSession(str(ONNX_PATH), providers=["CPUExecutionProvider"])
    input_name = session.get_inputs()[0].name
    shapes = [tuple(i.shape) for i in session.get_inputs()]
    print(f"ONNX inputs={shapes} outputs={[tuple(o.shape) for o in session.get_outputs()]}")
    embs = []
    for i in range(batch.shape[0]):
        out = session.run(None, {input_name: batch[i : i + 1]})[0]
        embs.append(out.reshape(-1))
    return np.stack(embs)


def torch_embeddings(batch: np.ndarray):
    import torch
    from transformers import AutoModel

    model = AutoModel.from_pretrained(HF_REPO, subfolder=HF_SUBFOLDER, trust_remote_code=True)
    model.eval()
    embs = []
    with torch.no_grad():
        for i in range(batch.shape[0]):
            tensor = torch.from_numpy(batch[i : i + 1])
            out = model(pixel_values=tensor)
            if hasattr(out, "embeddings"):
                vec = out.embeddings
            elif hasattr(out, "last_hidden_state"):
                vec = out.last_hidden_state
            else:
                vec = out[0] if isinstance(out, (tuple, list)) else out
            embs.append(vec.reshape(-1).cpu().numpy())
    return np.stack(embs)


def cosine(a: np.ndarray, b: np.ndarray) -> float:
    return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b) + 1e-12))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--images-dir", default=None, help="20 images 112x112 deja alignees")
    args = parser.parse_args()

    images = load_images(args.images_dir)
    batch = preprocess_batch(images)

    print("Inference ONNX Runtime (CPU)…")
    emb_onnx = onnx_embeddings(batch)
    print("Inference PyTorch (CPU)…")
    emb_torch = torch_embeddings(batch)

    print(f"\n{'#':>2}  {'cosinus':>8}  verdict")
    scores = []
    for i in range(N_IMAGES):
        c = cosine(emb_torch[i], emb_onnx[i])
        scores.append(c)
        print(f"{i + 1:>2}  {c:8.6f}  {'OK' if c >= COSINE_MIN else 'ECHEC'}")

    worst = min(scores)
    mean = float(np.mean(scores))
    ok = worst >= COSINE_MIN
    print(f"\nmin={worst:.6f}  moyenne={mean:.6f}  seuil={COSINE_MIN}")
    print("RESULTAT:", "EQUIVALENT" if ok else "NON EQUIVALENT")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
