package com.upupprogress.app.ui.screens.nutrition

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.upupprogress.app.data.model.validFoodBarcode
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun BarcodeScannerDialog(onBarcode: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val activity = context.findActivity()
    var checks by remember { mutableIntStateOf(0) }
    var requested by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requested = true
        checks++
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) checks++ }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    // El contador solo dispara la revisión; el permiso se consulta al sistema en cada composición.
    @Suppress("UNUSED_VARIABLE") val checkTrigger = checks
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val rationale = activity?.let {
        ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
    } == true
    val needsSettings = !granted && requested && !rationale

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Escanear alimento", style = MaterialTheme.typography.headlineSmall)
                Text("Apunta la cámara trasera al código de barras del producto.")
                if (granted) {
                    RearBarcodePreview(onBarcode, Modifier.weight(1f).fillMaxWidth())
                } else {
                    Column(
                        Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(if (needsSettings) "El permiso de cámara está desactivado. Puedes habilitarlo en Ajustes."
                            else "Necesitamos la cámara para leer el código del producto.")
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                checks++
                            } else if (needsSettings) {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)))
                            } else {
                                // La explicación anterior también sirve de justificación si rationale es true.
                                launcher.launch(Manifest.permission.CAMERA)
                            }
                        }) { Text(if (needsSettings) "Abrir ajustes" else "Permitir cámara") }
                    }
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
            }
        }
    }
}

@Composable
private fun RearBarcodePreview(onBarcode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val callback by rememberUpdatedState(onBarcode)
    var cameraError by remember { mutableStateOf<String?>(null) }
    val view = remember(context) { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(view, owner) {
        val active = AtomicBoolean(true)
        val delivered = AtomicBoolean(false)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(
            Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E, Barcode.FORMAT_ITF, Barcode.FORMAT_CODE_128,
        ).build())
        val controller = LifecycleCameraController(context)
        controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        controller.setImageAnalysisAnalyzer(mainExecutor, MlKitAnalyzer(
            listOf(scanner), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, mainExecutor,
        ) { result ->
            if (active.get()) {
                val code = result.getValue(scanner)?.firstNotNullOfOrNull { it.rawValue?.takeIf(::validFoodBarcode) }
                if (code != null && delivered.compareAndSet(false, true)) callback(code)
            }
        })
        controller.initializationFuture.addListener({
            if (active.get()) {
                try {
                    controller.initializationFuture.get()
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        cameraError = "El permiso de cámara ya no está disponible."
                    } else if (!controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                        cameraError = "Este dispositivo no tiene una cámara trasera disponible."
                    } else {
                        controller.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        view.controller = controller
                        controller.bindToLifecycle(owner)
                    }
                } catch (_: SecurityException) {
                    cameraError = "El permiso de cámara ya no está disponible."
                } catch (_: Exception) {
                    cameraError = "No se pudo abrir la cámara trasera. Cierra el escáner y vuelve a intentarlo."
                }
            }
        }, mainExecutor)
        onDispose {
            active.set(false)
            controller.clearImageAnalysisAnalyzer()
            view.controller = null
            controller.unbind()
            scanner.close()
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        cameraError?.let { message ->
            Surface(Modifier.padding(16.dp)) { Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
