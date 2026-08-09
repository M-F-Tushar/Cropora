package com.leafguard

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

class ScanActivity : AppCompatActivity() {

    private lateinit var imagePreview: ImageView
    private lateinit var textImageStatus: TextView

    private var selectedImageUri: Uri? = null
    private var pendingCameraUri: Uri? = null

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

        findViewById<Button>(R.id.buttonTakePhoto).setOnClickListener {
            openCameraWithPermissionCheck()
        }

        findViewById<Button>(R.id.buttonChooseGallery).setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        savedInstanceState?.getString(KEY_SELECTED_IMAGE_URI)?.let { uriText ->
            updateSelectedImage(Uri.parse(uriText))
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
        textImageStatus.setText(R.string.image_selected)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SELECTED_IMAGE_URI, selectedImageUri?.toString())
    }

    companion object {
        private const val KEY_SELECTED_IMAGE_URI = "selected_image_uri"
    }
}