# App assets

## `receipt_category.tflite` (optional)

PocketScan's spending-category classifier (`CategoryClassifier.kt`) will load a
TensorFlow Lite model from **`receipt_category.tflite`** in this folder if one
is present.

The model is **intentionally not committed** — shipping a binary blob in a
source repo is undesirable, and the app works fully without it: when the asset
is missing, the classifier transparently falls back to a deterministic keyword
model. Inference is 100% on-device either way.

To generate and install the optional model:

```bash
pip install "tensorflow>=2.15"
python scripts/train_category_model.py --out app/src/main/assets/receipt_category.tflite
```

The training script uses the **exact same 128-dimension hashing feature layout**
the Android app computes (`CategoryClassifier.featurize`, verified against
Java's `String.hashCode`), so the produced model drops in with no glue code.
