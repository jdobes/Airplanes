package cz.owny.airplanes

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
//import android.util.Log
import android.view.WindowManager
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

import androidx.core.graphics.toColorInt
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.geojson.FeatureCollection

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this, "", WellKnownTileServer.MapLibre)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            MapContent(savedInstanceState)
        }
    }
}

@Composable
fun MapContent(savedInstanceState: Bundle?) {
    val viewModel: MainViewModel = viewModel()
    val aircraft by viewModel.aircraft.collectAsState()
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val lifecycle = activity.lifecycle

    val mapView = remember { MapView(context) }
    var mapLibreMap by remember { mutableStateOf<org.maplibre.android.maps.MapLibreMap?>(null) }
    var locationSource by remember { mutableStateOf<GeoJsonSource?>(null) }
    var userLatLng by remember { mutableStateOf<LatLng?>(null) }

    DisposableEffect(lifecycle) {
        var created = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> if (!created) {
                    mapView.onCreate(savedInstanceState)
                    created = true
                }
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isAttributionEnabled = false
            map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                /*style.layers.forEach { layer ->
                    Log.d("Airplanes", "Layer: ${layer.id}")
                }*/
                style.removeLayer("highway-shield-non-us")
                locationSource = GeoJsonSource("user-location")
                style.addSource(locationSource!!)
                style.addLayer(
                    CircleLayer("user-location-layer", "user-location")
                        .withProperties(
                            PropertyFactory.circleColor("#4285F4".toColorInt()),
                            PropertyFactory.circleRadius(6f),
                            PropertyFactory.circleStrokeColor(Color.WHITE),
                            PropertyFactory.circleStrokeWidth(3f)
                        )
                )

                try {
                    val planeBitmap = loadPlaneIcon(context)
                    style.addImage("plane-icon", planeBitmap)
                } catch (_: Exception) {}

                val aircraftSource = GeoJsonSource("aircraft")
                style.addSource(aircraftSource)
                style.addLayer(
                    SymbolLayer("aircraft-layer", "aircraft")
                        .withProperties(
                            PropertyFactory.iconImage("plane-icon"),
                            PropertyFactory.iconRotate(Expression.get("track")),
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconSize(0.5f),
                            PropertyFactory.iconAnchor("center")
                        )
                )
            }
        }
    }

    var permissionGranted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
    }

    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        permissionGranted = fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    LaunchedEffect(permissionGranted, mapLibreMap) {
        if (!permissionGranted || mapLibreMap == null) return@LaunchedEffect

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateDistanceMeters(10f)
            .build()

        try {
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let {
                    mapLibreMap?.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 10.0)
                    )
                }
            }
        } catch (_: SecurityException) {}

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    val latLng = LatLng(loc.latitude, loc.longitude)
                    userLatLng = latLng
                    locationSource?.setGeoJson(
                        Feature.fromGeometry(Point.fromLngLat(loc.longitude, loc.latitude))
                    )
                    mapLibreMap?.animateCamera(
                        CameraUpdateFactory.newLatLng(latLng)
                    )
                }
            }
        }

        try {
            fusedClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
            kotlinx.coroutines.delay(Long.MAX_VALUE.milliseconds)
        } catch (_: SecurityException) {
        } finally {
            try {
                fusedClient.removeLocationUpdates(callback)
            } catch (_: SecurityException) {}
        }
    }

    LaunchedEffect(Unit) {
        while (userLatLng == null) {
            delay(500)
        }
        while (true) {
            userLatLng?.let { viewModel.fetchAircraft(it.latitude, it.longitude) }
            delay(5000)
        }
    }

    LaunchedEffect(aircraft) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val features = aircraft.map { a ->
            Feature.fromGeometry(Point.fromLngLat(a.lon!!, a.lat!!)).apply {
                addNumberProperty("track", (a.track ?: 0f).toDouble())
                addStringProperty("hex", a.hex)
                a.flight?.let { addStringProperty("flight", it) }
                a.gs?.let { addNumberProperty("gs", it.toDouble()) }
            }
        }
        map.getStyle { style ->
            val source = style.getSourceAs<GeoJsonSource>("aircraft")
            source?.setGeoJson(FeatureCollection.fromFeatures(features))
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize()
    )
}

private fun loadPlaneIcon(context: Context): Bitmap {
    return BitmapFactory.decodeStream(context.assets.open("plane.png"))!!
}

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
