package com.cropora

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cropora.database.ScanRecord
import com.google.android.material.chip.ChipGroup

class AdvancedScansFragment : AdvancedTabFragment(R.layout.fragment_adv_scans) {
    private var scans: List<ScanRecord> = emptyList()
    private lateinit var adapter: HistoryAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = HistoryAdapter(advanced = true) { record ->
            AdvancedUi.openScanRecord(requireContext(), record)
        }
        view.findViewById<RecyclerView>(R.id.advScansList).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@AdvancedScansFragment.adapter
        }
        view.findViewById<ChipGroup>(R.id.advScansFilter).setOnCheckedStateChangeListener { _, _ -> render() }
        observeScans { latest ->
            scans = latest
            render()
        }
    }

    private fun render() {
        val view = view ?: return
        val filter = when (view.findViewById<ChipGroup>(R.id.advScansFilter).checkedChipId) {
            R.id.advFilterHealthy -> AdvancedUi.Status.HEALTHY
            R.id.advFilterDiseased -> AdvancedUi.Status.DISEASE
            R.id.advFilterReview -> AdvancedUi.Status.REVIEW
            else -> null
        }
        val visible = if (filter == null) scans else scans.filter { AdvancedUi.status(it) == filter }
        adapter.submitList(visible)

        view.findViewById<TextView>(R.id.advScansCount).text =
            resources.getQuantityString(R.plurals.adv_scans_count, scans.size, scans.size)
        view.findViewById<View>(R.id.advScansList).visibility = if (visible.isEmpty()) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.advScansEmptyScroll).visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        val empty = view.findViewById<View>(R.id.advScansEmpty)
        if (scans.isEmpty()) {
            AdvancedUi.bindEmpty(empty, R.drawable.ic_history, R.string.adv_no_scans_title, R.string.adv_no_scans_body) {
                navigator.startScan()
            }
        } else {
            AdvancedUi.bindEmpty(empty, R.drawable.adv_ic_search, R.string.adv_filter_empty, null, null)
        }
    }
}
