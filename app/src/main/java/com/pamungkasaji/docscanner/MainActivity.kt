package com.pamungkasaji.docscanner

import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.pamungkasaji.docscanner.ui.theme.DocScannerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .callTimeout(6, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val ioScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DocScannerTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen()
                }
            }
        }
    }

    @Composable
    fun MainScreen() {
        val context = LocalContext.current

        var nomorInduk by remember { mutableStateOf("") }
        var nisn by remember { mutableStateOf("") }
        var studentName by remember { mutableStateOf("") }
        var gender by remember { mutableStateOf("laki-laki") }
        var dob by remember { mutableStateOf("") }

        var isSearching by remember { mutableStateOf(false) }
        var isSaving by remember { mutableStateOf(false) }
        var studentFound by remember { mutableStateOf(false) }
        var searchPerformed by remember { mutableStateOf(false) }

        var bukuIndukFile by remember { mutableStateOf<File?>(null) }
        var ijazahFile by remember { mutableStateOf<File?>(null) }
        var showBukuIndukPreview by remember { mutableStateOf(false) }
        var showIjazahPreview by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Search Section Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Pencarian Data Siswa",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = nomorInduk,
                            onValueChange = {
                                if (it.matches(Regex("[A-Za-z0-9]*"))) {
                                    nomorInduk = it
                                    // Reset form when nomor induk changes
                                    if (searchPerformed) {
                                        searchPerformed = false
                                        studentFound = false
                                        nisn = ""
                                        studentName = ""
                                        gender = "laki-laki"
                                        dob = ""
                                        bukuIndukFile = null
                                        ijazahFile = null
                                    }
                                }
                            },
                            label = { Text("Nomor Induk") },
                            modifier = Modifier.weight(1f),
                            enabled = !isSearching,
                            placeholder = { Text("Masukkan nomor induk siswa") }
                        )
                        Button(
                            onClick = {
                                if (nomorInduk.isBlank()) {
                                    Toast.makeText(context, "Masukkan nomor induk", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                ioScope.launch {
                                    searchStudent(
                                        nomorInduk = nomorInduk,
                                        onSearching = { isSearching = true },
                                        onFound = { student ->
                                            launch(Dispatchers.Main) {
                                                studentFound = true
                                                searchPerformed = true
                                                nisn = student.optString("nisn", "")
                                                studentName = student.optString("student_name", "")
                                                gender = student.optString("gender", "laki-laki")
                                                dob = student.optString("dob", "")
                                                Toast.makeText(context, "Data siswa ditemukan", Toast.LENGTH_LONG).show()
                                                isSearching = false
                                            }
                                        },
                                        onNotFound = {
                                            launch(Dispatchers.Main) {
                                                studentFound = false
                                                searchPerformed = true
                                                // Changed from dialog to toast
                                                Toast.makeText(context, "Data tidak ditemukan, silakan isi manual", Toast.LENGTH_LONG).show()
                                                isSearching = false
                                            }
                                        },
                                        onError = { error ->
                                            launch(Dispatchers.Main) {
                                                Toast.makeText(context, "Error: $error", Toast.LENGTH_LONG).show()
                                                isSearching = false
                                            }
                                        }
                                    )
                                }
                            },
                            enabled = !isSearching && nomorInduk.isNotBlank()
                        ) {
                            Text(if (isSearching) "..." else "Cari")
                        }
                    }
                }
            }

            // Student Data Section Card
            if (searchPerformed) {
                StudentDataSection(
                    studentFound = studentFound,
                    nisn = nisn,
                    onNisnChange = { if (it.matches(Regex("[A-Za-z0-9]*"))) nisn = it },
                    studentName = studentName,
                    onStudentNameChange = { studentName = it },
                    gender = gender,
                    onGenderChange = { gender = it },
                    dob = dob,
                    onDobChange = { dob = it },
                    bukuIndukFile = bukuIndukFile,
                    onBukuIndukFileChange = { bukuIndukFile = it },
                    ijazahFile = ijazahFile,
                    onIjazahFileChange = { ijazahFile = it },
                    showBukuIndukPreview = showBukuIndukPreview,
                    onBukuIndukPreviewClick = { showBukuIndukPreview = true },
                    onBukuIndukPreviewDismiss = { showBukuIndukPreview = false },
                    showIjazahPreview = showIjazahPreview,
                    onIjazahPreviewClick = { showIjazahPreview = true },
                    onIjazahPreviewDismiss = { showIjazahPreview = false },
                    onSave = {
                        if (nomorInduk.isBlank()) {
                            Toast.makeText(context, "Nomor induk wajib diisi", Toast.LENGTH_LONG).show()
                            return@StudentDataSection
                        }
                        if (!studentFound && (studentName.isBlank() || dob.isBlank())) {
                            Toast.makeText(context, "Lengkapi data wajib", Toast.LENGTH_LONG).show()
                            return@StudentDataSection
                        }

                        ioScope.launch {
                            saveStudentData(
                                nomorInduk = nomorInduk,
                                nisn = nisn,
                                studentName = studentName,
                                gender = gender,
                                dob = dob,
                                bukuIndukFile = bukuIndukFile,
                                ijazahFile = ijazahFile,
                                studentFound = studentFound,
                                onSaving = { isSaving = true },
                                onSuccess = {
                                    launch(Dispatchers.Main) {
                                        Toast.makeText(context, "Data berhasil disimpan", Toast.LENGTH_LONG).show()
                                        // Reset form
                                        if (!studentFound) {
                                            nomorInduk = ""
                                            nisn = ""
                                            studentName = ""
                                            gender = "laki-laki"
                                            dob = ""
                                            searchPerformed = false
                                        }
                                        bukuIndukFile = null
                                        ijazahFile = null
                                        studentFound = false
                                        isSaving = false
                                    }
                                },
                                onError = { error ->
                                    launch(Dispatchers.Main) {
                                        Toast.makeText(context, "Error: $error", Toast.LENGTH_LONG).show()
                                        isSaving = false
                                    }
                                }
                            )
                        }
                    },
                    isSaving = isSaving
                )
            } else {
                // Placeholder card before search
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            "Silakan masukkan Nomor Induk",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "Data siswa akan muncul setelah pencarian",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Loading Dialog
            if (isSaving || isSearching) {
                Dialog(onDismissRequest = { }) {
                    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 8.dp) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(72.dp), strokeWidth = 6.dp)
                            Text(
                                if (isSearching) "Mencari..." else "Menyimpan...",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun StudentDataSection(
        studentFound: Boolean,
        nisn: String,
        onNisnChange: (String) -> Unit,
        studentName: String,
        onStudentNameChange: (String) -> Unit,
        gender: String,
        onGenderChange: (String) -> Unit,
        dob: String,
        onDobChange: (String) -> Unit,
        bukuIndukFile: File?,
        onBukuIndukFileChange: (File?) -> Unit,
        ijazahFile: File?,
        onIjazahFileChange: (File?) -> Unit,
        showBukuIndukPreview: Boolean,
        onBukuIndukPreviewClick: () -> Unit,
        onBukuIndukPreviewDismiss: () -> Unit,
        showIjazahPreview: Boolean,
        onIjazahPreviewClick: () -> Unit,
        onIjazahPreviewDismiss: () -> Unit,
        onSave: () -> Unit,
        isSaving: Boolean
    ) {
        val context = LocalContext.current
        val fieldsEnabled = !studentFound

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (studentFound) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                        contentDescription = null,
                        tint = if (studentFound) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (studentFound) "Data siswa ditemukan" else "Data tidak ditemukan, silakan isi manual",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (studentFound) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                // Student Info Fields
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nisn,
                        onValueChange = onNisnChange,
                        label = { Text("NISN") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = fieldsEnabled,
                        placeholder = { Text("Nomor Induk Siswa Nasional") }
                    )
                    OutlinedTextField(
                        value = studentName,
                        onValueChange = onStudentNameChange,
                        label = { Text("Nama Siswa") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = fieldsEnabled,
                        placeholder = { Text("Nama lengkap siswa") }
                    )

                    // Gender radio
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Jenis Kelamin:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (fieldsEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            GenderRadio("laki-laki", gender, fieldsEnabled, onGenderChange)
                            GenderRadio("perempuan", gender, fieldsEnabled, onGenderChange)
                        }
                    }

                    // DOB picker - Display only date, not datetime
                    OutlinedButton(
                        onClick = {
                            if (fieldsEnabled) {
                                val cal = Calendar.getInstance()
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        val mm = (m + 1).toString().padStart(2, '0')
                                        val dd = d.toString().padStart(2, '0')
                                        onDobChange("$y-$mm-$dd")
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                        },
                        enabled = fieldsEnabled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (dob.isBlank()) "Pilih Tanggal Lahir" else "Lahir: $dob")
                    }
                }

                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                // Scan Sections
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ScanSection(
                        title = "Scan Buku Induk",
                        file = bukuIndukFile,
                        onFileScanned = onBukuIndukFileChange,
                        showPreview = showBukuIndukPreview,
                        onPreviewClick = onBukuIndukPreviewClick,
                        onPreviewDismiss = onBukuIndukPreviewDismiss,
                        onScanAgain = { onBukuIndukFileChange(null) },
                        context = context
                    )

                    ScanSection(
                        title = "Scan Ijazah",
                        file = ijazahFile,
                        onFileScanned = onIjazahFileChange,
                        showPreview = showIjazahPreview,
                        onPreviewClick = onIjazahPreviewClick,
                        onPreviewDismiss = onIjazahPreviewDismiss,
                        onScanAgain = { onIjazahFileChange(null) },
                        context = context
                    )
                }

                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                // Save Button - Made different with gap below
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSave,
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 8.dp
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Menyimpan...")
                        } else {
                            Text(
                                "Simpan Data Siswa",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    // Added gap below Simpan button
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    @Composable
    fun GenderRadio(option: String, selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selected == option,
                onClick = { if (enabled) onSelect(option) },
                enabled = enabled
            )
            Text(
                text = option.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    @Composable
    fun ScanSection(
        title: String,
        file: File?,
        onFileScanned: (File?) -> Unit,
        showPreview: Boolean,
        onPreviewClick: () -> Unit,
        onPreviewDismiss: () -> Unit,
        onScanAgain: () -> Unit,
        context: android.content.Context
    ) {
        val activity = context as ComponentActivity
        val options = remember {
            GmsDocumentScannerOptions.Builder()
                .setScannerMode(SCANNER_MODE_FULL)
                .setGalleryImportAllowed(true)
                .setPageLimit(100)
                .setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
                .build()
        }

        val scannerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { res ->
            if (res.resultCode == RESULT_OK) {
                val result = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
                result?.pdf?.let { pdf ->
                    val safeName = title.lowercase().replace(" ", "_")
                    val outFile = File(context.filesDir, "${safeName}_${System.currentTimeMillis()}.pdf")
                    context.contentResolver.openInputStream(pdf.uri)?.use { input ->
                        FileOutputStream(outFile).use { fos -> input.copyTo(fos) }
                    }
                    onFileScanned(outFile)
                }
            }
        }

        // PDF View Intent launcher
        val pdfViewLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { /* We don't need to handle the result */ }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (file != null) {
                    Button(
                        onClick = {
                            // Open PDF with built-in viewer using FileProvider for security
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                file
                            )
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/pdf")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                            }

                            // Try to open with built-in viewer, fallback to any PDF viewer
                            try {
                                pdfViewLauncher.launch(intent)
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(context, "Tidak ada aplikasi untuk membuka PDF", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text("Lihat PDF")
                    }
                    Button(
                        onClick = {
                            onScanAgain()
                            GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                                .addOnSuccessListener { intent ->
                                    scannerLauncher.launch(IntentSenderRequest.Builder(intent).build())
                                }
                                .addOnFailureListener { err ->
                                    Toast.makeText(context, err.message, Toast.LENGTH_LONG).show()
                                }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Scan Ulang")
                    }
                } else {
                    Button(
                        onClick = {
                            GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                                .addOnSuccessListener { intent ->
                                    scannerLauncher.launch(IntentSenderRequest.Builder(intent).build())
                                }
                                .addOnFailureListener { err ->
                                    Toast.makeText(context, err.message, Toast.LENGTH_LONG).show()
                                }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Scan $title")
                    }
                }
            }

            if (file != null) {
                Text(
                    "File: ${file.name} (${file.length() / 1024} KB)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    private suspend fun searchStudent(
        nomorInduk: String,
        onSearching: () -> Unit,
        onFound: (JSONObject) -> Unit,
        onNotFound: () -> Unit,
        onError: (String) -> Unit
    ) {
        onSearching()
        try {
            val payload = JSONObject().apply {
                put("action", "search")
                put("nomor_induk", nomorInduk)
            }
            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder().url(WEB_APP_URL).post(body).build()

            http.newCall(req).execute().use { resp ->
                val responseBody = resp.body?.string() ?: "{}"
                Log.d("MainActivity", "Search response: $responseBody")
                val response = JSONObject(responseBody)
                if (response.optBoolean("found")) {
                    onFound(response.getJSONObject("student"))
                } else {
                    onNotFound()
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error searching student", e)
            onError(e.message ?: "Unknown error")
        }
    }

    private suspend fun saveStudentData(
        nomorInduk: String,
        nisn: String,
        studentName: String,
        gender: String,
        dob: String,
        bukuIndukFile: File?,
        ijazahFile: File?,
        studentFound: Boolean,
        onSaving: () -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        onSaving()
        try {
            val payload = JSONObject().apply {
                put("action", "save")
                put("nomor_induk", nomorInduk)
                put("nisn", nisn)
                put("student_name", studentName)
                put("gender", gender)
                put("dob", dob) // This is already in yyyy-MM-dd format

                if (bukuIndukFile != null) {
                    val b64 = Base64.encodeToString(bukuIndukFile.readBytes(), Base64.NO_WRAP)
                    put("buku_induk_base64", b64)
                    put("buku_induk_filename", bukuIndukFile.name)
                }

                if (ijazahFile != null) {
                    val b64 = Base64.encodeToString(ijazahFile.readBytes(), Base64.NO_WRAP)
                    put("ijazah_base64", b64)
                    put("ijazah_filename", ijazahFile.name)
                }
            }

            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder().url(WEB_APP_URL).post(body).build()

            http.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    onSuccess()
                } else {
                    val errorBody = resp.body?.string() ?: "Unknown error"
                    onError("${resp.code}: $errorBody")
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error saving student data", e)
            onError(e.message ?: "Unknown error")
        }
    }
}