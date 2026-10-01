package com.cropora.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.cropora.data.DiseaseRepository
import com.cropora.network.PredictionResponse
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.abs
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

class TFLiteClassifier @Throws(IOException::class) constructor(
    context: Context,
    modelAssetName: String = "model.tflite",
    labelsAssetName: String = "labels.txt"
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val labels = loadLabels(labelsAssetName)
    private var interpreter: Interpreter? = loadInterpreter(modelAssetName)
    private val outputClasses: Int

    init {
        val activeInterpreter = checkNotNull(interpreter)
        if (activeInterpreter.inputTensorCount != 1 || activeInterpreter.outputTensorCount != 1) {
            close()
            throw IOException("TFLite model must have exactly one input and one output")
        }
        val inputTensor = activeInterpreter.getInputTensor(0)
        val outputTensor = activeInterpreter.getOutputTensor(0)
        val inputShape = inputTensor.shape()
        val outputShape = outputTensor.shape()
        if (inputTensor.dataType() != DataType.FLOAT32) {
            close()
            throw IOException("Expected TFLite float32 input")
        }
        if (outputTensor.dataType() != DataType.FLOAT32) {
            close()
            throw IOException("Expected TFLite float32 output")
        }
        if (!inputShape.contentEquals(intArrayOf(1, INPUT_SIZE, INPUT_SIZE, RGB_CHANNELS))) {
            close()
            throw IOException("Expected TFLite input [1, 224, 224, 3]")
        }
        if (outputShape.size != 2 || outputShape[0] != 1) {
            close()
            throw IOException("Expected TFLite output [1, class_count]")
        }
        outputClasses = outputShape[1]
        if (outputClasses != labels.size) {
            close()
            throw IOException("TFLite output count does not match labels")
        }
    }

    fun classify(bitmap: Bitmap): PredictionResponse {
        val activeInterpreter = checkNotNull(interpreter) { "TFLite interpreter is closed" }
        val output = Array(1) { FloatArray(outputClasses) }
        activeInterpreter.run(preprocess(bitmap), output)
        validateProbabilities(output[0])
        val bestIndex = argmax(output[0])
        val confidence = output[0][bestIndex]
        val modelLabel = labels[bestIndex]
        val displayName = displayLabel(modelLabel)
        val guidance = try {
            DiseaseRepository.getInstance(appContext).findByName(displayName)
        } catch (_: Exception) {
            null
        }
        return PredictionResponse(
            modelLabel = modelLabel,
            disease = displayName,
            confidence = confidence,
            uncertain = confidence < CONFIDENCE_THRESHOLD,
            guidanceAvailable = guidance != null,
            symptoms = guidance?.symptoms ?: GENERIC_SYMPTOMS,
            treatment = guidance?.treatment ?: GENERIC_TREATMENT,
            prevention = guidance?.prevention ?: GENERIC_PREVENTION
        )
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val buffer = ByteBuffer.allocateDirect(
            INPUT_SIZE * INPUT_SIZE * RGB_CHANNELS * FLOAT_BYTES
        ).order(ByteOrder.nativeOrder())
        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val pixel = scaled.getPixel(x, y)
                buffer.putFloat(Color.red(pixel).toFloat())
                buffer.putFloat(Color.green(pixel).toFloat())
                buffer.putFloat(Color.blue(pixel).toFloat())
            }
        }
        buffer.rewind()
        if (scaled !== bitmap) scaled.recycle()
        return buffer
    }

    private fun loadLabels(assetName: String): List<String> {
        val loaded = mutableListOf<String>()
        BufferedReader(InputStreamReader(appContext.assets.open(assetName))).use { reader ->
            reader.forEachLine { line ->
                val value = line.trim()
                if (value.isNotEmpty() && !value.startsWith("#")) loaded += value
            }
        }
        if (loaded.size != EXPECTED_CLASSES || loaded.size != loaded.toSet().size) {
            throw IOException("Expected 38 unique labels")
        }
        return loaded
    }

    private fun loadInterpreter(assetName: String): Interpreter {
        appContext.assets.openFd(assetName).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { input ->
                val model = input.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.startOffset,
                    descriptor.declaredLength
                )
                return Interpreter(model, Interpreter.Options().apply { setNumThreads(4) })
            }
        }
    }

    private fun validateProbabilities(scores: FloatArray) {
        if (scores.any { !it.isFinite() || it < 0f || it > 1f }) {
            throw IOException("TFLite output must contain finite probabilities in [0, 1]")
        }
        val total = scores.sumOf { it.toDouble() }
        if (abs(total - 1.0) > PROBABILITY_SUM_TOLERANCE) {
            throw IOException("TFLite output probabilities must sum to 1")
        }
    }

    private fun argmax(scores: FloatArray): Int {
        var bestIndex = 0
        for (index in 1 until scores.size) {
            if (scores[index] > scores[bestIndex]) bestIndex = index
        }
        return bestIndex
    }

    private fun displayLabel(label: String): String = when (label) {
        "Apple___Apple_scab" -> "Apple Scab"
        "Corn___Cercospora_leaf_spot Gray_leaf_spot" -> "Corn Gray Leaf Spot"
        "Corn___Northern_Leaf_Blight" -> "Corn Northern Leaf Blight"
        "Potato___Early_blight" -> "Potato Early Blight"
        "Potato___Late_blight" -> "Potato Late Blight"
        "Tomato___Early_blight" -> "Tomato Early Blight"
        "Tomato___Late_blight" -> "Tomato Late Blight"
        else -> label.replace("___", " ").replace('_', ' ')
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
    }

    companion object {
        private const val INPUT_SIZE = 224
        private const val RGB_CHANNELS = 3
        private const val FLOAT_BYTES = 4
        private const val EXPECTED_CLASSES = 38
        private const val CONFIDENCE_THRESHOLD = 0.50f
        private const val PROBABILITY_SUM_TOLERANCE = 0.000011
        private const val GENERIC_SYMPTOMS =
            "Detailed symptoms and treatment guidance are not available in this version."
        private const val GENERIC_TREATMENT =
            "Please verify this result with a local agricultural expert or plant-disease reference."
        private const val GENERIC_PREVENTION =
            "Capture a clear close-up and continue monitoring. This result is not a confirmed diagnosis."
    }
}