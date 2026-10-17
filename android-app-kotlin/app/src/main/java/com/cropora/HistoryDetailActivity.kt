package com.cropora

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.cropora.database.AppDatabase
import com.cropora.database.ScanRecord
import com.cropora.data.Disease
import com.cropora.data.DiseasePresentation
import com.cropora.data.DiseaseRepository
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.text.DateFormat
import java.util.Date
import java.io.IOException
import org.xmlpull.v1.XmlPullParserException

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryDetailActivity : ModeAwareActivity() {
    private var scanId: Long = INVALID_SCAN_ID
    private var isDeleting = false
    private lateinit var deleteButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(if (advanced) R.layout.activity_history_detail_advanced else R.layout.activity_history_detail)
        if (advanced) {
            findViewById<View>(R.id.advBack).setOnClickListener { finish() }
        }

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
        val hasLocation = record.latitude != null && record.longitude != null
        findViewById<TextView>(R.id.textDetailDisease).text = DiseasePresentation.name(this, record.disease, record.modelLabel)
        findViewById<TextView>(R.id.textDetailUncertain).setText(
            if (record.uncertain) R.string.result_uncertain else R.string.result_confident
        )
        findViewById<TextView>(R.id.textDetailGuidanceStatus).text = getString(
            if (record.guidanceAvailable) {
                R.string.guidance_available
            } else {
                R.string.guidance_not_reviewed
            }
        )
        if (advanced) {
            renderAdvancedRecord(record, hasLocation)
        } else {
            findViewById<TextView>(R.id.textDetailModelLabel).text = getString(
                R.string.model_label_format,
                record.modelLabel
            )
            findViewById<TextView>(R.id.textDetailConfidence).text = getString(
                R.string.history_confidence_format,
                record.confidence * 100f
            )
            val formattedTimestamp = DateFormat
                .getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT, resources.configuration.locales[0])
                .format(Date(record.timestamp))
            findViewById<TextView>(R.id.textDetailTimestamp).text = getString(
                R.string.history_saved_at_format,
                formattedTimestamp
            )
            findViewById<TextView>(R.id.textDetailLocation).text = if (hasLocation) {
                getString(R.string.history_location_format, record.latitude, record.longitude)
            } else {
                getString(R.string.history_location_unavailable)
            }
        }
        val bangla = resources.configuration.locales[0].language == "bn"
        findViewById<TextView>(if (advanced) R.id.advCareSigns else R.id.textDetailSymptoms).text = if (bangla) {
            guidance?.symptoms ?: getString(R.string.generic_symptoms)
        } else record.symptoms
        findViewById<TextView>(if (advanced) R.id.advCareTreatment else R.id.textDetailTreatment).text = if (bangla) {
            guidance?.treatment ?: getString(R.string.generic_treatment)
        } else record.treatment
        findViewById<TextView>(if (advanced) R.id.advCarePrevention else R.id.textDetailPrevention).text = if (bangla) {
            guidance?.prevention ?: getString(R.string.generic_prevention)
        } else record.prevention
    }

    private fun renderAdvancedRecord(record: ScanRecord, hasLocation: Boolean) {
        val status = AdvancedUi.status(record)
        val percent = ResultTextFormatter.confidencePercent(record.confidence.coerceIn(0f, 1f))
        findViewById<TextView>(R.id.advDetailStatusPill).apply {
            AdvancedUi.bindStatusPill(this, status)
            visibility = View.VISIBLE
        }
        findViewById<CircularProgressIndicator>(R.id.advDetailRing).apply {
            progress = percent
            AdvancedUi.tintRing(this, status)
        }
        findViewById<TextView>(R.id.textDetailConfidence).apply {
            text = AdvancedUi.percent(this@HistoryDetailActivity, record.confidence)
            contentDescription = getString(R.string.confidence_format, percent)
        }
        findViewById<View>(R.id.advDetailReviewBanner).visibility =
            if (record.uncertain) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.textDetailModelLabel).text = record.modelLabel
        findViewById<TextView>(R.id.textDetailTimestamp).text =
            AdvancedUi.dateTime(this, record.timestamp, DateFormat.LONG)
        findViewById<TextView>(R.id.textDetailLocation).text = if (hasLocation) {
            getString(R.string.adv_location_value, record.latitude, record.longitude)
        } else {
            getString(R.string.adv_location_none)
        }
        findViewById<View>(R.id.advDetailLocationChevron).visibility = if (hasLocation) View.VISIBLE else View.GONE
        findViewById<View>(R.id.advDetailLocationRow).apply {
            setOnClickListener(if (hasLocation) View.OnClickListener {
                openMap(record.latitude ?: return@OnClickListener, record.longitude ?: return@OnClickListener)
            } else null)
            // setOnClickListener always makes the view clickable, so set this afterwards.
            isClickable = hasLocation
            isFocusable = hasLocation
            contentDescription = if (hasLocation) {
                getString(R.string.adv_open_map) + ", " + findViewById<TextView>(R.id.textDetailLocation).text
            } else {
                null
            }
        }
    }

    private fun openMap(latitude: Double, longitude: Double) {
        val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.adv_no_map_app, Toast.LENGTH_SHORT).show()
        }
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
