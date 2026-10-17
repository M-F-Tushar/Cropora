package com.cropora

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.cropora.data.DiseaseRepository
import com.cropora.database.ScanRecord
import com.cropora.network.ServerPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdvancedHomeFragment : AdvancedTabFragment(R.layout.fragment_adv_home) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.advHomeHero).clipToOutline = true

        view.findViewById<View>(R.id.advHomeSettings).setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        view.findViewById<View>(R.id.advHomeStartScan).setOnClickListener { navigator.startScan() }
        view.findViewById<View>(R.id.advHomeScansTile).setOnClickListener { navigator.openTab(AdvancedTab.SCANS) }
        view.findViewById<View>(R.id.advHomeSeeAll).setOnClickListener { navigator.openTab(AdvancedTab.SCANS) }
        view.findViewById<View>(R.id.advHomeLibraryTile).setOnClickListener { navigator.openTab(AdvancedTab.LIBRARY) }
        view.findViewById<View>(R.id.advHomeHealthCard).setOnClickListener { navigator.openTab(AdvancedTab.INSIGHTS) }
        view.findViewById<View>(R.id.advHomeEngineCloud).setOnClickListener { navigator.startScan(offline = false) }
        view.findViewById<View>(R.id.advHomeEngineDevice).setOnClickListener { navigator.startScan(offline = true) }
        view.findViewById<View>(R.id.advHomeSample).setOnClickListener {
            val context = requireContext()
            startActivity(ResultActivity.createIntent(context, MainActivity.samplePrediction(context)))
        }
        AdvancedUi.bindEmpty(
            view.findViewById(R.id.advHomeEmpty), R.drawable.ic_history,
            R.string.adv_no_scans_title, R.string.adv_no_scans_body
        ) { navigator.startScan() }

        loadLibraryCount(view.findViewById(R.id.advHomeLibraryCount))
        observeScans(::render)
    }

    override fun onResume() {
        super.onResume()
        val view = view ?: return
        view.findViewById<TextView>(R.id.advHomeGreeting).setText(AdvancedUi.greeting())
        view.findViewById<TextView>(R.id.advHomeServerHost).text =
            AdvancedUi.serverHost(ServerPreferences.getBaseUrl(requireContext()))
    }

    private fun loadLibraryCount(target: TextView) {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val count = try {
                withContext(Dispatchers.IO) {
                    DiseaseRepository.getInstance(context.applicationContext).getLocalizedDiseases(context).size
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            target.text = count?.toString() ?: "\u2013"
        }
    }

    private fun render(scans: List<ScanRecord>) {
        val view = view ?: return
        val context = requireContext()
        val counts = AdvancedUi.statusCounts(scans)
        val healthy = counts[AdvancedUi.Status.HEALTHY] ?: 0

        view.findViewById<TextView>(R.id.advHomeScansCount).text = scans.size.toString()
        view.findViewById<TextView>(R.id.advHomeHealthyPercent).text =
            if (scans.isEmpty()) "\u2013" else AdvancedUi.percent(context, healthy.toFloat() / scans.size)
        AdvancedUi.bindHealth(
            view.findViewById(R.id.advHomeDonut),
            listOf(R.id.advLegendHealthy, R.id.advLegendDiseased, R.id.advLegendReview).map { view.findViewById(it) },
            counts
        )

        val recentList = view.findViewById<LinearLayout>(R.id.advHomeRecentList)
        recentList.removeAllViews()
        val inflater = LayoutInflater.from(context)
        scans.take(RECENT_COUNT).forEach { record ->
            val row = inflater.inflate(R.layout.item_scan_history_advanced, recentList, false)
            HistoryAdapter.HistoryViewHolder(row, advanced = true).bind(record) {
                AdvancedUi.openScanRecord(context, it)
            }
            recentList.addView(row)
        }
        recentList.visibility = if (scans.isEmpty()) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.advHomeEmptyCard).visibility = if (scans.isEmpty()) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.advHomeSeeAll).visibility = if (scans.isEmpty()) View.GONE else View.VISIBLE
    }

    private companion object {
        const val RECENT_COUNT = 3
    }
}
