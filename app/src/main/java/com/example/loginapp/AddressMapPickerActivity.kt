package com.example.loginapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.loginapp.databinding.ActivityAddressMapPickerBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Tela de seleção de localização usando OSMDroid (OpenStreetMap)
 */
class AddressMapPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddressMapPickerBinding
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var selectedMarker: Marker? = null
    private var selectedPoint: GeoPoint? = null

    companion object {
        private const val REQUEST_LOCATION_PERMISSIONS = 5010
        const val EXTRA_LAT = "selected_lat"
        const val EXTRA_LNG = "selected_lng"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configurar user agent para OSMDroid (evita ban de tiles)
        Configuration.getInstance().userAgentValue = packageName

        binding = ActivityAddressMapPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupMap()
        setupActions()
        requestLocationIfNeeded()
    }

    private fun setupMap() {
        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.setMultiTouchControls(true)
        binding.map.controller.setZoom(16.0)

        // Listener de clique para escolher ponto
        binding.map.setOnTouchListener { _, _ -> false }
        binding.map.overlays.add(object : org.osmdroid.views.overlay.Overlay() {
            override fun onSingleTapConfirmed(e: android.view.MotionEvent?, mapView: org.osmdroid.views.MapView?): Boolean {
                e ?: return false
                mapView ?: return false
                val proj = mapView.projection
                val geoPoint = proj.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                setSelectedPoint(geoPoint)
                return true
            }
        })
    }

    private fun setupActions() {
        binding.btnMyLocation.setOnClickListener {
            enableMyLocation { point ->
                setSelectedPoint(point)
                centerMap(point)
            }
        }

        binding.btnSaveLocation.setOnClickListener {
            val point = selectedPoint
            if (point == null) {
                Toast.makeText(this, "Selecione um ponto no mapa", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val data = intent
            data.putExtra(EXTRA_LAT, point.latitude)
            data.putExtra(EXTRA_LNG, point.longitude)
            setResult(RESULT_OK, data)
            finish()
        }

        binding.btnClose.setOnClickListener { finish() }
    }

    private fun requestLocationIfNeeded() {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            enableMyLocation { point -> centerMap(point) }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_LOCATION_PERMISSIONS
            )
        }
    }

    private fun enableMyLocation(onFirstFix: (GeoPoint) -> Unit) {
        if (myLocationOverlay == null) {
            myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), binding.map).apply {
                enableMyLocation()
                enableFollowLocation()
                runOnFirstFix {
                    myLocation?.let { loc ->
                        val point = GeoPoint(loc.latitude, loc.longitude)
                        runOnUiThread { onFirstFix(point) }
                    }
                }
            }
            binding.map.overlays.add(myLocationOverlay)
        } else {
            myLocationOverlay?.enableMyLocation()
        }
    }

    private fun setSelectedPoint(point: GeoPoint) {
        selectedPoint = point
        if (selectedMarker == null) {
            selectedMarker = Marker(binding.map).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            binding.map.overlays.add(selectedMarker)
        }
        selectedMarker?.position = point
        selectedMarker?.title = "Local selecionado"
        binding.tvSelectedCoords.text = String.format("Lat: %.5f, Lng: %.5f", point.latitude, point.longitude)
        centerMap(point)
        binding.map.invalidate()
    }

    private fun centerMap(point: GeoPoint) {
        binding.map.controller.animateTo(point)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSIONS) {
            val granted = grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }
            if (granted) {
                enableMyLocation { point -> centerMap(point) }
            } else {
                Toast.makeText(this, "Permissão de localização negada. Você ainda pode tocar no mapa para escolher.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
    }
}
