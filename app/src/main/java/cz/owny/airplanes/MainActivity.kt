package cz.owny.airplanes

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.util.Log
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color as ComposeColor
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
import org.maplibre.android.geometry.LatLngBounds
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf

import androidx.core.graphics.toColorInt
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import cz.owny.airplanes.data.Aircraft
import cz.owny.airplanes.data.AircraftDetails
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
    var selectedAircraftSource by remember { mutableStateOf<GeoJsonSource?>(null) }
    var userLatLng by remember { mutableStateOf<LatLng?>(null) }
    var visibleBounds by remember { mutableStateOf<LatLngBounds?>(null) }

    val positionedAircraft = aircraft.filter { it.hasPosition() }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val selectedAircraft = positionedAircraft.getOrNull(selectedIndex)
    val selectedDetails by viewModel.selectedDetails.collectAsState()

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
                if (Config.DEBUG) {
                    style.layers.forEach { layer ->
                        Log.d("Airplanes", "Layer: ${layer.id}")
                    }
                }
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

                val selectedSource = GeoJsonSource("selected-aircraft")
                selectedAircraftSource = selectedSource
                style.addSource(selectedSource)
                style.addLayer(
                    CircleLayer("selected-aircraft-layer", "selected-aircraft")
                        .withProperties(
                            PropertyFactory.circleColor(Color.WHITE),
                            PropertyFactory.circleOpacity(0.4f),
                            PropertyFactory.circleRadius(20f),
                            PropertyFactory.circleStrokeColor(Color.WHITE),
                            PropertyFactory.circleStrokeWidth(2f)
                        )
                )
            }
            map.addOnCameraMoveListener {
                visibleBounds = map.projection.visibleRegion.latLngBounds
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
            delay(Long.MAX_VALUE.milliseconds)
        } catch (_: SecurityException) {
        } finally {
            try {
                fusedClient.removeLocationUpdates(callback)
            } catch (_: SecurityException) {}
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            visibleBounds?.let { bounds ->
                viewModel.fetchAircraft(
                    bounds.northEast.latitude,
                    bounds.southWest.latitude,
                    bounds.southWest.longitude,
                    bounds.northEast.longitude
                )
            }
            delay(5000.milliseconds)
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

    LaunchedEffect(aircraft, selectedIndex) {
        val source = selectedAircraftSource ?: return@LaunchedEffect
        if (selectedAircraft != null) {
            source.setGeoJson(
                Feature.fromGeometry(Point.fromLngLat(selectedAircraft.lon!!, selectedAircraft.lat!!))
            )
        } else {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }
    }

    LaunchedEffect(positionedAircraft.size) {
        val size = positionedAircraft.size
        if (size == 0) return@LaunchedEffect
        selectedIndex = selectedIndex.coerceIn(0, size - 1)
        while (true) {
            delay(5000.milliseconds)
            selectedIndex = (selectedIndex + 1) % size
        }
    }

    LaunchedEffect(selectedAircraft?.r, selectedAircraft?.flight) {
        viewModel.fetchDetails(selectedAircraft)
    }

    Row(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.weight(2f).fillMaxHeight()
        )
        AircraftDetailPanel(
            aircraft = selectedAircraft,
            details = selectedDetails,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }
}

@Composable
private fun AircraftDetailPanel(aircraft: Aircraft?, details: AircraftDetails?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (aircraft != null) {
                val photoUrl = details?.photoUrl
                val photoLink = details?.photoLink
                val photographer = details?.photographer
                if (photoUrl != null) {
                    val context = LocalContext.current
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(photoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .let { mod ->
                                    if (photoLink != null) mod.clickable {
                                        context.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(photoLink)
                                            )
                                        )
                                    } else mod
                                },
                            contentScale = ContentScale.Crop
                        )
                        if (photographer != null) {
                            Text(
                                text = "\u00a9 $photographer",
                                style = MaterialTheme.typography.labelSmall,
                                color = ComposeColor.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .background(
                                        ComposeColor.Black.copy(alpha = 0.5f),
                                        RoundedCornerShape(topEnd = 6.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Photo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                aircraft.t?.let { type ->
                    val desc = aircraft.desc ?: ""
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                HorizontalDivider()

                val originCode = details?.origin?.iataCode ?: details?.origin?.icaoCode
                val destCode = details?.destination?.iataCode ?: details?.destination?.icaoCode
                val originCity = details?.origin?.municipality
                val destCity = details?.destination?.municipality
                val originCountry = details?.origin?.countryIso
                val destCountry = details?.destination?.countryIso

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = originCode ?: "???",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (originCity != null || originCountry != null) {
                            Text(
                                text = buildString {
                                    originCity?.let { append(it) }
                                    originCountry?.let { append(" ($it)") }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Text(
                        text = "\u2192",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = destCode ?: "???",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (destCity != null || destCountry != null) {
                            Text(
                                text = buildString {
                                    destCity?.let { append(it) }
                                    destCountry?.let { append(" ($it)") }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                details?.airline?.name?.let {
                    Text(
                        "Airline: $it",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                aircraft.flight?.let { flight ->
                    Text(
                        "Flight: $flight",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                aircraft.r?.let { reg ->
                    val country = details?.ownerCountryName
                    Text(
                        text = if (country != null) "Reg: $reg ($country)" else "Reg: $reg",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                aircraft.altBaroMeters?.let {
                    Text(
                        "Alt: $it m",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No aircraft",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
}

private fun loadPlaneIcon(context: Context): Bitmap {
    return BitmapFactory.decodeStream(context.assets.open("plane.png"))!!
}

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
