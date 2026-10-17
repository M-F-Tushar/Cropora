package com.cropora

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cropora.ml.TFLiteClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineInferencePerformanceTest {
    @Test
    fun oneOfflineInferenceCompletesWithinSmokeBudget() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(30, 180, 60))
        }

        val startedAt = System.nanoTime()
        val prediction = TFLiteClassifier(context).use { classifier ->
            classifier.classify(bitmap)
        }
        val elapsedMilliseconds = (System.nanoTime() - startedAt) / 1_000_000

        val labelCount = context.assets.open("labels.txt").use { stream ->
            stream.bufferedReader().readLines().count { it.isNotBlank() }
        }
        assertEquals(38, labelCount)
        assertTrue(prediction.modelLabel.isNotBlank())
        assertTrue(prediction.confidence in 0f..1f)
        assertTrue("Inference took ${elapsedMilliseconds}ms", elapsedMilliseconds < 15_000)
        bitmap.recycle()
    }
}
