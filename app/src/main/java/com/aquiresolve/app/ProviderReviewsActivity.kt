package com.aquiresolve.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aquiresolve.app.databinding.ActivityProviderReviewsBinding
import com.aquiresolve.app.databinding.ItemProviderReviewBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * ProviderReviewsActivity - Lista todas as avaliações públicas de um prestador.
 *
 * Exibe:
 * - Média geral com estrelas
 * - Distribuição de notas (5★, 4★, 3★, 2★, 1★)
 * - Lista de reviews com nome do cliente, nota, comentário, data
 */
class ProviderReviewsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProviderReviewsBinding
    private var providerId: String = ""
    private var providerName: String = ""

    private val reviews = mutableListOf<FirebaseOrderManager.ProviderReview>()
    private val adapter = ReviewAdapter(reviews)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProviderReviewsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        providerId = intent.getStringExtra("provider_id") ?: ""
        providerName = intent.getStringExtra("provider_name") ?: "Prestador"

        binding.btnBack.setOnClickListener { finish() }
        binding.recyclerViewReviews.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewReviews.adapter = adapter

        loadReviews()
    }

    private fun loadReviews() {
        if (providerId.isBlank()) {
            showEmptyState()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerViewReviews.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutStatsHeader.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val orderManager = FirebaseOrderManager()

                // Carrega estatísticas e reviews em paralelo
                val stats = orderManager.getProviderReviewStats(providerId)
                val reviewList = orderManager.getProviderReviews(providerId)

                reviews.clear()
                reviews.addAll(reviewList)
                adapter.notifyDataSetChanged()

                updateStatsHeader(stats)

                if (reviews.isEmpty()) {
                    showEmptyState()
                } else {
                    binding.recyclerViewReviews.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                showEmptyState()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateStatsHeader(stats: Map<String, Any>) {
        val average = (stats["averageRating"] as? Number)?.toDouble() ?: 0.0
        val total = (stats["totalReviews"] as? Number)?.toInt() ?: 0

        if (total == 0) {
            binding.layoutStatsHeader.visibility = View.GONE
            return
        }

        binding.layoutStatsHeader.visibility = View.VISIBLE
        binding.tvAverageRating.text = String.format(Locale("pt", "BR"), "%.1f", average)
        binding.tvTotalReviews.text = if (total == 1) "1 avaliação" else "$total avaliações"

        // Distribution bars
        val distribution = stats["distribution"] as? Map<*, *> ?: return
        val layoutDist = binding.layoutDistribution
        layoutDist.removeAllViews()

        // Show 5★ to 1★
        for (stars in 5 downTo 1) {
            val count = ((distribution[stars.toLong()] ?: distribution[stars.toDouble()] ?: distribution[stars]) as? Number)?.toInt() ?: 0
            val percent = if (total > 0) (count.toFloat() / total * 100).toInt() else 0

            val row = layoutInflater.inflate(android.R.layout.simple_list_item_1, layoutDist, false) as? TextView ?: continue
            row.text = "${"★".repeat(stars)}${"☆".repeat(5 - stars)}  $count ($percent%)"
            row.setTextColor(ContextCompat.getColor(this, R.color.gray_500))
            row.textSize = 12f
            layoutDist.addView(row)
        }
    }

    private fun showEmptyState() {
        binding.recyclerViewReviews.visibility = View.GONE
        binding.layoutStatsHeader.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
    }

    // ---- RecyclerView Adapter ----

    private inner class ReviewAdapter(
        private val items: List<FirebaseOrderManager.ProviderReview>
    ) : RecyclerView.Adapter<ReviewAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemBinding = ItemProviderReviewBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(private val b: ItemProviderReviewBinding) :
            RecyclerView.ViewHolder(b.root) {

            fun bind(review: FirebaseOrderManager.ProviderReview) {
                b.tvClientName.text = review.clientName.ifBlank { "Cliente" }
                b.ratingBar.rating = review.rating.toFloat()

                // Service info
                val serviceInfo = buildString {
                    if (review.serviceName.isNotBlank()) append(review.serviceName)
                    if (review.serviceType.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(review.serviceType)
                    }
                }
                if (serviceInfo.isNotEmpty()) {
                    b.tvServiceInfo.text = serviceInfo
                    b.tvServiceInfo.visibility = View.VISIBLE
                } else {
                    b.tvServiceInfo.visibility = View.GONE
                }

                // Review text
                val reviewText = review.review?.trim()
                if (!reviewText.isNullOrEmpty()) {
                    b.tvReviewText.text = reviewText
                    b.tvReviewText.visibility = View.VISIBLE
                } else {
                    b.tvReviewText.visibility = View.GONE
                }

                // Detailed ratings
                val details = mutableListOf<String>()
                review.qualityRating?.let { if (it > 0) details.add("Qualidade ${it}★") }
                review.punctualityRating?.let { if (it > 0) details.add("Pontualidade ${it}★") }
                review.communicationRating?.let { if (it > 0) details.add("Comunicação ${it}★") }
                review.cleanlinessRating?.let { if (it > 0) details.add("Limpeza ${it}★") }

                if (details.isNotEmpty()) {
                    b.tvDetailedRatings.text = details.joinToString(" · ")
                    b.layoutDetailedRatings.visibility = View.VISIBLE
                } else {
                    b.layoutDetailedRatings.visibility = View.GONE
                }

                // Tags
                if (review.tags.isNotEmpty()) {
                    b.tvTags.text = review.tags.joinToString("  ") { "#$it" }
                    b.tvTags.visibility = View.VISIBLE
                } else {
                    b.tvTags.visibility = View.GONE
                }

                // Date
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
                dateFormat.timeZone = TimeZone.getTimeZone("America/Sao_Paulo")
                b.tvDate.text = dateFormat.format(review.createdAt.toDate())
            }
        }
    }
}
