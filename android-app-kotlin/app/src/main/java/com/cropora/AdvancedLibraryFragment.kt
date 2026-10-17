package com.cropora

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cropora.data.Disease
import com.cropora.data.DiseasePresentation
import com.cropora.data.DiseaseRepository
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParserException

class AdvancedLibraryFragment : AdvancedTabFragment(R.layout.fragment_adv_library) {
    private var diseases: List<Disease> = emptyList()
    private var selectedCrop: String? = null
    private var loadFailed = false
    private lateinit var adapter: DiseaseAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectedCrop = savedInstanceState?.getString(STATE_CROP) ?: selectedCrop
        adapter = DiseaseAdapter(advanced = true) { disease ->
            startActivity(
                Intent(requireContext(), DiseaseDetailActivity::class.java)
                    .putExtra(DiseaseDetailActivity.EXTRA_DISEASE_NAME, disease.name)
            )
        }
        view.findViewById<RecyclerView>(R.id.advLibraryList).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@AdvancedLibraryFragment.adapter
        }
        view.findViewById<TextView>(R.id.advLibrarySearch).doAfterTextChanged { render() }
        loadDiseases()
    }

    private fun loadDiseases() {
        val context = requireContext()
        val view = requireView()
        view.findViewById<View>(R.id.advLibraryProgress).visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                diseases = withContext(Dispatchers.IO) {
                    DiseaseRepository.getInstance(context.applicationContext).getLocalizedDiseases(context)
                }
                loadFailed = false
            } catch (_: IOException) {
                loadFailed = true
            } catch (_: XmlPullParserException) {
                loadFailed = true
            }
            view.findViewById<View>(R.id.advLibraryProgress).visibility = View.GONE
            buildCropChips(view.findViewById(R.id.advLibraryCrops))
            render()
        }
    }

    private fun buildCropChips(group: ChipGroup) {
        group.setOnCheckedStateChangeListener(null)
        group.removeAllViews()
        val crops = diseases.map { it.plant }.distinct().sorted()
        if (selectedCrop !in crops) selectedCrop = null
        val inflater = LayoutInflater.from(group.context)
        (listOf<String?>(null) + crops).forEach { crop ->
            val chip = inflater.inflate(R.layout.item_adv_filter_chip, group, false) as Chip
            chip.id = View.generateViewId()
            chip.tag = crop
            chip.text = crop ?: getString(R.string.adv_filter_all)
            group.addView(chip)
            if (crop == selectedCrop) chip.isChecked = true
        }
        group.setOnCheckedStateChangeListener { chipGroup, checkedIds ->
            selectedCrop = checkedIds.firstOrNull()?.let { chipGroup.findViewById<Chip>(it)?.tag as? String }
            render()
        }
    }

    private fun render() {
        val view = view ?: return
        if (view.findViewById<View>(R.id.advLibraryProgress).visibility == View.VISIBLE) return
        val context = requireContext()
        val query = view.findViewById<TextView>(R.id.advLibrarySearch).text.toString().trim()
        val visible = diseases.filter { disease ->
            (selectedCrop == null || disease.plant == selectedCrop) && (
                query.isEmpty() ||
                    DiseasePresentation.name(context, disease.name).contains(query, ignoreCase = true) ||
                    disease.plant.contains(query, ignoreCase = true) ||
                    disease.name.contains(query, ignoreCase = true)
                )
        }
        adapter.submitList(visible)

        view.findViewById<TextView>(R.id.advLibraryCount).text =
            resources.getQuantityString(R.plurals.adv_library_count, diseases.size, diseases.size)
        view.findViewById<View>(R.id.advLibraryList).visibility = if (visible.isEmpty()) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.advLibraryEmptyScroll).visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        AdvancedUi.bindEmpty(
            view.findViewById(R.id.advLibraryEmpty),
            if (loadFailed) R.drawable.adv_ic_warning else R.drawable.adv_ic_search,
            if (loadFailed) R.string.disease_library_error else R.string.adv_library_no_match,
            null, null
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CROP, selectedCrop)
    }

    private companion object {
        const val STATE_CROP = "state_selected_crop"
    }
}
