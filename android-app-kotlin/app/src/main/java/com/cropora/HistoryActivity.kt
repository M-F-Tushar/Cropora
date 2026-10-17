package com.cropora

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cropora.database.AppDatabase
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var recyclerHistory: RecyclerView
    private lateinit var textHistoryEmpty: TextView
    private lateinit var referenceHeader: View
    private lateinit var advancedAdapter: ConcatAdapter
    private var advanced = false
    private var hasHistory = false
    private var stateMessage = R.string.history_empty

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        recyclerHistory = findViewById(R.id.recyclerHistory)
        textHistoryEmpty = findViewById(R.id.textHistoryEmpty)
        historyAdapter = HistoryAdapter { record ->
            val intent = Intent(this, HistoryDetailActivity::class.java).apply {
                putExtra(HistoryDetailActivity.EXTRA_SCAN_ID, record.id)
            }
            startActivity(intent)
        }

        recyclerHistory.layoutManager = LinearLayoutManager(this)
        recyclerHistory.adapter = historyAdapter
        referenceHeader = findViewById(R.id.referenceHistoryHeader)
        (referenceHeader.parent as ViewGroup).removeView(referenceHeader)
        referenceHeader.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        referenceHeader.visibility = View.VISIBLE
        advancedAdapter = ConcatAdapter(ReferenceHeaderAdapter(referenceHeader), historyAdapter)
        listOf(R.id.referenceHistoryHeading, R.id.referenceHistorySection).forEach { id ->
            ViewCompat.setAccessibilityHeading(referenceHeader.findViewById(id), true)
        }
        ViewCompat.setAccessibilityHeading(findViewById(R.id.textHistoryTitle), true)
        recyclerHistory.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                if (view === referenceHeader) return
                view.setBackgroundResource(
                    if (advanced) R.drawable.reference_card_evidence else R.color.leaf_surface
                )
            }

            override fun onChildViewDetachedFromWindow(view: View) = Unit
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppDatabase.getInstance(applicationContext)
                    .scanDao()
                    .observeAllScans()
                    .catch {
                        historyAdapter.submitList(emptyList())
                        hasHistory = false
                        stateMessage = R.string.history_load_error
                        updateHistoryState()
                    }
                    .collect { scans ->
                        historyAdapter.submitList(scans)
                        stateMessage = R.string.history_empty
                        hasHistory = scans.isNotEmpty()
                        updateHistoryState()
                    }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val enabled = UiPreferences.isAdvanced(this)
        if (advanced != enabled) {
            advanced = enabled
            recyclerHistory.adapter = if (advanced) advancedAdapter else historyAdapter
        }
        findViewById<View>(R.id.textHistoryTitle).visibility = if (advanced) View.GONE else View.VISIBLE
        updateHistoryState()
    }

    private fun updateHistoryState() {
        recyclerHistory.visibility = if (advanced || hasHistory) View.VISIBLE else View.GONE
        textHistoryEmpty.setText(stateMessage)
        textHistoryEmpty.visibility = if (!advanced && !hasHistory) View.VISIBLE else View.GONE
        referenceHeader.findViewById<TextView>(R.id.referenceHistoryState).apply {
            setText(stateMessage)
            visibility = if (hasHistory) View.GONE else View.VISIBLE
        }
    }

    // Keeping the introduction in the list lets large text and short screens scroll naturally.
    private class ReferenceHeaderAdapter(private val header: View) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            object : RecyclerView.ViewHolder(header) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit

        override fun getItemCount(): Int = 1
    }
}
