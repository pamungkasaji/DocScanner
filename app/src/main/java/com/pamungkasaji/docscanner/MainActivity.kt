package com.pamungkasaji.docscanner

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    companion object {
        private const val WEB_APP_URL = "https://script.google.com/macros/s/AKfycbwEgSebaZkgF_NCzX0STe1zdagA5vBcpjjvqjBTZsG-4kMybTt4o4PshkkQjEszgmn87w/exec"
    }

//    private val http by lazy { OkHttpClient() }

    // replace your http client definition with this (or add if you don't have it here)
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .callTimeout(6, TimeUnit.MINUTES)   // total budget per call
            .retryOnConnectionFailure(true)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(SCANNER_MODE_FULL)
            .setGalleryImportAllowed(true)
            .setPageLimit(100)
            .setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
            .build()

        setContent {
            DocScannerTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

                    var nomorInduk by remember { mutableStateOf("") }
                    var nisn by remember { mutableStateOf("") }
                    var studentName by remember { mutableStateOf("") }
                    var gender by remember { mutableStateOf("laki-laki") } // radio default
                    var dob by remember { mutableStateOf("") } // yyyy-MM-dd

                    var sending by remember { mutableStateOf(false) }

                    val scannerLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartIntentSenderForResult()
                    ) { res ->
                        if (res.resultCode == RESULT_OK) {
                            val result = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
                            imageUris = result?.pages?.map { p -> p.imageUri } ?: emptyList()

                            result?.pdf?.let { pdf ->
                                val safeName = (studentName.ifBlank { "scan" }).replace(Regex("""[\\/:*?"<>|#]"""), "-")
                                val outFile = File(filesDir, "${safeName}_scan.pdf")
                                contentResolver.openInputStream(pdf.uri)?.use { input ->
                                    FileOutputStream(outFile).use { fos -> input.copyTo(fos) }
                                }
                                sendToWebhook(
                                    file = outFile,
                                    nomorInduk = nomorInduk,
                                    nisn = nisn,
                                    studentName = studentName,
                                    gender = gender,
                                    dob = dob,
                                    onState = { sending = it },
                                    onReset = {
                                        // reset to initial
                                        imageUris = emptyList()
                                        nomorInduk = ""
                                        nisn = ""
                                        studentName = ""
                                        gender = "laki-laki"
                                        dob = ""
                                    }
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            // .padding(16.dp)
                            .padding(start = 16.dp, end = 16.dp, top = 80.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        OutlinedTextField(
                            value = nomorInduk,
                            onValueChange = { if (it.matches(Regex("[A-Za-z0-9]*"))) nomorInduk = it },
                            label = { Text("Nomor Induk") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = nisn,
                            onValueChange = { if (it.matches(Regex("[A-Za-z0-9]*"))) nisn = it },
                            label = { Text("NISN") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = studentName,
                            onValueChange = { studentName = it },
                            label = { Text("Nama Siswa") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Gender radio
//                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Jenis Kelamin:")
                            GenderRadio("laki-laki", gender) { gender = it }
                            GenderRadio("perempuan", gender) { gender = it }
                        }

                        // DOB picker
                        OutlinedButton(onClick = {
                            val cal = Calendar.getInstance()
                            DatePickerDialog(
                                this@MainActivity,
                                { _, y, m, d ->
                                    val mm = (m + 1).toString().padStart(2, '0')
                                    val dd = d.toString().padStart(2, '0')
                                    dob = "$y-$mm-$dd"
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }) { Text(if (dob.isBlank()) "Tanggal Lahir" else dob) }

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
                                if (nomorInduk.isBlank() || studentName.isBlank() || dob.isBlank()) {
                                    Toast.makeText(applicationContext, "Lengkapi data wajib", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                GmsDocumentScanning.getClient(options).getStartScanIntent(this@MainActivity)
                                    .addOnSuccessListener { intent ->
                                        scannerLauncher.launch(IntentSenderRequest.Builder(intent).build())
                                    }
                                    .addOnFailureListener { err ->
                                        Toast.makeText(applicationContext, err.message, Toast.LENGTH_LONG).show()
                                    }
                            },
                            enabled = !sending
                        ) { Text(if (sending) "Mengunggah..." else "Scan & Upload") }

                        if (sending) {
                            Dialog(onDismissRequest = { /* no-op while uploading */ }) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    tonalElevation = 8.dp
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(72.dp),
                                            strokeWidth = 6.dp
                                        )
                                        Text("Mengunggah...", style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun GenderRadio(option: String, selected: String, onSelect: (String) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected == option, onClick = { onSelect(option) })
            Text(option)
        }
    }

    private fun sendToWebhook(
        file: File,
        nomorInduk: String,
        nisn: String,
        studentName: String,
        gender: String,
        dob: String,
        onState: (Boolean) -> Unit,
        onReset: () -> Unit
    ) {
        onState(true)
        Thread {
            try {
                val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                val payload = JSONObject().apply {
                    put("nomor_induk", nomorInduk)
                    put("nisn", nisn)
                    put("student_name", studentName)
                    put("gender", gender) // "laki-laki" or "perempuan"
                    put("dob", dob)
                    put("pdf_filename", file.name)
                    put("pdf_base64", b64)
                }
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder().url(WEB_APP_URL).post(body).build()
//                OkHttpClient().newCall(req).execute().use { resp ->
                http.newCall(req).execute().use { resp ->
                    val ok = resp.isSuccessful
                    val msg = if (ok) "Terkirim: ${file.name}" else "Gagal ${resp.code}: ${resp.body?.string() ?: ""}"
                    runOnUiThread {
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                        Log.e("MainActivity", msg)
                        if (ok) onReset()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
            } finally {
                runOnUiThread { onState(false) }
            }
        }.start()
    }
}