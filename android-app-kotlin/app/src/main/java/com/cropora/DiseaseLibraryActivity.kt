package com.cropora

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
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
    private lateinit var referenceHeader: View
    private lateinit var advancedAdapter: ConcatAdapter
    private var advanced = false
    private var loading = true
    private var hasDiseases = false
    private var stateMessage = R.string.disease_library_empty

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
        referenceHeader = findViewById(R.id.referenceLibraryHeader)
        (referenceHeader.parent as ViewGroup).removeView(referenceHeader)
        referenceHeader.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        referenceHeader.visibility = View.VISIBLE
        advancedAdapter = ConcatAdapter(ReferenceHeaderAdapter(referenceHeader), diseaseAdapter)
        listOf(R.id.referenceLibraryHeading, R.id.referenceLibrarySection).forEach { id ->
            ViewCompat.setAccessibilityHeading(referenceHeader.findViewById(id), true)
        }
        ViewCompat.setAccessibilityHeading(findViewById(R.id.textLibraryTitle), true)
        recyclerDiseases.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                if (view === referenceHeader) return
                view.setBackgroundResource(
                    if (advanced) R.drawable.reference_card_action else R.color.leaf_surface
                )
            }

            override fun onChildViewDetachedFromWindow(view: View) = Unit
        })
        loadDiseases()
    }

    private fun loadDiseases() {
        lifecycleScope.launch {
            loading = true
            updateLibraryState()
            try {
                val diseases = withContext(Dispatchers.IO) {
                    DiseaseRepository.getInstance(applicationContext).getLocalizedDiseases(this@DiseaseLibraryActivity)
                }
                diseaseAdapter.submitList(diseases)
                hasDiseases = diseases.isNotEmpty()
                stateMessage = R.string.disease_library_empty
            } catch (exception: IOException) {
                showLibraryError()
            } catch (exception: XmlPullParserException) {
                showLibraryError()
            } finally {
                loading = false
                updateLibraryState()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val enabled = UiPreferences.isAdvanced(this)
        if (advanced != enabled) {
            advanced = enabled
            recyclerDiseases.adapter = if (advanced) advancedAdapter else diseaseAdapter
        }
        listOf(R.id.textLibraryTitle, R.id.textLibraryDescription).forEach { id ->
            findViewById<View>(id).visibility = if (advanced) View.GONE else View.VISIBLE
        }
        updateLibraryState()
    }

    private fun updateLibraryState() {
        recyclerDiseases.visibility = if (advanced || hasDiseases) View.VISIBLE else View.GONE
        progressLibrary.visibility = if (!advanced && loading) View.VISIBLE else View.GONE
        textLibraryEmpty.setText(stateMessage)
        textLibraryEmpty.visibility = if (!advanced && !loading && !hasDiseases) View.VISIBLE else View.GONE
        referenceHeader.findViewById<View>(R.id.referenceLibraryLoading).visibility =
            if (loading) View.VISIBLE else View.GONE
        referenceHeader.findViewById<TextView>(R.id.referenceLibraryState).apply {
            setText(stateMessage)
            visibility = if (!loading && !hasDiseases) View.VISIBLE else View.GONE
        }
    }

    // A scrolling header preserves room for results at large font scales and in landscape.
    private class ReferenceHeaderAdapter(private val header: View) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            object : RecyclerView.ViewHolder(header) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit

        override fun getItemCount(): Int = 1
    }

    private fun showLibraryError() {
        diseaseAdapter.submitList(emptyList())
        hasDiseases = false
        stateMessage = R.string.disease_library_error
        Toast.makeText(this, R.string.disease_library_error, Toast.LENGTH_LONG).show()
    }
}
