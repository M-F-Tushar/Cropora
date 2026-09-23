package com.cropora

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cropora.network.ServerPreferences

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val editServerUrl = findViewById<EditText>(R.id.editServerUrl)
        editServerUrl.setText(ServerPreferences.getBaseUrl(this))

        findViewById<Button>(R.id.buttonSaveServerUrl).setOnClickListener {
            val enteredUrl = editServerUrl.text.toString().trim()
            if (!enteredUrl.startsWith("http://") && !enteredUrl.startsWith("https://")) {
                Toast.makeText(this, R.string.server_url_invalid, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            ServerPreferences.setBaseUrl(this, enteredUrl)
            Toast.makeText(this, R.string.server_url_saved, Toast.LENGTH_SHORT).show()
        }
    }
}
