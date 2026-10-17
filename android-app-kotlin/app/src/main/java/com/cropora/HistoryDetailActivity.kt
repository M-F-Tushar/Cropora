package com.cropora

import android.os.Bundle

import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cropora.database.AppDatabase
import com.cropora.database.ScanRecord
import com.cropora.data.Disease
import com.cropora.data.DiseasePresentation
import com.cropora.data.DiseaseRepository
import java.text.DateFormat
import java.util.Date
import java.io.IOException
import org.xmlpull.v1.XmlPullParserException

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryDetailActivity : AppCompatActivity() {
    private var scanId: Long = INVALID_SCAN_ID
    private var isDeleting = false
    private var uncertain = true
    private var confidence = 0f
    private var recordLoaded = false
    private lateinit var deleteButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history_detail)

        scanId = intent.getLongExtra(EXTRA_SCAN_ID, INVALID_SCAN_ID)
        if (scanId == INVALID_SCAN_ID) {
            Toast.makeText(this, R.string.history_invalid_id, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        deleteButton = findViewById<Button>(R.id.buttonDeleteHistory).apply {
            isEnabled = false
            setOnClickListener {
                confirmDelete()
            }
        }
        isDeleting = savedInstanceState?.getBoolean(STATE_DELETING) ?: false
        if (isDeleting) {
            deleteRecord()
        } else {
            loadRecord()
        }
    }

    override fun onResume() {
        super.onResume()
        applyUiMode()
    }

    private fun applyUiMode() {
        findViewById<TextView>(R.id.textDetailUncertain).setText(
            if (uncertain) R.string.result_uncertain else R.string.result_confident
        )
        if (recordLoaded) {
            AdvancedResultPresentation.render(
                this, confidence, uncertain, findViewById<TextView>(R.id.textDetailGuidanceStatus).text
            )
        }
        AdvancedResultPresentation.styleCare(
            this,
            listOf(R.id.textDetailSymptoms, R.id.textDetailTreatment, R.id.textDetailPrevention),
            listOf(R.id.textDetailSignsHeading, R.id.textDetailActionHeading, R.id.textDetailPreventionHeading)
        )
    }

    private fun loadRecord() {
        lifecycleScope.launch {
            try {
                val record = AppDatabase.getInstance(applicationContext)
                    .scanDao()
                    .getScanById(scanId)
                if (record == null) {
                    Toast.makeText(
                        this@HistoryDetailActivity,
                        R.string.history_record_missing,
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                } else {
                    val guidance = withContext(Dispatchers.IO) {
                        try {
                            DiseaseRepository.getInstance(applicationContext)
                                .findLocalizedByName(this@HistoryDetailActivity, record.disease)
                        } catch (_: IOException) {
                            null
                        } catch (_: XmlPullParserException) {
                            null
                        }
                    }
                    renderRecord(record, guidance)
                    deleteButton.isEnabled = true
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Toast.makeText(
                    this@HistoryDetailActivity,
                    R.string.history_detail_load_error,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun renderRecord(record: ScanRecord, guidance: Disease?) {
        findViewById<TextView>(R.id.textDetailDisease).text = DiseasePresentation.name(this, record.disease, record.modelLabel)
        findViewById<TextView>(R.id.textDetailModelLabel).text = getString(
            R.string.model_label_format,
            record.modelLabel
        )
        findViewById<TextView>(R.id.textDetailConfidence).text = getString(
            R.string.history_confidence_format,
            record.confidence * 100f
        )
        uncertain = record.uncertain
        confidence = record.confidence
        recordLoaded = true
        findViewById<TextView>(R.id.textDetailGuidanceStatus).text = getString(
            if (record.guidanceAvailable) {
                R.string.guidance_available
            } else {
                R.string.guidance_not_reviewed
            }
        )
        val formattedTimestamp = DateFormat
            .getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT, resources.configuration.locales[0])
            .format(Date(record.timestamp))
        findViewById<TextView>(R.id.textDetailTimestamp).text = getString(
            R.string.history_saved_at_format,
            formattedTimestamp
        )
        findViewById<TextView>(R.id.textDetailLocation).text = if (
            record.latitude != null && record.longitude != null
        ) {
            getString(R.string.history_location_format, record.latitude, record.longitude)
        } else {
            getString(R.string.history_location_unavailable)
        }
        val bangla = resources.configuration.locales[0].language == "bn"
        findViewById<TextView>(R.id.textDetailSymptoms).text = if (bangla) {
            guidance?.symptoms ?: getString(R.string.generic_symptoms)
        } else record.symptoms
        findViewById<TextView>(R.id.textDetailTreatment).text = if (bangla) {
            guidance?.treatment ?: getString(R.string.generic_treatment)
        } else record.treatment
        findViewById<TextView>(R.id.textDetailPrevention).text = if (bangla) {
            guidance?.prevention ?: getString(R.string.generic_prevention)
        } else record.prevention
        applyUiMode()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_history_title)
            .setMessage(R.string.delete_history_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> deleteRecord() }
            .show()
    }

    private fun deleteRecord() {
        isDeleting = true
        deleteButton.isEnabled = false
        lifecycleScope.launch {
            try {
                val deletedRows = AppDatabase.getInstance(applicationContext)
                    .scanDao()
                    .deleteScanById(scanId)
                Toast.makeText(
                    this@HistoryDetailActivity,
                    if (deletedRows > 0) {
                        R.string.history_deleted
                    } else {
                        R.string.history_record_missing
                    },
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                isDeleting = false
                deleteButton.isEnabled = true
                Toast.makeText(
                    this@HistoryDetailActivity,
                    R.string.history_delete_error,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_DELETING, isDeleting)
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val EXTRA_SCAN_ID = "extra_scan_id"
        private const val INVALID_SCAN_ID = -1L
        private const val STATE_DELETING = "state_deleting"
    }
}
