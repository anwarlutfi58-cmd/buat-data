package com.pla.datagenerator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pla.datagenerator.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private var binding: ActivityMainBinding? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(180, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private var katalog: JSONObject = JSONObject()
    private var currentSubKategoriList: List<String> = emptyList()
    private var currentDetailList: List<String> = emptyList()

    private val PREFS_NAME = "AppPrefs"
    private val KEY_API = "api_key_tersimpan"
    private val MASTER_FILE = "dataset_ai.json"
    private val MASTER_JSONL = "dataset_ai.jsonl"

    private var hasilTerakhir: String = ""

    private val daftarGaya = listOf("Singkat (1 kalimat)", "Sedang (2-3 kalimat)", "Panjang (paragraf)", "Gaya Santai", "Gaya Formal")
    private val daftarBahasa = listOf("Bahasa Indonesia", "Bahasa Inggris", "Bahasa Arab")

    private val daftarInti = listOf(
        "Matematika", "Fisika", "Kimia", "Biologi", "Astronomi", "Geografi",
        "Sejarah Indonesia", "Sejarah Dunia", "Pancasila", "Ekonomi", "Akuntansi",
        "Bahasa Indonesia", "Bahasa Inggris", "Bahasa Arab", "Bahasa Jawa", "Bahasa Sunda",
        "Sastra", "Puisi", "Filsafat", "Psikologi", "Sosiologi", "Antropologi",
        "Hukum", "Politik", "Pemerintahan", "Kewarganegaraan",
        "Islam", "Hadits", "Tafsir", "Kristen", "Hindu", "Buddha",
        "Kesehatan", "Kedokteran", "Gizi", "Olahraga", "Kebugaran", "Kesehatan Mental",
        "Teknologi", "Komputer", "Internet", "Kecerdasan Buatan", "Robotika",
        "Pemrograman", "Kotlin", "Python", "JavaScript", "Database", "Jaringan Komputer",
        "Bisnis", "Pemasaran", "Manajemen", "Kewirausahaan", "UMKM", "Investasi",
        "Seni Rupa", "Musik", "Tari", "Teater", "Fotografi", "Desain",
        "Masakan", "Resep Nusantara", "Pertanian", "Peternakan", "Perikanan",
        "Lingkungan", "Iklim", "Energi Terbarukan", "Bencana Alam",
        "Parenting", "Pendidikan", "Metode Belajar", "Motivasi", "Produktivitas",
        "Komunikasi", "Kepemimpinan", "Negosiasi", "Public Speaking",
        "Wisata Indonesia", "Wisata Dunia", "Transportasi", "Arsitektur",
        "Otomotif", "Elektronika", "Konstruksi", "Pertambangan", "Kelautan",
        "Kedokteran Gigi", "Farmasi", "Keperawatan", "Kebidanan",
        "Jurnalistik", "Media Sosial", "Periklanan", "Hubungan Masyarakat",
        "Mode dan Fashion", "Kecantikan", "Kerajinan Tangan", "Fotografi Produk"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding!!.root)
        } catch (e: Exception) {
            tampilkanErrorDiLayar("Gagal memuat tampilan (Layout XML error):\n\n${e.message}\n\nPastikan file activity_main.xml sudah benar.")
            return
        }

        muatApiKeyTersimpan()
        perbaruiStatistik()

        // Isi spinner gaya & bahasa
        binding?.spinnerGaya?.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, daftarGaya)
        binding?.spinnerBahasa?.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, daftarBahasa)

        try {
            muatKatalog()
        } catch (e: Exception) {
            tampilkanErrorDiLayar("Gagal memuat katalog JSON:\n\n${e.message}\n\nPastikan file katalog_topik.json ada di folder app/src/main/assets/")
            return
        }

        binding?.btnGenerate?.setOnClickListener { prosesGenerate() }

        binding?.btnHapusKey?.setOnClickListener {
            hapusApiKey()
            binding?.edtApiKey?.setText("")
            Toast.makeText(this, "API Key dihapus.", Toast.LENGTH_SHORT).show()
        }

        binding?.btnSalin?.setOnClickListener {
            if (hasilTerakhir.isEmpty()) {
                Toast.makeText(this, "Belum ada hasil untuk disalin.", Toast.LENGTH_SHORT).show()
            } else {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("hasil", hasilTerakhir))
                Toast.makeText(this, "Hasil disalin ke clipboard!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun muatApiKeyTersimpan() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val apiKeyTersimpan = prefs.getString(KEY_API, "")
            if (!apiKeyTersimpan.isNullOrEmpty()) binding?.edtApiKey?.setText(apiKeyTersimpan)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun simpanApiKey(apiKey: String) {
        try {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_API, apiKey).apply()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun hapusApiKey() {
        try {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().remove(KEY_API).apply()
        } catch (e: Exception) { e.printStackTrace() }
    }

    // Fitur #3: hitung total data di file induk
    private fun perbaruiStatistik() {
        lifecycleScope.launch(Dispatchers.IO) {
            val total = hitungTotalData()
            withContext(Dispatchers.Main) {
                binding?.tvStatistik?.text = "Total data terkumpul: $total pasang tanya-jawab"
            }
        }
    }

    private fun hitungTotalData(): Int {
        return try {
            val uri = cariUriFile(MASTER_FILE) ?: return 0
            contentResolver.openInputStream(uri)?.use { input ->
                val teks = BufferedReader(InputStreamReader(input)).readText()
                if (teks.isBlank()) 0 else JSONArray(teks).length()
            } ?: 0
        } catch (e: Exception) { 0 }
    }

    private fun perluasDaftar(inti: List<String>): List<String> {
        val hasil = mutableListOf<String>()
        val variasi = listOf("", "Dasar", "Menengah", "Lanjutan", "Contoh Soal", "Istilah Penting", "Tokoh dan Sejarah")
        for (item in inti) for (v in variasi) hasil.add(if (v.isEmpty()) item else "$item - $v")
        return hasil
    }

    private fun prosesGenerate() {
        val apiKey = binding?.edtApiKey?.text.toString().trim()
        val manual = binding?.edtManual?.text.toString().trim()
        val jumlah = (binding?.edtJumlah?.text.toString().toIntOrNull() ?: 50).coerceIn(1, 300)
        val gaya = binding?.spinnerGaya?.selectedItem?.toString() ?: "Sedang (2-3 kalimat)"
        val bahasa = binding?.spinnerBahasa?.selectedItem?.toString() ?: "Bahasa Indonesia"
        val modeBatch = binding?.chkBatch?.isChecked == true
        val modeAppend = binding?.chkAppend?.isChecked == true

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Isi API Key dulu!", Toast.LENGTH_SHORT).show()
            return
        }
        simpanApiKey(apiKey)

        // Kumpulkan daftar topik yang akan digenerate
        val daftarTopik = mutableListOf<String>()
        if (manual.isNotEmpty()) {
            daftarTopik.add(manual)
        } else if (modeBatch) {
            // Fitur #6: batch semua detail di sub-kategori terpilih
            for (d in currentDetailList) {
                val kat = binding?.spinnerKategori?.selectedItem?.toString() ?: ""
                val sub = binding?.spinnerSub?.selectedItem?.toString() ?: ""
                daftarTopik.add("$kat $sub - $d")
            }
            if (daftarTopik.isEmpty()) {
                Toast.makeText(this, "Pilih kategori dari katalog dulu untuk mode batch.", Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            val kat = binding?.spinnerKategori?.selectedItem?.toString() ?: ""
            val sub = binding?.spinnerSub?.selectedItem?.toString() ?: ""
            val det = binding?.spinnerDetail?.selectedItem?.toString() ?: ""
            val topik = "$kat $sub - $det"
            if (topik == "  - ") {
                Toast.makeText(this, "Pilih topik dulu!", Toast.LENGTH_SHORT).show()
                return
            }
            daftarTopik.add(topik)
        }

        binding?.btnGenerate?.isEnabled = false
        binding?.btnHapusKey?.isEnabled = false

        lifecycleScope.launch {
            var totalBerhasil = 0
            for ((idx, topik) in daftarTopik.withIndex()) {
                binding?.tvStatus?.text = "[$idx+1/${daftarTopik.size}] Menghubungi Gemini...\nTopik: $topik"
                val result = generateJson(apiKey, topik, jumlah, gaya, bahasa)
                if (result != null) {
                    val bersih = bersihkanDanCegahDuplikat(result)
                    if (bersih.isNotEmpty()) {
                        hasilTerakhir = bersih
                        tampilkanPreview(bersih)
                        if (modeAppend) {
                            gabungKeFileInduk(bersih)
                            gabungKeJsonl(bersih)
                        } else {
                            val safeName = topik.replace(" ", "_").replace("/", "_").take(20)
                            simpanFileBaru("data_${safeName}_${System.currentTimeMillis()}.json", bersih)
                        }
                        totalBerhasil++
                    }
                } else {
                    binding?.tvStatus?.text = "Gagal di: $topik\nCek API Key/Internet."
                }
                if (idx < daftarTopik.size - 1) delay(3000) // jeda anti rate-limit
            }
            perbaruiStatistik()
            binding?.tvStatus?.text = "Selesai! $totalBerhasil/${daftarTopik.size} topik berhasil digenerate."
            binding?.btnGenerate?.isEnabled = true
            binding?.btnHapusKey?.isEnabled = true
        }
    }

    // Fitur #5: preview beberapa baris pertama
    private fun tampilkanPreview(jsonTeks: String) {
        try {
            val arr = JSONArray(jsonTeks)
            val sb = StringBuilder()
            val batas = minOf(3, arr.length())
            for (i in 0 until batas) sb.append(arr.getJSONObject(i).toString(2)).append("\n\n")
            if (arr.length() > batas) sb.append("... dan ${arr.length() - batas} baris lagi")
            binding?.tvPreview?.text = sb.toString()
        } catch (e: Exception) {
            binding?.tvPreview?.text = jsonTeks.take(300)
        }
    }

    // Fitur #4: cegah duplikat pertanyaan
    private fun bersihkanDanCegahDuplikat(jsonBaru: String): String {
        return try {
            val arrBaru = JSONArray(jsonBaru)
            val setPertanyaan = bacaPertanyaanYangAda()
            val hasil = JSONArray()
            for (i in 0 until arrBaru.length()) {
                val obj = arrBaru.getJSONObject(i)
                val q = obj.optString("pertanyaan", "").trim()
                if (q.isNotEmpty() && !setPertanyaan.contains(q.lowercase())) {
                    setPertanyaan.add(q.lowercase())
                    hasil.put(obj)
                }
            }
            hasil.toString()
        } catch (e: Exception) { jsonBaru }
    }

    private fun bacaPertanyaanYangAda(): MutableSet<String> {
        val set = mutableSetOf<String>()
        try {
            val uri = cariUriFile(MASTER_FILE) ?: return set
            contentResolver.openInputStream(uri)?.use { input ->
                val teks = BufferedReader(InputStreamReader(input)).readText()
                if (teks.isNotBlank()) {
                    val arr = JSONArray(teks)
                    for (i in 0 until arr.length()) {
                        val q = arr.getJSONObject(i).optString("pertanyaan", "").trim()
                        if (q.isNotEmpty()) set.add(q.lowercase())
                    }
                }
            }
        } catch (e: Exception) {}
        return set
    }

    // Fitur #1: gabung ke file induk JSON
    private fun gabungKeFileInduk(jsonBaru: String) {
        try {
            val arrBaru = JSONArray(jsonBaru)
            val gabungan = JSONArray()
            val uriLama = cariUriFile(MASTER_FILE)
            if (uriLama != null) {
                contentResolver.openInputStream(uriLama)?.use { input ->
                    val teks = BufferedReader(InputStreamReader(input)).readText()
                    if (teks.isNotBlank()) {
                        val lama = JSONArray(teks)
                        for (i in 0 until lama.length()) gabungan.put(lama.get(i))
                    }
                }
            }
            for (i in 0 until arrBaru.length()) gabungan.put(arrBaru.get(i))
            tulisKeDownloads(MASTER_FILE, gabungan.toString(), "application/json", uriLama)
        } catch (e: Exception) { e.printStackTrace() }
    }

    // Fitur #2: export JSONL (satu baris satu data) untuk fine-tuning
    private fun gabungKeJsonl(jsonBaru: String) {
        try {
            val arrBaru = JSONArray(jsonBaru)
            val uriLama = cariUriFile(MASTER_JSONL)
            val sb = StringBuilder()
            if (uriLama != null) {
                contentResolver.openInputStream(uriLama)?.use { input ->
                    sb.append(BufferedReader(InputStreamReader(input)).readText())
                    if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
                }
            }
            for (i in 0 until arrBaru.length()) sb.append(arrBaru.getJSONObject(i).toString()).append("\n")
            tulisKeDownloads(MASTER_JSONL, sb.toString(), "application/jsonl", uriLama)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun cariUriFile(namaFile: String): android.net.Uri? {
        return try {
            val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
            val cursor = contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, null, null, null)
            var hasil: android.net.Uri? = null
            cursor?.use {
                val idxId = it.getColumnIndex(MediaStore.MediaColumns._ID)
                val idxName = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                while (it.moveToNext()) {
                    if (it.getString(idxName) == namaFile) {
                        hasil = android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, it.getLong(idxId))
                        break
                    }
                }
            }
            hasil
        } catch (e: Exception) { null }
    }

    private fun tulisKeDownloads(namaFile: String, isi: String, mime: String, uriUpdate: android.net.Uri?) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (uriUpdate != null) {
                    contentResolver.openOutputStream(uriUpdate, "wt")?.use { out -> out.write(isi.toByteArray()) }
                } else {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, namaFile)
                        put(MediaStore.MediaColumns.MIME_TYPE, mime)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    uri?.let { contentResolver.openOutputStream(it)?.use { out -> out.write(isi.toByteArray()) } }
                }
            } else {
                val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), namaFile)
                file.writeText(isi)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun simpanFileBaru(namaFile: String, isi: String) {
        tulisKeDownloads(namaFile, isi, "application/json", null)
    }

    private fun muatKatalog() {
        val jsonString = assets.open("katalog_topik.json").bufferedReader().use { it.readText() }
        katalog = JSONObject(jsonString)

        val kategoriList = mutableListOf<String>()
        katalog.keys().forEach { kategoriList.add(it) }
        kategoriList.addAll(perluasDaftar(daftarInti))

        if (kategoriList.isEmpty()) {
            Toast.makeText(this, "Katalog JSON kosong.", Toast.LENGTH_SHORT).show()
            return
        }

        binding?.spinnerKategori?.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, kategoriList)

        binding?.spinnerKategori?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                try {
                    val katTerpilih = kategoriList[position]
                    currentSubKategoriList = if (katalog.has(katTerpilih)) {
                        katalog.getJSONObject(katTerpilih).keys().asSequence().toList()
                    } else listOf("Umum", "Konsep", "Contoh", "Latihan")
                    binding?.spinnerSub?.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, currentSubKategoriList)
                } catch (e: Exception) { e.printStackTrace() }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding?.spinnerSub?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                try {
                    val katTerpilih = binding?.spinnerKategori?.selectedItem.toString()
                    currentDetailList = if (katalog.has(katTerpilih) && position in currentSubKategoriList.indices) {
                        val subTerpilih = currentSubKategoriList[position]
                        val detailArr = katalog.getJSONObject(katTerpilih).getJSONArray(subTerpilih)
                        (0 until detailArr.length()).map { detailArr.getString(it) }
                    } else listOf("Penjelasan", "Tanya Jawab", "Ringkasan", "Studi Kasus")
                    binding?.spinnerDetail?.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, currentDetailList)
                } catch (e: Exception) { e.printStackTrace() }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun tampilkanErrorDiLayar(pesanError: String) {
        val scrollView = ScrollView(this)
        val linearLayout = LinearLayout(this)
        linearLayout.orientation = LinearLayout.VERTICAL
        linearLayout.setPadding(32, 32, 32, 32)
        linearLayout.setBackgroundColor(Color.BLACK)
        val tv = TextView(this)
        tv.text = pesanError
        tv.setTextColor(Color.RED)
        tv.textSize = 16f
        tv.gravity = Gravity.START
        linearLayout.addView(tv)
        scrollView.addView(linearLayout)
        setContentView(scrollView)
    }

    private suspend fun generateJson(apiKey: String, topik: String, count: Int, gaya: String, bahasa: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=$apiKey"
            val instruksiGaya = when {
                gaya.contains("Singkat") -> "Jawaban sangat singkat, cukup 1 kalimat padat."
                gaya.contains("Panjang") -> "Jawaban panjang dan mendalam, berupa paragraf penjelas lengkap."
                gaya.contains("Santai") -> "Gunakan gaya bahasa santai dan mudah dipahami sehari-hari."
                gaya.contains("Formal") -> "Gunakan gaya bahasa formal dan baku."
                else -> "Jawaban jelas dan lengkap, 2-4 kalimat."
            }
            val sysPrompt = "Anda adalah generator dataset tanya-jawab dalam $bahasa untuk melatih AI. Balas HANYA JSON Array murni tanpa markdown. Format: [{\"id\": 1, \"topik\": \"$topik\", \"pertanyaan\": \"...\", \"jawaban\": \"...\"}]"
            val fullPrompt = "Buatkan $count pasang tanya-jawab yang edukatif, bervariasi, dan tidak berulang untuk topik: $topik. $instruksiGaya"

            val jsonBody = JSONObject()
            val contents = JSONArray()
            val parts = JSONArray()
            val part = JSONObject()
            part.put("text", "$sysPrompt\n\n$fullPrompt")
            parts.put(part)
            val content = JSONObject()
            content.put("parts", parts)
            contents.put(content)
            jsonBody.put("contents", contents)

            val genConfig = JSONObject()
            genConfig.put("responseMimeType", "application/json")
            jsonBody.put("generationConfig", genConfig)

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val respBody = response.body?.string() ?: return@withContext null
                val candidates = JSONObject(respBody).getJSONArray("candidates")
                if (candidates.length() > 0) {
                    val partsArr = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                    val sb = StringBuilder()
                    for (i in 0 until partsArr.length()) sb.append(partsArr.getJSONObject(i).optString("text", ""))
                    var text = sb.toString().replace("```json", "").replace("```", "").trim()
                    return@withContext text
                }
            }
            return@withContext null
        } catch (e: Exception) { return@withContext null }
    }
}