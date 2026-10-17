package com.cropora

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cropora.database.AppDatabase
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var recyclerHistory: RecyclerView
    private lateinit var textHistoryEmpty: TextView

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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppDatabase.getInstance(applicationContext)
                    .scanDao()
                    .observeAllScans()
                    .catch {
                        historyAdapter.submitList(emptyList())
                        recyclerHistory.visibility = View.GONE
                        textHistoryEmpty.setText(R.string.history_load_error)
                        textHistoryEmpty.visibility = View.VISIBLE
                    }
                    .collect { scans ->
                        historyAdapter.submitList(scans)
                        textHistoryEmpty.setText(R.string.history_empty)
                        val hasHistory = scans.isNotEmpty()
                        recyclerHistory.visibility = if (hasHistory) View.VISIBLE else View.GONE
                        textHistoryEmpty.visibility = if (hasHistory) View.GONE else View.VISIBLE
                    }
            }
        }
    }
}
