package com.cropora

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.cropora.ml.TFLiteClassifier
import com.cropora.network.PredictionResponse
import com.cropora.network.RetrofitClient
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * ScanActivity
 * Handles image capture/selection and routes predictions through either the
 * Cloud API or the on-device TensorFlow Lite model. Both paths send the shared
 * prediction response to the Result screen.
 */
 
class ScanActivity : AppCompatActivity() {

    // UI references 
    private lateinit var imagePreview: ImageView
    private lateinit var textImageStatus: TextView
    private lateinit var buttonDetectDisease: Button
    private lateinit var progressDetection: ProgressBar
    private lateinit var radioDetectionMode: RadioGroup
    private lateinit var textDetectionModeDescription: TextView

    // State and helpers 
    private var selectedImageUri: Uri? = null          // currently chosen image URI
    private var pendingCameraUri: Uri? = null          // URI reserved when invoking camera
    private var activeUploadCall: Call<PredictionResponse>? = null
    private var activeUploadFile: File? = null
    private var isPreparingUpload = false
    private var localClassifier: TFLiteClassifier? = null
    private val gson = Gson()
    private val imageProcessingExecutor = Executors.newSingleThreadExecutor()

    /**
     * Launcher that requests the CAMERA permission. If granted, starts the camera.
     */
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Launcher that takes a picture using the built-in camera. The image is
     * saved to a FileProvider URI created earlier. On success, we update the
     * selected image to that URI.
     */
     
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val cameraUri = pendingCameraUri
        if (success && cameraUri != null) {
            updateSelectedImage(cameraUri)
        } else {
            Toast.makeText(this, R.string.camera_cancelled, Toast.LENGTH_SHORT).show()
        }
        pendingCameraUri = null
    }

    /**
     * Launcher that opens the system picker for content. We request an image
     * and update the selected image when the user chooses one.
     */
     
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            updateSelectedImage(uri)
        } else {
            Toast.makeText(this, R.string.gallery_cancelled, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * onCreate
     * - Hook up UI
     * - Restore minimal instance state (pending camera URI, selected image, or
     *   interrupted upload status)
     * - Wire button click handlers for camera, gallery, and detection mode
     */

     
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        imagePreview = findViewById(R.id.imagePreview)
        textImageStatus = findViewById(R.id.textImageStatus)
        buttonDetectDisease = findViewById(R.id.buttonDetectDisease)
        progressDetection = findViewById(R.id.progressDetection)
        radioDetectionMode = findViewById(R.id.radioDetectionMode)
        textDetectionModeDescription = findViewById(R.id.textDetectionModeDescription)

        radioDetectionMode.setOnCheckedChangeListener { _, checkedId ->
            textDetectionModeDescription.setText(
                if (checkedId == R.id.radioOfflineMode) {
                    R.string.detection_mode_offline_description
                } else {
                    R.string.detection_mode_cloud_description
                }
            )
        }

        // Open camera (with permission check) when user taps Take Photo
        findViewById<Button>(R.id.buttonTakePhoto).setOnClickListener {
            openCameraWithPermissionCheck()
        }
        // Open gallery picker
        findViewById<Button>(R.id.buttonChooseGallery).setOnClickListener {
            galleryLauncher.launch("image/*")
        }
        // Run the selected prediction path when Detect Disease is tapped.
        buttonDetectDisease.setOnClickListener {
            if (radioDetectionMode.checkedRadioButtonId == R.id.radioOfflineMode) {
                classifySelectedImageLocally()
            } else {
                uploadSelectedImage()
            }
        }

        // Restore any in-progress state after rotation / process restart
        pendingCameraUri = savedInstanceState
            ?.getString(KEY_PENDING_CAMERA_URI)
            ?.let(Uri::parse)
        savedInstanceState?.getString(KEY_SELECTED_IMAGE_URI)?.let { uriText ->
            updateSelectedImage(Uri.parse(uriText))
        }
        if (savedInstanceState?.getBoolean(KEY_UPLOAD_IN_PROGRESS) == true) {
            textImageStatus.setText(R.string.upload_interrupted)
        }
    }

    /**
     * Check camera permission; if granted, launch the camera flow. Otherwise
     * request the permission using the Activity Result API.
     */
    private fun openCameraWithPermissionCheck() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /**
     * Attempt to reserve a FileProvider-backed file and launch the camera. If
     * creating the file fails, show an error toast.
     */
    private fun launchCamera() {
        try {
            val imageUri = createImageUri()
            pendingCameraUri = imageUri
            cameraLauncher.launch(imageUri)
        } catch (exception: IOException) {
            pendingCameraUri = null
            Toast.makeText(this, R.string.camera_file_error, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Create a file path under the app's external files directory and return a
     * FileProvider URI for it. Throws IOException on failure.
     */
    @Throws(IOException::class)
    private fun createImageUri(): Uri {
        val imageDirectory = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "captures")
        if (!imageDirectory.exists() && !imageDirectory.mkdirs()) {
            throw IOException("Could not create image directory")
        }

        val imageFile = File(imageDirectory, "leafguard_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            this,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            imageFile
        )
    }

    /**
     * Update UI and internal state to reflect the newly selected image URI.
     */
    private fun updateSelectedImage(uri: Uri) {
        selectedImageUri = uri
        imagePreview.setImageURI(uri)
        textImageStatus.setText(R.string.image_ready_for_detection)
        buttonDetectDisease.isEnabled = true
    }

    /**
     * Run on-device classification for the currently selected image.
     */
    private fun classifySelectedImageLocally() {
        val imageUri = selectedImageUri
        if (imageUri == null) {
            Toast.makeText(this, R.string.select_image_first, Toast.LENGTH_SHORT).show()
            return
        }

        setDetectionInProgress(true)
        imageProcessingExecutor.execute {
            var bitmap: Bitmap? = null
            val predictionResult = try {
                bitmap = contentResolver.openInputStream(imageUri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                } ?: throw IOException("Unable to decode selected image")
                val classifier = localClassifier
                    ?: TFLiteClassifier(applicationContext).also { localClassifier = it }
                Result.success(classifier.classify(bitmap))
            } catch (exception: Exception) {
                Result.failure(exception)
            } finally {
                bitmap?.recycle()
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                setDetectionInProgress(false)
                predictionResult.onSuccess { prediction ->
                    startActivity(ResultActivity.createIntent(this@ScanActivity, prediction))
                }.onFailure {
                    Toast.makeText(
                        this@ScanActivity,
                        getString(R.string.offline_prediction_error),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Prepare the currently selected image for upload:
     * - copy its contents into a cache file (on a background thread)
     * - then start the multipart upload on the main thread
     * Handles basic error conditions and displays toasts.
     */
    private fun uploadSelectedImage() {
        val imageUri = selectedImageUri
        if (imageUri == null) {
            Toast.makeText(this, R.string.select_image_first, Toast.LENGTH_SHORT).show()
            return
        }

        setDetectionInProgress(true)
        isPreparingUpload = true
        imageProcessingExecutor.execute {
            val uploadFile = try {
                copyUriToCacheFile(imageUri)
            } catch (exception: IOException) {
                null
            } catch (exception: SecurityException) {
                null
            }

            runOnUiThread {
                isPreparingUpload = false
                if (isFinishing || isDestroyed) {
                    uploadFile?.delete()
                    return@runOnUiThread
                }
                if (uploadFile == null) {
                    setDetectionInProgress(false)
                    Toast.makeText(this, R.string.image_prepare_error, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                startUpload(imageUri, uploadFile)
            }
        }
    }

    /**
     * Start the Retrofit multipart upload and handle the asynchronous callbacks.
     * On success, transitions to ResultActivity with the parsed prediction.
     */
    private fun startUpload(imageUri: Uri, uploadFile: File) {
        val mimeType = contentResolver.getType(imageUri) ?: "image/*"
        val requestBody = uploadFile.asRequestBody(mimeType.toMediaTypeOrNull())
        val imagePart = MultipartBody.Part.createFormData("image", uploadFile.name, requestBody)
        val uploadCall = RetrofitClient.apiService(this).uploadImage(imagePart)
        activeUploadCall = uploadCall
        activeUploadFile = uploadFile

        uploadCall.enqueue(object : Callback<PredictionResponse> {
            override fun onResponse(
                call: Call<PredictionResponse>,
                response: Response<PredictionResponse>
            ) {
                if (!finishUpload(call, uploadFile)) {
                    return
                }

                val prediction = response.body()
                if (!response.isSuccessful || prediction == null) {
                    Toast.makeText(
                        this@ScanActivity,
                        serverErrorMessage(response),
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
                startActivity(ResultActivity.createIntent(this@ScanActivity, prediction))
            }

            override fun onFailure(
                call: Call<PredictionResponse>,
                throwable: Throwable
            ) {
                if (!finishUpload(call, uploadFile) || call.isCanceled) {
                    return
                }
                Toast.makeText(
                    this@ScanActivity,
                    if (throwable is IOException) {
                        R.string.network_error
                    } else {
                        R.string.invalid_server_response
                    },
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    /**
     * Common cleanup after an upload finishes or fails. Deletes the temporary
     * upload file and clears active upload references. Returns true if the
     * completed call was the active upload (caller should proceed) or false if
     * another upload replaced it.
     */

     
    private fun finishUpload(call: Call<PredictionResponse>, uploadFile: File): Boolean {
        uploadFile.delete()
        if (call !== activeUploadCall) {
            return false
        }
        activeUploadCall = null
        activeUploadFile = null
        if (isFinishing || isDestroyed) {
            return false
        }
        setDetectionInProgress(false)
        return true
    }

    /**
     * Helper to extract a useful error message from a non-2xx response. If the
     * backend returns JSON `{ "detail": "..." }` this returns that string;
     * otherwise it returns a generic formatted message with the response code.
     */
    private fun serverErrorMessage(response: Response<PredictionResponse>): String {
        val detail = runCatching {
            response.errorBody()
                ?.string()
                ?.let { gson.fromJson(it, JsonObject::class.java) }
                ?.get("detail")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString
        }.getOrNull()

        return if (detail.isNullOrBlank()) {
            getString(R.string.server_error_format, response.code())
        } else {
            getString(R.string.server_error_detail_format, response.code(), detail)
        }
    }

    /**
     * Copy the content addressed by `uri` into a new file in the app's cache
     * directory and return the File. Throws IOException if reading or writing
     * fails.
     */
    @Throws(IOException::class)
    private fun copyUriToCacheFile(uri: Uri): File {
        val uploadFile = File(cacheDir, "leafguard_upload_${System.currentTimeMillis()}.jpg")
        try {
            contentResolver.openInputStream(uri).use { inputStream ->
                if (inputStream == null) {
                    throw IOException("Unable to open selected image")
                }
                FileOutputStream(uploadFile).use { outputStream ->
                    inputStream.copyTo(outputStream, bufferSize = 8192)
                }
            }
        } catch (exception: IOException) {
            uploadFile.delete()
            throw exception
        } catch (exception: SecurityException) {
            uploadFile.delete()
            throw exception
        }
        return uploadFile
    }

    /**
     * Update the scan controls while either prediction path is running.
     */
    private fun setDetectionInProgress(inProgress: Boolean) {
        progressDetection.visibility = if (inProgress) View.VISIBLE else View.GONE
        buttonDetectDisease.isEnabled = !inProgress && selectedImageUri != null
        findViewById<Button>(R.id.buttonTakePhoto).isEnabled = !inProgress
        findViewById<Button>(R.id.buttonChooseGallery).isEnabled = !inProgress
        for (index in 0 until radioDetectionMode.childCount) {
            radioDetectionMode.getChildAt(index).isEnabled = !inProgress
        }
    }

    /**
     * Preserve minimal state across configuration changes so a pending camera
     * URI or interrupted upload state can be restored.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SELECTED_IMAGE_URI, selectedImageUri?.toString())
        outState.putString(KEY_PENDING_CAMERA_URI, pendingCameraUri?.toString())
        outState.putBoolean(
            KEY_UPLOAD_IN_PROGRESS,
            isPreparingUpload || activeUploadCall != null
        )
    }

    /**
     * Cancel any in-flight upload, remove temporary files, close the local
     * interpreter, and stop background work when the activity is destroyed.
     */
    override fun onDestroy() {
        activeUploadCall?.cancel()
        activeUploadCall = null
        activeUploadFile?.delete()
        activeUploadFile = null
        imageProcessingExecutor.execute {
            localClassifier?.close()
            localClassifier = null
        }
        imageProcessingExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        // Keys used for saving minimal instance state
        private const val KEY_SELECTED_IMAGE_URI = "selected_image_uri"
        private const val KEY_PENDING_CAMERA_URI = "pending_camera_uri"
        private const val KEY_UPLOAD_IN_PROGRESS = "upload_in_progress"
    }
}
