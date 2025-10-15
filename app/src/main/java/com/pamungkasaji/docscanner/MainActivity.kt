package com.pamungkasaji.docscanner

import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.pamungkasaji.docscanner.ui.theme.DocScannerTheme
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    companion object {
        // Replace with your deployed Apps Script Web App /exec URL
        private const val WEB_APP_URL = "https://script.google.com/macros/s/AKfycbwEgSebaZkgF_NCzX0STe1zdagA5vBcpjjvqjBTZsG-4kMybTt4o4PshkkQjEszgmn87w/exec"
    }

    private val http by lazy { OkHttpClient() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(SCANNER_MODE_FULL)
            .setGalleryImportAllowed(true)
            .setPageLimit(100)
            .setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
            .build()
        val scanner = GmsDocumentScanning.getClient(options)

        setContent {
            DocScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
                    var studentName by remember { mutableStateOf("") }
                    var dob by remember { mutableStateOf("") } // ISO yyyy-MM-dd is fine
                    var sending by remember { mutableStateOf(false) }

                    val scannerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartIntentSenderForResult(),
                        onResult = {
                            if (it.resultCode == RESULT_OK) {
                                val result = GmsDocumentScanningResult.fromActivityResultIntent(it.data)
                                imageUris = result?.pages?.map { p -> p.imageUri } ?: emptyList()

                                result?.pdf?.let { pdf ->
                                    // Save the scanned PDF to internal storage
                                    val safeName = if (studentName.isBlank()) "scan" else studentName.trim()
                                        .replace(Regex("""[\\/:*?"<>|#]"""), "-")
                                    val outFile = File(filesDir, "${safeName}_scan.pdf")
                                    contentResolver.openInputStream(pdf.uri)?.use { input ->
                                        FileOutputStream(outFile).use { fos ->
                                            input.copyTo(fos)
                                        }
                                    }
                                    // Encode and send
                                    sendToWebhook(
                                        file = outFile,
                                        studentName = studentName,
                                        dob = dob,
                                        onState = { sending = it }
                                    )
                                }
                            }
                        }
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        OutlinedTextField(
                            value = studentName,
                            onValueChange = { studentName = it },
                            label = { Text("Student name") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = dob,
                            onValueChange = { dob = it },
                            label = { Text("Date of birth (yyyy-MM-dd)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            modifier = Modifier.fillMaxWidth()
                        )

                        imageUris.forEach { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Button(
                            onClick = {
                                scanner.getStartScanIntent(this@MainActivity)
                                    .addOnSuccessListener {
                                        val req = IntentSenderRequest.Builder(it).build()
                                        scannerLauncher.launch(req)
                                    }
                                    .addOnFailureListener { err ->
                                        Toast.makeText(applicationContext, err.message, Toast.LENGTH_LONG).show()
                                    }
                            },
                            enabled = !sending
                        ) {
                            Text(if (sending) "Uploading..." else "Scan PDF")
                        }
                    }
                }
            }
        }
    }

    private fun sendToWebhook(
        file: File,
        studentName: String,
        dob: String,
        onState: (Boolean) -> Unit
    ) {
        onState(true)

        Thread {
            try {
                val bytes = file.readBytes()
                // Base64 without line breaks
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

                val payload = JSONObject().apply {
                    put("student_name", studentName)
                    put("dob", dob)
                    put("pdf_filename", file.name)
                    put("pdf_base64", b64)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = payload.toString().toRequestBody(mediaType)

                val req = Request.Builder()
                    .url(WEB_APP_URL)
                    .post(body)
                    .build()

                http.newCall(req).execute().use { resp ->
                    val msg = if (resp.isSuccessful) {
                        "Uploaded: ${file.name}"
                    } else {
                        "Upload failed ${resp.code}: ${resp.body?.string() ?: ""}"
                    }
                    runOnUiThread {
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                runOnUiThread { onState(false) }
            }
        }.start()
    }
}
