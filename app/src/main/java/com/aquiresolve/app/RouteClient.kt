package com.aquiresolve.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.TlsVersion
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.util.concurrent.TimeUnit

/**
 * Cliente de roteamento de carro compartilhado. Busca a rota PRIMÁRIO no proxy do
 * nosso backend (`/api/route`, TLS confiável mesmo em Android < TLS 1.3) e cai no
 * OSRM público como fallback. Mesma estratégia já validada no mini-mapa do prestador.
 */
object RouteClient {

    data class Route(
        val points: List<GeoPoint>,
        val distanceMeters: Double,
        val durationSeconds: Double
    ) {
        val distanceKm: Double get() = distanceMeters / 1000.0
    }

    private val routeProxyBase: String =
        BuildConfig.PAYMENTS_API_BASE_URL.removeSuffix("/").removeSuffix("/payments") + "/route"

    private val osrmHosts = listOf(
        "https://router.project-osrm.org",
        "https://routing.openstreetmap.de/routed-car"
    )

    private val client: OkHttpClient by lazy {
        val tlsSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
            .allEnabledCipherSuites()
            .build()
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .connectionSpecs(listOf(tlsSpec, ConnectionSpec.CLEARTEXT))
            .build()
    }

    /** Calcula a rota de carro de [from] até [to]. Retorna null se nenhuma fonte responder. */
    suspend fun fetchRoute(from: GeoPoint, to: GeoPoint): Route? = withContext(Dispatchers.IO) {
        // 1) Proxy do backend.
        try {
            val url = "$routeProxyBase?from=${from.longitude},${from.latitude}" +
                "&to=${to.longitude},${to.latitude}"
            val request = Request.Builder().url(url).header("User-Agent", "AquiResolve/1.0").build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    val json = JSONObject(body)
                    if (json.optBoolean("ok", false) && json.has("coordinates")) {
                        val coords = json.getJSONArray("coordinates")
                        val points = ArrayList<GeoPoint>(coords.length())
                        for (i in 0 until coords.length()) {
                            val c = coords.getJSONArray(i)
                            points.add(GeoPoint(c.getDouble(1), c.getDouble(0)))
                        }
                        if (points.isNotEmpty()) {
                            return@withContext Route(points, json.getDouble("distance"), json.getDouble("duration"))
                        }
                    }
                }
            }
        } catch (_: Exception) { /* tenta o fallback */ }

        // 2) OSRM público direto.
        val path = "/route/v1/driving/" +
            "${from.longitude},${from.latitude};${to.longitude},${to.latitude}" +
            "?overview=full&geometries=geojson"
        for (host in osrmHosts) {
            try {
                val request = Request.Builder().url(host + path).header("User-Agent", "AquiResolve/1.0").build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful && body != null) {
                        val routes = JSONObject(body).getJSONArray("routes")
                        if (routes.length() > 0) {
                            val route = routes.getJSONObject(0)
                            val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
                            val points = ArrayList<GeoPoint>(coords.length())
                            for (i in 0 until coords.length()) {
                                val c = coords.getJSONArray(i)
                                points.add(GeoPoint(c.getDouble(1), c.getDouble(0)))
                            }
                            return@withContext Route(points, route.getDouble("distance"), route.getDouble("duration"))
                        }
                    }
                }
            } catch (_: Exception) { /* próximo host */ }
        }
        null
    }
}
