package com.aquiresolve.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.aquiresolve.app.adapters.NotificationHistoryAdapter
import com.aquiresolve.app.databinding.ActivityNotificationHistoryBinding
import com.aquiresolve.app.models.AppNotification
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class NotificationHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationHistoryBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!FirebaseConfig.isInitialized()) FirebaseConfig.initialize(this)
        binding = ActivityNotificationHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_color)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.swipeRefresh.setOnRefreshListener { loadNotifications() }
        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        binding.rvNotifications.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        loadNotifications()
    }

    private fun loadNotifications() {
        val uid = auth.currentUser?.uid ?: return
        binding.progressBar.visibility = View.VISIBLE
        binding.layoutEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val snap = db.collection("notifications")
                    .whereEqualTo("userId", uid)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(100)
                    .get()
                    .await()

                val items = snap.documents.mapNotNull { doc ->
                    doc.toObject(AppNotification::class.java)?.copy(id = doc.id)
                }

                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false

                if (items.isEmpty()) {
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.rvNotifications.visibility = View.GONE
                } else {
                    binding.layoutEmpty.visibility = View.GONE
                    binding.rvNotifications.visibility = View.VISIBLE
                    binding.rvNotifications.adapter = NotificationHistoryAdapter(items)
                }

                // Marca todas como lidas
                markAllRead(uid)
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                binding.layoutEmpty.visibility = View.VISIBLE
            }
        }
    }

    private fun markAllRead(uid: String) {
        lifecycleScope.launch {
            try {
                val unread = db.collection("notifications")
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("isRead", false)
                    .get()
                    .await()

                val batch = db.batch()
                unread.documents.forEach { batch.update(it.reference, "isRead", true) }
                if (unread.documents.isNotEmpty()) batch.commit().await()
            } catch (_: Exception) {}
        }
    }
}
