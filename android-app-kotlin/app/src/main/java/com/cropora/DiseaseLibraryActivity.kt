package com.cropora

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cropora.data.DiseaseRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParserException

class DiseaseLibraryActivity : AppCompatActivity() {
    private lateinit var diseaseAdapter: DiseaseAdapter
    private lateinit var recyclerDiseases: RecyclerView
    private lateinit var textLibraryEmpty: TextView
    private lateinit var progressLibrary: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_disease_library)

        recyclerDiseases = findViewById(R.id.recyclerDiseases)
        textLibraryEmpty = findViewById(R.id.textLibraryEmpty)
        progressLibrary = findViewById(R.id.progressLibrary)
        diseaseAdapter = DiseaseAdapter { disease ->
            val intent = Intent(this, DiseaseDetailActivity::class.java).apply {
                putExtra(DiseaseDetailActivity.EXTRA_DISEASE_NAME, disease.name)
            }
            startActivity(intent)
        }
        recyclerDiseases.layoutManager = LinearLayoutManager(this)
        recyclerDiseases.adapter = diseaseAdapter
        loadDiseases()
    }

    private fun loadDiseases() {
        lifecycleScope.launch {
            progressLibrary.visibility = View.VISIBLE
            try {
                val diseases = withContext(Dispatchers.IO) {
                    DiseaseRepository.getInstance(applicationContext).getAllDiseases()
                }
                diseaseAdapter.submitList(diseases)
                val hasDiseases = diseases.isNotEmpty()
                recyclerDiseases.visibility = if (hasDiseases) View.VISIBLE else View.GONE
                textLibraryEmpty.visibility = if (hasDiseases) View.GONE else View.VISIBLE
            } catch (exception: IOException) {
                showLibraryError()
            } catch (exception: XmlPullParserException) {
                showLibraryError()
            } finally {
                progressLibrary.visibility = View.GONE
            }
        }
    }

    private fun showLibraryError() {
        diseaseAdapter.submitList(emptyList())
        recyclerDiseases.visibility = View.GONE
        textLibraryEmpty.setText(R.string.disease_library_error)
        textLibraryEmpty.visibility = View.VISIBLE
        Toast.makeText(this, R.string.disease_library_error, Toast.LENGTH_LONG).show()
    }
}
