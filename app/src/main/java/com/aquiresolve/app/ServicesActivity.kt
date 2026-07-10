package com.aquiresolve.app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.aquiresolve.app.adapters.ServiceCategoriesAdapter
import com.aquiresolve.app.adapters.ServiceTypesAdapter
import com.aquiresolve.app.databinding.ActivityServicesBinding
import com.aquiresolve.app.models.ServicePricing
import com.aquiresolve.app.models.ServiceType
import com.aquiresolve.app.utils.ServiceNicheCatalog
import com.aquiresolve.app.utils.ServiceSearchHelper
import kotlinx.coroutines.launch

class ServicesActivity : AppCompatActivity(), InsetsSelfManaged {

    private lateinit var binding: ActivityServicesBinding
    private lateinit var serviceManager: FirebaseServiceManager
    private lateinit var categoriesAdapter: ServiceCategoriesAdapter
    private val floatingMic = FloatingMicHelper()

    private var searchQuery = ""
    private var searchResultsAdapter: ServiceTypesAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServicesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        serviceManager = FirebaseServiceManager()

        setupWindowInsets()
        setupUI()
        setupSearch()
        floatingMic.attach(this)
        loadCategories()

        intent.getStringExtra("search_query")?.takeIf { it.isNotEmpty() }?.let { q ->
            binding.etSearch.setText(q)
            binding.etSearch.setSelection(q.length)
        }
    }

    private fun setupWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.updatePadding(top = bars.top)
            binding.contentScroll.updatePadding(bottom = dpToPx(32) + bars.bottom)
            insets
        }
    }

    private fun setupUI() {
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_color)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.background_color)

        binding.btnBack.setOnClickListener { finish() }
        binding.rvSearchResults.layoutManager = LinearLayoutManager(this)

        categoriesAdapter = ServiceCategoriesAdapter(emptyList()) { item ->
            openCategory(item.name)
        }
        binding.rvCategories.layoutManager = GridLayoutManager(this, 2)
        binding.rvCategories.adapter = categoriesAdapter

        hideEmptyState()
        hideLoadingState()
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString() ?: ""
                filterContent()
            }
        })
    }

    private fun loadCategories() {
        showLoadingState()
        lifecycleScope.launch {
            CatalogRepository.load()
            val niches = ServiceNicheCatalog.selectableNichesWithIcons()
                .map { ServiceCategoriesAdapter.NicheItem(it.name, it.icon) }
            runOnUiThread {
                hideLoadingState()
                categoriesAdapter.updateNiches(niches)
                if (searchQuery.isNotEmpty()) filterContent()
            }
        }
    }

    private fun filterContent() {
        if (searchQuery.isEmpty()) {
            binding.rvCategories.visibility = View.VISIBLE
            binding.rvSearchResults.visibility = View.GONE
            hideEmptyState()
            return
        }

        val searchResults = ServiceSearchHelper.search(searchQuery)
        if (searchResults.isNotEmpty()) {
            showSearchResultsWithPrices(searchResults)
            binding.rvCategories.visibility = View.VISIBLE
            hideEmptyState()
        } else {
            val matchedCategory = ServiceSearchHelper.searchCategory(searchQuery)
            if (matchedCategory != null) {
                showCategoryServicesWithPrices(matchedCategory)
                binding.rvCategories.visibility = View.VISIBLE
                hideEmptyState()
            } else {
                binding.rvSearchResults.visibility = View.GONE
                binding.rvCategories.visibility = View.GONE
                showEmptyState()
            }
        }
    }

    private fun showSearchResultsWithPrices(results: List<ServiceSearchHelper.SearchResult>) {
        val seen = mutableSetOf<String>()
        val serviceTypes = mutableListOf<ServiceType>()
        for (result in results) {
            val key = "${result.category}|${result.serviceType}"
            if (seen.add(key)) {
                val price = ServicePricing.getPrice(result.category, result.serviceType)
                serviceTypes.add(ServiceType(
                    id = "search_${key.hashCode()}",
                    categoryId = result.category,
                    name = result.serviceType,
                    description = "Categoria: ${result.category}",
                    estimatedPrice = price ?: ServicePricing.getDefaultPrice(result.category),
                    isActive = true
                ))
            }
        }
        setupResultsAdapter(serviceTypes)
        binding.rvSearchResults.visibility = View.VISIBLE
    }

    private fun showCategoryServicesWithPrices(category: String) {
        val serviceNames = ServicePricing.getServicesForCategory(category)
        val serviceTypes = serviceNames.mapIndexed { index, name ->
            val price = ServicePricing.getPrice(category, name)
            ServiceType(
                id = "cat_${category.hashCode()}_$index",
                categoryId = category,
                name = name,
                description = "Categoria: $category",
                estimatedPrice = price ?: ServicePricing.getDefaultPrice(category),
                isActive = true
            )
        }
        setupResultsAdapter(serviceTypes)
        binding.rvSearchResults.visibility = View.VISIBLE
    }

    private fun setupResultsAdapter(serviceTypes: List<ServiceType>) {
        searchResultsAdapter = ServiceTypesAdapter(
            serviceTypes = serviceTypes,
            onServiceClick = { serviceType ->
                val intent = Intent(this, CreateOrderActivity::class.java).apply {
                    putExtra("service_category_name", serviceType.categoryId)
                    putExtra("search_query", serviceType.name)
                }
                startActivity(intent)
            },
            onFavoriteClick = { _, _ -> },
            serviceManager = serviceManager
        )
        binding.rvSearchResults.adapter = searchResultsAdapter
    }

    private fun openCategory(categoryName: String) {
        val intent = Intent(this, CreateOrderActivity::class.java).apply {
            putExtra("service_category_name", categoryName)
        }
        startActivity(intent)
    }

    private fun showLoadingState() { binding.loadingState.visibility = View.VISIBLE }
    private fun hideLoadingState() { binding.loadingState.visibility = View.GONE }
    private fun showEmptyState() { binding.emptyState.visibility = View.VISIBLE }
    private fun hideEmptyState() { binding.emptyState.visibility = View.GONE }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dpToPx(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        floatingMic.detach()
        super.onDestroy()
    }
}
