package com.kchienja.jetmap

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.location.LocationServices
import com.google.android.libraries.maps.CameraUpdateFactory
import com.google.android.libraries.maps.MapView
import com.google.android.libraries.maps.model.LatLng
import com.google.android.libraries.maps.model.MarkerOptions
import com.google.android.libraries.maps.model.PolylineOptions
import com.google.maps.android.ktx.awaitMap
import com.kchienja.jetmap.ui.theme.JetMapTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.IllegalStateException

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<MainActivityViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getLocationPermission()
        setContent {
            JetMapTheme {
                val newDestinationSelected = viewModel.isNewLocationSelected.observeAsState(false)

                val destination = if (newDestinationSelected.value) LatLng(viewModel.selectedLat.value, viewModel.selectedLng.value) else  LatLng(-1.491, 38.309)

                Surface(color = MaterialTheme.colors.background) {

                    LoadMapView(
                        viewModel = viewModel,
                        destination = destination
                    )

                }
            }
        }
    }

    private fun getLocationPermission(){
        if (ContextCompat.checkSelfPermission(this.applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        {
            viewModel.permissionGrand(true)
            getDeviceLocation()

        }else{
            Log.d("Exception", "Permission not granted")
        }
    }

    private  fun getDeviceLocation(){
        val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        try {
            if (viewModel.locationPermissionGranted.value ==true){
                val locationResult = fusedLocationProviderClient.lastLocation

                locationResult.addOnCompleteListener {
                        task ->
                    if (task.isSuccessful){
                        val lastKnownLocation = task.result

                        if (lastKnownLocation != null){
                            viewModel.currentUserGeoCOord(
                                LatLng(
                                    lastKnownLocation.altitude,
                                    lastKnownLocation.longitude
                                )
                            )
                        }
                    }else{
                        Log.d("Exception"," Current User location is null")
                    }
                }

            }

        }catch (e: SecurityException){
            Log.d("Exception", "Exception:  $e.message.toString()")
        }
    }
}

@Composable
fun LoadMapView(viewModel: MainActivityViewModel, destination: LatLng){

    val  mapView = rememberMapViewLifecycle()

    Box(modifier = Modifier.fillMaxSize()){
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colors.background,
                )
        ) {
            AndroidView({mapView}){
                CoroutineScope(Dispatchers.Main).launch {
                    val map = mapView.awaitMap()
                    map.uiSettings.isZoomControlsEnabled = true

                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(destination, 6f))

                    val markerOptions = MarkerOptions().title("Start position").position(viewModel.pickUp)
                    map.addMarker(markerOptions)

                    val markerOptionsDestination = MarkerOptions().title("Destination").position(destination)

                    map.addMarker(markerOptionsDestination)

                    map.addPolyline(PolylineOptions().clickable(true).add(viewModel.pickUp, destination).width(4f).geodesic(true))

                    map.setOnMapLongClickListener { c ->
                        viewModel.getSelectedLocation(LatLng(c.latitude, c.longitude))
                        viewModel.updateSelectedLocation(true)
                    }
                }
            }
        }
    }
}



@Composable
fun rememberMapViewLifecycle(): MapView{
    val context = LocalContext.current

    val mapView = remember {
        MapView(context).apply { id = R.id.map }
    }

    val lifeCycleObserver = rememberMapLifecycleObserver(mapView)
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle){
        lifecycle.addObserver(lifeCycleObserver)
        onDispose { lifecycle.removeObserver(lifeCycleObserver) }
    }

    return  mapView
}

@Composable
fun rememberMapLifecycleObserver(mapView: MapView): LifecycleEventObserver = remember(mapView) {
    LifecycleEventObserver { _, event ->
        when(event){
            Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle())
            Lifecycle.Event.ON_START -> mapView.onStart()
            Lifecycle.Event.ON_RESUME -> mapView.onResume()
            Lifecycle.Event.ON_PAUSE -> mapView.onPause()
            Lifecycle.Event.ON_STOP -> mapView.onStop()
            Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
            else -> throw IllegalStateException()
        }
    }
}