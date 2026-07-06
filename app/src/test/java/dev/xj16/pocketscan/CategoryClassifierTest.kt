package dev.xj16.pocketscan

import dev.xj16.pocketscan.ml.CategoryClassifier
import dev.xj16.pocketscan.ml.SpendCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the deterministic keyword fallback path of the category classifier
 * (the path used when no TFLite model is bundled) plus the feature vectorizer.
 * No Android or TensorFlow runtime required.
 */
class CategoryClassifierTest {

    private val clf = CategoryClassifier.keywordOnly()

    @Test
    fun `classifies a supermarket receipt as groceries`() {
        val text = "MIGROS SUPERMARKET\nBananas 1.29\nMilk 3.49\nTOTAL 10.58"
        assertEquals(SpendCategory.GROCERIES, clf.classify(text))
    }

    @Test
    fun `classifies a coffee shop as dining`() {
        val text = "Blue Bottle Cafe\nLatte 4.50\nEspresso 3.00"
        assertEquals(SpendCategory.DINING, clf.classify(text))
    }

    @Test
    fun `classifies a fuel stop as transport`() {
        val text = "SHELL\nUnleaded fuel\nPetrol 45.00"
        assertEquals(SpendCategory.TRANSPORT, clf.classify(text))
    }

    @Test
    fun `classifies a pharmacy as health`() {
        val text = "City Pharmacy\nParacetamol\nTotal 8.99"
        assertEquals(SpendCategory.HEALTH, clf.classify(text))
    }

    @Test
    fun `unknown text falls back to other`() {
        assertEquals(SpendCategory.OTHER, clf.classify("xyzzy 123 qwertyuiop"))
    }

    @Test
    fun `featurizer produces fixed-length normalized vector`() {
        val vec = clf.featurize("coffee shop latte")
        assertEquals(CategoryClassifier.FEATURE_SIZE, vec.size)
        // L2 norm should be ~1 for non-empty input.
        val norm = kotlin.math.sqrt(vec.fold(0.0) { acc, v -> acc + v * v })
        assertEquals(1.0, norm, 1e-4)
    }

    @Test
    fun `featurizer is deterministic`() {
        val a = clf.featurize("shell fuel station")
        val b = clf.featurize("shell fuel station")
        assertTrue(a.contentEquals(b))
    }
}
