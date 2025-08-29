package ru.iplc.eartem

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleanHands
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.util.Log
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import ru.iplc.eartem.ui.theme.EARTEMTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            EARTEMTheme {
                val navController = rememberNavController()
                NavHost(navController, startDestination = "welcome") {
                    composable("welcome") {
                        WelcomeScreen(
                            onSpotterClick = { navController.navigate("map") }
                        )
                    }
                    composable("map") {
                        MapScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(
    onSpotterClick: () -> Unit
) {
    var showVolunteerDialog by remember { mutableStateOf(false) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            StylizedBackground()

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Welcome to Litter Map",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 48.dp)
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        RoleButton(
                            title = "I'm Spotter",
                            subtitle = "No registration required.\nI just want to mark polluted areas on the map.",
                            icon = Icons.Default.LocationOn,
                            onClick = onSpotterClick,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        RoleButton(
                            title = "I'm a Volunteer",
                            subtitle = "Ready to register.\nI want to mark polluted areas and help clean them up.",
                            icon = Icons.Default.CleanHands,
                            onClick = { showVolunteerDialog = true },
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                }
            }
        }
    }

    if (showVolunteerDialog) {
        VolunteerRegistrationDialog(
            onDismiss = { showVolunteerDialog = false },
            onSave = { name, contact, photoUri ->
                // TODO: отправить на сервер
                showVolunteerDialog = false
            }
        )
    }
}


@Composable
fun StylizedBackground() {
    Image(
        painter = painterResource(id = R.mipmap.ic_logo_foreground),
        contentDescription = "background logo",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
}

@Composable
fun RoleButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 8.dp,
            pressedElevation = 4.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .padding(end = 16.dp)
            )

            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val mapView = rememberMapViewWithLifecycle()
    var selectedMarker by remember { mutableStateOf<LatLng?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        AndroidView({ mapView }) { mapView ->
            mapView.getMapAsync { map ->
                val fused = LocationServices.getFusedLocationProviderClient(context)
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    fused.lastLocation.addOnSuccessListener { location ->
                        location?.let {
                            val myPos = LatLng(it.latitude, it.longitude)
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(myPos, 16f))
                        }
                    }
                }

                // 🔽 вот сюда вставляем
                map.setOnMapClickListener { latLng ->
                    map.clear()
                    map.addMarker(com.google.android.gms.maps.model.MarkerOptions().position(latLng))
                    selectedMarker = latLng
                    showDialog = true
                }
            }
        }


        FloatingActionButton(
            onClick = {
                mapView.getMapAsync { map ->
                    val fused = LocationServices.getFusedLocationProviderClient(context)
                    fused.lastLocation.addOnSuccessListener { location ->
                        location?.let {
                            val myPos = LatLng(it.latitude, it.longitude)
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(myPos, 16f))
                        }
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = "My location")
        }
    }

    if (showDialog && selectedMarker != null) {
        MarkDialog(
            latLng = selectedMarker!!,
            onDismiss = { showDialog = false },
            onSave = { description, shortText, photoUri ->
                // TODO: отправка на сервер
                Log.d("MapScreen", "Saved: $description, $shortText, $photoUri")
                showDialog = false
            }
        )
    }

}

@Composable
fun MarkDialog(
    latLng: LatLng,
    onDismiss: () -> Unit,
    onSave: (description: String, shortText: String, photoUri: Uri) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var shortText by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        photoUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add pollution point") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text("Location: ${latLng.latitude}, ${latLng.longitude}")

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = shortText,
                    onValueChange = { shortText = it },
                    label = { Text("Short description of the pollution point (e.g. “Near café”)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Spacer(Modifier.height(12.dp))
                Button(onClick = { launcher.launch("image/*") }) {
                    Text("Прикрепить фото")
                }
                photoUri?.let {
                    Text("Выбрано фото: $it", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (photoUri != null && description.isNotBlank()) {
                        onSave(description, shortText, photoUri!!)
                    }
                },
                enabled = photoUri != null && description.isNotBlank()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}


@Composable
fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // MapView требует onCreate
    DisposableEffect(lifecycle, mapView) {
        mapView.onCreate(null) // без сохранённого состояния

        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = mapView.onStart()
            override fun onResume(owner: LifecycleOwner) = mapView.onResume()
            override fun onPause(owner: LifecycleOwner) = mapView.onPause()
            override fun onStop(owner: LifecycleOwner) = mapView.onStop()
            override fun onDestroy(owner: LifecycleOwner) = mapView.onDestroy()
        }

        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    return mapView
}


@Composable
fun VolunteerRegistrationDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, contact: String, photoUri: Uri?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        photoUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Volunteer Registration") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = contact,
                    onValueChange = { contact = it },
                    label = { Text("Contact (email or phone)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                Button(onClick = { launcher.launch("image/*") }) {
                    Text("Attach profile photo")
                }

                photoUri?.let {
                    Text("Selected photo: $it", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, contact, photoUri) },
                enabled = name.isNotBlank() && contact.isNotBlank()
            ) {
                Text("Register")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}





@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun WelcomeScreenPreview() {
    EARTEMTheme {
        WelcomeScreen(onSpotterClick = {})
    }
}
