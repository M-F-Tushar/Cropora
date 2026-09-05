package com.cropora

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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

class ScanActivity : AppCompatActivity() {

    private lateinit var imagePreview: ImageView
    private lateinit var textImageStatus: TextView
    private lateinit var buttonDetectDisease: Button
    private lateinit var progressUpload: ProgressBar

    private var selectedImageUri: Uri? = null
    private var pendingCameraUri: Uri? = null
    private var activeUploadCall: Call<PredictionResponse>? = null
    private var activeUploadFile: File? = null
    private var isPreparingUpload = false
    private val gson = Gson()
    private val imagePreparationExecutor = Executors.newSingleThreadExecutor()

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

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

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            updateSelectedImage(uri)
        } else {
            Toast.makeText(this, R.string.gallery_cancelled, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        imagePreview = findViewById(R.id.imagePreview)
        textImageStatus = findViewById(R.id.textImageStatus)
        buttonDetectDisease = findViewById(R.id.buttonDetectDisease)
        progressUpload = findViewById(R.id.progressUpload)

        findViewById<Button>(R.id.buttonTakePhoto).setOnClickListener {
            openCameraWithPermissionCheck()
        }
        findViewById<Button>(R.id.buttonChooseGallery).setOnClickListener {
            galleryLauncher.launch("image/*")
        }
        buttonDetectDisease.setOnClickListener {
            uploadSelectedImage()
        }

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

    private fun updateSelectedImage(uri: Uri) {
        selectedImageUri = uri
        imagePreview.setImageURI(uri)
        textImageStatus.setText(R.string.image_ready_for_upload)
        buttonDetectDisease.isEnabled = true
    }

    private fun uploadSelectedImage() {
        val imageUri = selectedImageUri
        if (imageUri == null) {
            Toast.makeText(this, R.string.select_image_first, Toast.LENGTH_SHORT).show()
            return
        }

        setUploadInProgress(true)
        isPreparingUpload = true
        imagePreparationExecutor.execute {
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
                    setUploadInProgress(false)
                    Toast.makeText(this, R.string.image_prepare_error, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                startUpload(imageUri, uploadFile)
            }
        }
    }

    private fun startUpload(imageUri: Uri, uploadFile: File) {
        val mimeType = contentResolver.getType(imageUri) ?: "image/*"
        val requestBody = uploadFile.asRequestBody(mimeType.toMediaTypeOrNull())
        val imagePart = MultipartBody.Part.createFormData("image", uploadFile.name, requestBody)
        val uploadCall = RetrofitClient.apiService.uploadImage(imagePart)
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
        setUploadInProgress(false)
        return true
    }

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

    private fun setUploadInProgress(inProgress: Boolean) {
        progressUpload.visibility = if (inProgress) View.VISIBLE else View.GONE
        buttonDetectDisease.isEnabled = !inProgress && selectedImageUri != null
        findViewById<Button>(R.id.buttonTakePhoto).isEnabled = !inProgress
        findViewById<Button>(R.id.buttonChooseGallery).isEnabled = !inProgress
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SELECTED_IMAGE_URI, selectedImageUri?.toString())
        outState.putString(KEY_PENDING_CAMERA_URI, pendingCameraUri?.toString())
        outState.putBoolean(
            KEY_UPLOAD_IN_PROGRESS,
            isPreparingUpload || activeUploadCall != null
        )
    }

    override fun onDestroy() {
        activeUploadCall?.cancel()
        activeUploadCall = null
        activeUploadFile?.delete()
        activeUploadFile = null
        imagePreparationExecutor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val KEY_SELECTED_IMAGE_URI = "selected_image_uri"
        private const val KEY_PENDING_CAMERA_URI = "pending_camera_uri"
        private const val KEY_UPLOAD_IN_PROGRESS = "upload_in_progress"
    }
}