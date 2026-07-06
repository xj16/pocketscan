package dev.xj16.pocketscan.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/** Spending categories PocketScan can tag a receipt with. */
enum class SpendCategory(val label: String) {
    GROCERIES("Groceries"),
    DINING("Dining"),
    TRANSPORT("Transport"),
    SHOPPING("Shopping"),
    UTILITIES("Utilities"),
    HEALTH("Health"),
    OTHER("Other"),
}

/**
 * On-device receipt categorizer.
 *
 * If a TensorFlow Lite model is bundled at `assets/receipt_category.tflite`,
 * this runs it: the OCR text is hashed into a fixed bag-of-words feature
 * vector and fed to the interpreter, whose softmax output picks a category.
 * The model file is intentionally NOT committed (it would be a binary blob),
 * so out of the box the classifier degrades to a transparent, deterministic
 * keyword model — the same "prefer a local model, fall back gracefully" rule
 * the whole app follows.
 *
 * Either way, inference is 100% offline and free.
 */
class CategoryClassifier private constructor(
    private val interpreter: Interpreter?,
) {

    /** Predicts a spending category from raw OCR [text]. */
    fun classify(text: String): SpendCategory {
        val interp = interpreter
        return if (interp != null) {
            runCatching { classifyWithModel(interp, text) }
                .getOrElse { keywordFallback(text) }
        } else {
            keywordFallback(text)
        }
    }

    private fun classifyWithModel(interp: Interpreter, text: String): SpendCategory {
        val input = Array(1) { featurize(text) }
        val output = Array(1) { FloatArray(SpendCategory.entries.size) }
        interp.run(input, output)
        val bestIdx = output[0].indices.maxByOrNull { output[0][it] } ?: return SpendCategory.OTHER
        return SpendCategory.entries.getOrElse(bestIdx) { SpendCategory.OTHER }
    }

    /**
     * Hashing vectorizer: maps tokens into a fixed-width float vector via a
     * stable hash. Matches the feature layout a companion training script would
     * produce, and is unit-testable independent of any model.
     */
    internal fun featurize(text: String): FloatArray {
        val vec = FloatArray(FEATURE_SIZE)
        for (token in tokenize(text)) {
            val idx = (token.hashCode() and Int.MAX_VALUE) % FEATURE_SIZE
            vec[idx] += 1f
        }
        // L2-normalize for a stable scale.
        val norm = kotlin.math.sqrt(vec.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0f) for (i in vec.indices) vec[i] /= norm
        return vec
    }

    fun close() = interpreter?.close()

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 }

    // --- deterministic fallback ---------------------------------------------

    /** Transparent keyword model used when no TFLite model is present. */
    internal fun keywordFallback(text: String): SpendCategory {
        val t = text.lowercase()
        val scores = mutableMapOf<SpendCategory, Int>()
        for ((category, keywords) in KEYWORDS) {
            val hits = keywords.count { t.contains(it) }
            if (hits > 0) scores[category] = hits
        }
        return scores.maxByOrNull { it.value }?.key ?: SpendCategory.OTHER
    }

    companion object {
        private const val TAG = "CategoryClassifier"
        internal const val FEATURE_SIZE = 128
        private const val MODEL_ASSET = "receipt_category.tflite"

        private val KEYWORDS: Map<SpendCategory, List<String>> = mapOf(
            SpendCategory.GROCERIES to listOf(
                "market", "grocery", "supermarket", "foods", "bakkal", "migros",
                "bim", "produce", "dairy", "bananas", "milk", "bread",
            ),
            SpendCategory.DINING to listOf(
                "restaurant", "cafe", "coffee", "bistro", "pizzeria", "bar",
                "diner", "kitchen", "grill", "lokanta", "burger",
            ),
            SpendCategory.TRANSPORT to listOf(
                "fuel", "gas", "petrol", "shell", "bp", "uber", "taxi", "metro",
                "parking", "toll", "benzin",
            ),
            SpendCategory.SHOPPING to listOf(
                "store", "mall", "clothing", "electronics", "boutique", "shop",
                "apparel", "outlet",
            ),
            SpendCategory.UTILITIES to listOf(
                "electric", "water", "internet", "phone", "utility", "gas bill",
                "fatura",
            ),
            SpendCategory.HEALTH to listOf(
                "pharmacy", "chemist", "clinic", "hospital", "eczane", "dental",
                "optician",
            ),
        )

        /**
         * Builds a classifier, loading the optional TFLite model from assets.
         * Never throws — a missing/broken model just yields the keyword path.
         */
        fun create(context: Context): CategoryClassifier {
            val interpreter = try {
                val model = loadModel(context)
                model?.let { Interpreter(it) }
            } catch (t: Throwable) {
                Log.i(TAG, "No TFLite model available; using keyword classifier", t)
                null
            }
            return CategoryClassifier(interpreter)
        }

        private fun loadModel(context: Context): MappedByteBuffer? {
            return try {
                val fd = context.assets.openFd(MODEL_ASSET)
                FileInputStream(fd.fileDescriptor).use { input ->
                    input.channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        fd.startOffset,
                        fd.declaredLength,
                    )
                }
            } catch (_: Throwable) {
                // Asset absent — expected in the default build.
                null
            }
        }

        /** For tests: a classifier with no model (keyword path). */
        fun keywordOnly(): CategoryClassifier = CategoryClassifier(interpreter = null)
    }
}
