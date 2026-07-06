#!/usr/bin/env python3
"""Train the optional on-device receipt-category TFLite model.

PocketScan ships WITHOUT a committed model binary: the Android classifier falls
back to a transparent keyword model when no `receipt_category.tflite` asset is
present (see CategoryClassifier.kt). This script lets you *optionally* train a
tiny model that plugs into the exact same 128-dim hashing feature layout the
app uses, then drop the result into `app/src/main/assets/`.

Everything here is free and offline: TensorFlow/Keras only, synthetic training
data generated from the same keyword vocabulary the app falls back on.

Usage:
    pip install "tensorflow>=2.15"          # or tensorflow-cpu
    python scripts/train_category_model.py --out app/src/main/assets/receipt_category.tflite

If TensorFlow can't be installed on your Python version, that's fine — the app
still works via the keyword path. CI keeps the model optional on purpose.
"""
from __future__ import annotations

import argparse
import random
import sys

FEATURE_SIZE = 128  # must match CategoryClassifier.FEATURE_SIZE

# Category order must match the SpendCategory enum in CategoryClassifier.kt.
CATEGORIES = [
    "GROCERIES", "DINING", "TRANSPORT",
    "SHOPPING", "UTILITIES", "HEALTH", "OTHER",
]

VOCAB = {
    "GROCERIES": ["market", "grocery", "supermarket", "foods", "migros",
                  "bim", "produce", "dairy", "bananas", "milk", "bread"],
    "DINING": ["restaurant", "cafe", "coffee", "bistro", "pizzeria", "bar",
               "diner", "kitchen", "grill", "burger", "latte"],
    "TRANSPORT": ["fuel", "gas", "petrol", "shell", "uber", "taxi", "metro",
                  "parking", "toll", "benzin"],
    "SHOPPING": ["store", "mall", "clothing", "electronics", "boutique",
                 "shop", "apparel", "outlet"],
    "UTILITIES": ["electric", "water", "internet", "phone", "utility",
                  "fatura"],
    "HEALTH": ["pharmacy", "chemist", "clinic", "hospital", "eczane",
               "dental", "optician"],
    "OTHER": ["misc", "sundry", "item", "service", "note"],
}


def hashing_features(tokens: list[str]) -> list[float]:
    """Mirror of Kotlin CategoryClassifier.featurize()."""
    import math

    vec = [0.0] * FEATURE_SIZE
    for tok in tokens:
        # Java String.hashCode(), reproduced so features line up with the app.
        h = 0
        for ch in tok:
            h = (31 * h + ord(ch)) & 0xFFFFFFFF
        if h >= 0x80000000:
            h -= 0x100000000
        idx = (h & 0x7FFFFFFF) % FEATURE_SIZE
        vec[idx] += 1.0
    norm = math.sqrt(sum(v * v for v in vec))
    if norm > 0:
        vec = [v / norm for v in vec]
    return vec


def synth_sample(rng: random.Random, category: str) -> list[str]:
    words = rng.sample(VOCAB[category], k=min(3, len(VOCAB[category])))
    # sprinkle a couple of neutral tokens
    words += rng.choices(["total", "date", "cash", "vat", "tax"], k=2)
    return words


def build_dataset(n_per_class: int):
    rng = random.Random(42)
    x, y = [], []
    for ci, cat in enumerate(CATEGORIES):
        for _ in range(n_per_class):
            x.append(hashing_features(synth_sample(rng, cat)))
            y.append(ci)
    return x, y


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="app/src/main/assets/receipt_category.tflite")
    parser.add_argument("--epochs", type=int, default=30)
    args = parser.parse_args()

    try:
        import numpy as np
        import tensorflow as tf
    except ImportError:
        print("TensorFlow/NumPy not installed; skipping training. "
              "The app will use its keyword fallback.", file=sys.stderr)
        return 0

    x, y = build_dataset(n_per_class=400)
    x = np.asarray(x, dtype="float32")
    y = np.asarray(y, dtype="int32")

    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(FEATURE_SIZE,)),
        tf.keras.layers.Dense(64, activation="relu"),
        tf.keras.layers.Dense(len(CATEGORIES), activation="softmax"),
    ])
    model.compile(optimizer="adam",
                  loss="sparse_categorical_crossentropy",
                  metrics=["accuracy"])
    model.fit(x, y, epochs=args.epochs, batch_size=32, verbose=2)

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_model = converter.convert()
    with open(args.out, "wb") as f:
        f.write(tflite_model)
    print(f"Wrote {args.out} ({len(tflite_model)} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
