package com.exa.android.reflekt.loopit.presentation.main.Home.Map

import android.annotation.SuppressLint
import androidx.core.widget.addTextChangedListener
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.exa.android.reflekt.loopit.data.remote.main.ViewModel.LocationViewModel
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import android.Manifest
import android.app.Activity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.isGranted
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.widget.Autocomplete
import com.google.maps.android.compose.MapProperties
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
// Location and Permissions
import com.google.maps.android.compose.*

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.viewinterop.AndroidView
import com.exa.android.reflekt.R
import com.exa.android.reflekt.loopit.util.model.profileUser
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
//import timber.log.Timber
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
//import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ChipDefaults
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.exa.android.reflekt.loopit.presentation.main.Home.component.ImageUsingCoil
import com.exa.android.reflekt.loopit.presentation.main.profile.components.extra_card.openUrl
import com.exa.android.reflekt.loopit.presentation.main.profile.components.setting.SettingsViewModel
import com.exa.android.reflekt.loopit.presentation.navigation.component.HomeRoute
import com.exa.android.reflekt.loopit.presentation.navigation.component.ProfileRoute
import com.google.accompanist.permissions.shouldShowRationale
import timber.log.Timber


@SuppressLint("UnrememberedMutableState", "MissingPermission")
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(navController: NavController, viewModel: LocationViewModel = hiltViewModel(), settingViewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val userId = auth.currentUser?.uid ?: return
    val userLocations by viewModel.userLocations.collectAsState()

    // Location and UI states
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    // var searchLocation by remember { mutableStateOf<LatLng?>(null) }
    val radius by viewModel.radius
    val selectedRoles by viewModel.selectedRoles
    val minRating by viewModel.minRating
    var showBottomSheet by remember { mutableStateOf(false) }
    val currUserProfile by viewModel.userProfile.collectAsState()
    var selectedUser by rememberSaveable (stateSaver = profileUser.Saver) {
        mutableStateOf<profileUser?>(null)
    }

    val roles by viewModel.roleSuggestions.collectAsState()

    // Bottom sheet state
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION
    // Permission and location clients
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val cameraPositionState = rememberCameraPositionState()

    val launchPermissionRequestState = rememberUpdatedState(locationPermissionState)
    var showPermissionDialog by remember { mutableStateOf(true) }

    // Places API
    val placesClient = remember { Places.createClient(context) }

    // Observe the selected location from the ViewModel
    val selectedLocation by viewModel.selectedLocation.collectAsState()

    val autocompleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val place = Autocomplete.getPlaceFromIntent(result.data)
            // searchLocation = place.latLng?.let { LatLng(it.latitude, it.longitude) }
        }
    }
    //update
    val locationManager = remember {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    var isGpsEnabled by remember { mutableStateOf(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) }
    var showGpsDialog by remember { mutableStateOf(false) }

    var roleSearchQuery by remember { mutableStateOf("") }
    val filteredRoles = roles.filter {
        it.contains(roleSearchQuery, ignoreCase = true)
    }
    // Fetch current user's role
    LaunchedEffect(userId) {
        viewModel.getUserProfile(userId)
    }
    // Update selectedRole when currUserProfile is available
    LaunchedEffect(currUserProfile) {
        currUserProfile.role.let { role ->
            if (selectedRoles.isEmpty()) { // Only set it if it's not already selected

            }
        }
    }

    LaunchedEffect(
        locationPermissionState.status,
        currentLocation,
        selectedRoles,
        radius,
        selectedLocation,
        minRating
    ) {
        if (locationPermissionState.status.isGranted && currentLocation != null) {
            val rolesAsString = selectedRoles.joinToString(",")
            val location = selectedLocation ?: currentLocation!!
            val zoomLevel = getZoomLevel(radius)
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(location, zoomLevel),
                durationMs = 500
            )
            Timber.tag("GeoFire").d("Fetching user locations for role: $rolesAsString $radius $location")
            if(rolesAsString.isNotEmpty()) {
                viewModel.fetchUserLocations(
                    role = rolesAsString,
                    radius = radius.toDouble(),
                    location = location,
                    minRating = minRating
                )
            }else{
                viewModel.fetchAllNearbyUsers(
                    radius = radius.toDouble(),
                    location = selectedLocation ?: currentLocation!!
                )
            }
        }
    }

    // Log.d("GeoFire", "MapScreen Composable, ${locationPermissionState.status}, ${currentLocation}, $selectedRole $radius $selectedLocation $currUserProfile $userLocations}")
    // Initial data loading
    /*
    LaunchedEffect(locationPermissionState.status, currentLocation, selectedRoles) {
        if (locationPermissionState.status.isGranted && currentLocation != null) {

            // Timber.tag("GeoFire").d("Fetching user locations for role: $selectedRole $radius $selectedLocation $currentLocation")
            /*
            viewModel.fetchUserLocations(
                location = selectedLocation ?: currentLocation!!,
                radius = radius.toDouble(),
                role = selectedRole
            )

             */
            viewModel.fetchAllNearbyUsers(
                radius = radius.toDouble(),
                location = selectedLocation ?: currentLocation!!
            )
        }
    }

     */

    LaunchedEffect(Unit){
        viewModel.fetchAllRoles()
    }

    val locationUpdateCheck by settingViewModel.privacyEnabled.collectAsState()
    // update
    // Location permission handling
    LaunchedEffect(locationPermissionState.status) {
        if (locationPermissionState.status.isGranted) {
            showPermissionDialog = false
            isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            showGpsDialog = !isGpsEnabled
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    location?.let {
                        // Timber.tag("GeoFire").d("Current Location: ${it.latitude}, ${it.longitude}")
                        currentLocation = LatLng(it.latitude, it.longitude)
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(currentLocation!!, 12f)
                    }
                }
                .addOnFailureListener { e ->
                    // Timber.tag("GeoFire").e(e, "Error getting current location")
                }

            // Start location updates for the authenticated user
            Timber.tag("GeoFire").d("Map Screen Location updates started: ${locationUpdateCheck}")
            if(locationUpdateCheck) {
                //viewModel.startLocationUpdates(userId, context)
                viewModel.startLocationUpdates(userId)
            }
            // Timber.tag("GeoFire").d("Location updates started: $userId")

        } else {
            showPermissionDialog = true
            locationPermissionState.launchPermissionRequest()
        }
    }
    LaunchedEffect(locationUpdateCheck) {
        if (locationUpdateCheck && locationPermissionState.status.isGranted) {
            viewModel.startLocationUpdates(userId)
        } else {
            viewModel.stopLocationUpdates()
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (locationPermissionState.status.isGranted) {
                        navController.navigate("searchScreen")
                    } else {
                        // Show permission rationale
                        locationPermissionState.launchPermissionRequest()
                    }
                },
                icon = { Icon(Icons.Default.Search, "Search") },
                text = { Text("Search", style = MaterialTheme.typography.titleMedium) }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Timber.tag("GeoFire").d("User Locations: $userLocations, $currUserProfile $currentLocation, $selectedLocation $radius $selectedRole")
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = locationPermissionState.status.isGranted) // Enable only if granted
            ) {
                if (userLocations.isEmpty()) {
                    //
                } else{
                    userLocations.forEach { user ->
                        if (user.uid != userId) {  // Skip the current user
                            CustomMapMarker(
                                //imageUrl = "https://i.pinimg.com/originals/b8/5e/9d/b85e9df9e9b75bcce3a767eb894ef153.jpg",
                                imageUrl = user.imageUrl, //.ifBlank { R.drawable.placeholder },
                                fullName = user.name,
                                location = LatLng(user.lat, user.lng),
                                onClick = {
                                    selectedUser = user
                                    true
                                })
                        }
                    }
                }

                (selectedLocation ?: currentLocation)?.let { location ->
                    Circle(
                        center = location,
                        radius = radius * 1000.0, // Convert km to meters
                        fillColor = Color.Blue.copy(alpha = 0.1f),
                        strokeColor = Color.Blue,
                        strokeWidth = 2f
                    )
                }
            }
            // Profile Bottom Sheet
            selectedUser?.let { user ->
                ProfileBottomSheet(user = user,
                    openProfile = { navController.navigate(ProfileRoute.UserProfile.createRoute(user.uid)) },
                    openChat = { navController.navigate(HomeRoute.ChatDetail.createRoute(user.uid)) },
                    onDismiss = { selectedUser = null })
            }

            // update
            if (showGpsDialog) {
                AlertDialog(
                    onDismissRequest = { showGpsDialog = false },
                    title = { Text("GPS Required") },
                    text = { Text("Please enable GPS for accurate location services") },
                    confirmButton = {
                        Button(onClick = {
                            // Open location settings
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            showGpsDialog = false
                        }) {
                            Text("Enable GPS")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showGpsDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            /*
            if (showBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = {
                        showBottomSheet = false
                    },
                    sheetState = sheetState
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Location Input
                        Text("Search Location", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = MaterialTheme.shapes.medium
                                )
                        ) {
                            SearchBar(
                                onPlaceSelected = { place ->
                                    viewModel.selectLocation(place, context)
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                    location?.let {
                                        viewModel.setSelectedLocation(LatLng(it.latitude, it.longitude))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.LocationOn, "Current Location")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Use Current Location")
                        }

                        // Radius Slider
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Search Radius: ${radius.toInt()} km")
                        Slider(
                            value = radius,
                            onValueChange = {
                                // radius = it
                            },
                            valueRange = 1f..50f,
                            steps = 9,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (selectedRoles.isNotEmpty()) {
                            Text(
                                text = "Selected Roles:",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                            ) {
                                items(selectedRoles.size) {
                                    val role = selectedRoles.elementAt(it)
                                    SuggestionChip(
                                        onClick = {
                                            //selectedRoles = selectedRoles - role
                                        },
                                        label = {
                                            Text(
                                                text = role,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        border = BorderStroke(  
                                            1.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        ),
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            labelColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Select Role", style = MaterialTheme.typography.titleMedium)

                        /*
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            roles.forEach { role ->
                                FilterChip(
                                    selected = role == selectedRole,
                                    onClick = { selectedRole = role },
                                    label = { Text(role) }
                                )
                            }
                        }

                         */
                        OutlinedTextField(
                            value = roleSearchQuery,
                            onValueChange = { roleSearchQuery = it },
                            placeholder = { Text("Search roles...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 128.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.height(100.dp)
                        ) {
                            items(filteredRoles.size) {
                                val role = filteredRoles[it]
                                FilterChip(
                                    selected = selectedRoles.contains(role),
                                    onClick = {
                                        /*
                                        selectedRoles = if (selectedRoles.contains(role)) {
                                            selectedRoles - role
                                        } else {
                                            selectedRoles + role
                                        }

                                         */
                                    },
                                    label = {
                                        Text(
                                            text = role,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Apply Button
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        sheetState.hide()

                                        showBottomSheet = false

                                        // Get the target location for camera movement
                                        val targetLocation = selectedLocation ?: currentLocation

                                        targetLocation?.let { location ->
                                            // Move camera to selected location
                                            cameraPositionState.animate(
                                                update = CameraUpdateFactory.newLatLngZoom(
                                                    location,
                                                    12f
                                                ),
                                                durationMs = 500
                                            )

                                            val rolesAsString = selectedRoles.joinToString(",")
                                            // Fetch new data with current filters
                                            viewModel.fetchUserLocations(
                                                location = location,
                                                radius = radius.toDouble(),
                                                role = rolesAsString
                                            )
                                        }
                                    }catch (e: Exception) {
                                        // Timber.e(e, "Error applying filters")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Apply Filters")
                        }
                    }
                }
            }

             */
        }
    }

    if (showPermissionDialog) {
        val permissionStatus = locationPermissionState.status
        val isPermanentlyDenied = !permissionStatus.shouldShowRationale && !permissionStatus.isGranted

        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Permission Required") },
            text = {
                if (isPermanentlyDenied) {
                    Text("Location permission was permanently denied. Please enable it in app settings.")
                } else {
                    Text("Location permission is needed to show nearby users.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        if (isPermanentlyDenied) {
                            // Open app settings
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } else {
                            locationPermissionState.launchPermissionRequest()
                        }
                    }
                ) {
                    Text(if (isPermanentlyDenied) "Open Settings" else "Grant Permission")
                }
            },
            dismissButton = {
                if (!isPermanentlyDenied) {
                    TextButton(onClick = { showPermissionDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

}

private fun getZoomLevel(radiusInKm: Float): Float {
    return when {
        radiusInKm <= 1 -> 15f
        radiusInKm <= 5 -> 13f
        radiusInKm <= 10 -> 12.5f
        radiusInKm <= 15 -> 12f
        radiusInKm <= 20 -> 11.5f
        radiusInKm <= 30 -> 11f
        radiusInKm <= 40 -> 10.5f
        radiusInKm <= 50 -> 10f
        else -> 10f
    }
}

@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    onPlaceSelected: (String) -> Unit
) {
    // Determine the appropriate text color based on the current theme
    val textColor = if (isSystemInDarkTheme()) Color.White else Color.Black

    // Use AndroidView to integrate an Android View (AutoCompleteTextView) into Jetpack Compose
    AndroidView(
        factory = { context ->
            AutoCompleteTextView(context).apply {
                hint = "Search for a place"

                setTextColor(textColor.toArgb())
                setHintTextColor(textColor.copy(alpha = 0.6f).toArgb())

                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                if (!Places.isInitialized()) {
                    Places.initialize(context, context.getString(R.string.PLACE_API_KEY))
                }

                val autocompleteAdapter = ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line)
                val placesClient = Places.createClient(context)
                val autocompleteSessionToken = AutocompleteSessionToken.newInstance()

                // Use addTextChangedListener with doAfterTextChanged to avoid TextWatcher boilerplate
                addTextChangedListener { editable ->
                    val query = editable?.toString() ?: ""
                    if (query.isNotEmpty()) {
                        val request = FindAutocompletePredictionsRequest.builder()
                            .setSessionToken(autocompleteSessionToken)
                            .setQuery(query)
                            .build()

                        placesClient.findAutocompletePredictions(request).addOnSuccessListener { response ->
                            autocompleteAdapter.clear()
                            response.autocompletePredictions.forEach { prediction ->
                                autocompleteAdapter.add(prediction.getFullText(null).toString())
                            }
                            autocompleteAdapter.notifyDataSetChanged()
                        }
                    }
                }

                setAdapter(autocompleteAdapter)

                setOnItemClickListener { _, _, position, _ ->
                    val selectedPlace = autocompleteAdapter.getItem(position) ?: return@setOnItemClickListener
                    onPlaceSelected(selectedPlace)
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(6.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileBottomSheet(
    user: profileUser, openProfile: () -> Unit, openChat: () -> Unit, onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.secondary,
        tonalElevation = 16.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Profile Header
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { openProfile() }) {
                // Profile Image or Initials
                if (user.imageUrl.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            )
                            .padding(12.dp), contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                } else {
                    ImageUsingCoil(
                        context,
                        user.imageUrl,
                        R.drawable.placeholder,
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))

                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = user.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = user.role,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Contact Information
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoRow(
                    icon = Icons.Default.Email, text = user.email, Modifier.clickable {
                        if (user.email.isNotEmpty())
                        openUrl(context, "mailto:${user.email}") }
                )

                InfoRow(
                    icon = Icons.Default.Phone, text = "Not available"
                )

                InfoRow(
                    icon = Icons.Default.LocationOn,
                    text = user.collegeName ?: user.companyName.ifBlank { "Not Available" }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { openChat() }, modifier = Modifier.weight(1f)
                ) {
                    Text("Send Message")
                }

                OutlinedButton(
                    onClick = onDismiss, modifier = Modifier.weight(1f)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun InfoRow(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun CustomMapMarker(
    imageUrl: String?, fullName: String, location: LatLng, onClick: () -> Unit
) {
    val markerState = remember { MarkerState(position = location) }
    val context = LocalContext.current

    val painter = rememberAsyncImagePainter(
        ImageRequest.Builder(LocalContext.current).data(imageUrl).crossfade(true)
            .placeholder(R.drawable.placeholder).error(R.drawable.placeholder).allowHardware(false)
            .build()
    )

    MarkerComposable(
        keys = arrayOf(fullName, painter.state),
        state = markerState,
        title = fullName,
        anchor = Offset(0.5f, 1f),
        onClick = {
            onClick()
            true
        }
    ) {
        Box(
            modifier = Modifier
                .wrapContentSize()
                .padding(bottom = 4.dp) // Adjusts position relative to marker
        ) {
            // Marker Base Icon
            Image(
                painter = painterResource(id = R.drawable.img), // Your marker base icon
                contentDescription = "Map Marker",
                modifier = Modifier.size(60.dp)
            )

            // Profile Image Positioned on Top
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = 2.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White, CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                if (!imageUrl.isNullOrEmpty()) {
                    Image(
                        painter = painter,
                        contentDescription = "Profile Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = fullName.split(" ").let { parts ->
                            when {
                                parts.size >= 2 -> "${parts.first().take(1)}${parts.last().take(1)}"
                                parts.isNotEmpty() -> parts.first().take(1)
                                else -> ""
                            }.uppercase()
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}