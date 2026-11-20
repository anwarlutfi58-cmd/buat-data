package com.pla.datagenerator

import android.content.ContentValues
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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

    // Daftar inti untuk diperluas otomatis menjadi ratusan pilihan
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

        try {
            muatKatalog()
        } catch (e: Exception) {
            tampilkanErrorDiLayar("Gagal memuat katalog JSON:\n\n${e.message}\n\nPastikan file katalog_topik.json ada di folder app/src/main/assets/")
            return
        }

        binding?.btnGenerate?.setOnClickListener {
            prosesGenerate()
        }
    }

    // Memperluas daftar inti jadi ratusan pilihan: "X", "X - Dasar", "X - Menengah", ...
    private fun perluasDaftar(inti: List<String>): List<String> {
        val hasil = mutableListOf<String>()
        val variasi = listOf("", "Dasar", "Menengah", "Lanjutan", "Contoh Soal", "Istilah Penting", "Tokoh dan Sejarah")
        for (item in inti) {
            for (v in variasi) {
                hasil.add(if (v.isEmpty()) item else "$item - $v")
            }
        }
        return hasil
    }

    private fun prosesGenerate() {
        val apiKey = binding?.edtApiKey?.text.toString().trim()
        val manual = binding?.edtManual?.text.toString().trim()
        val jumlah = (binding?.edtJumlah?.text.toString().toIntOrNull() ?: 50).coerceIn(1, 300)

        val topikFinal = if (manual.isNotEmpty()) {
            manual
        } else {
            val kategori = binding?.spinnerKategori?.selectedItem?.toString() ?: ""
            val sub = binding?.spinnerSub?.selectedItem?.toString() ?: ""
            val detail = binding?.spinnerDetail?.selectedItem?.toString() ?: ""
            "$kategori $sub - $detail"
        }

        if (apiKey.isEmpty() || topikFinal.isEmpty() || topikFinal == "  - ") {
            Toast.makeText(this, "Isi API Key dan Topik!", Toast.LENGTH_SHORT).show()
            return
        }

        binding?.btnGenerate?.isEnabled = false
        binding?.tvStatus?.text = "Menghubungi Gemini...\nTopik: $topikFinal\nJumlah: $jumlah tanya-jawab"

        lifecycleScope.launch {
            val result = generateJson(apiKey, topikFinal, jumlah)
            if (result != null) {
                binding?.tvStatus?.text = "Menyimpan..."
                val safeName = topikFinal.replace(" ", "_").replace("/", "_").take(20)
                val fileName = "data_${safeName}_${System.currentTimeMillis()}.json"
                saveToDownloads(fileName, result)
                binding?.tvStatus?.text = "Berhasil!\nTersimpan di Download:\n$fileName"
            } else {
                binding?.tvStatus?.text = "Gagal. Cek API Key/Internet.\nKalau jumlah 300 gagal, coba 100 dulu."
            }
            binding?.btnGenerate?.isEnabled = true
        }
    }

    private fun muatKatalog() {
        val jsonString = assets.open("katalog_topik.json").bufferedReader().use { it.readText() }
        katalog = JSONObject(jsonString)

        // Spinner 1: kunci katalog + ratusan topik hasil perluasan
        val kategoriList = mutableListOf<String>()
        katalog.keys().forEach { kategoriList.add(it) }
        kategoriList.addAll(perluasDaftar(daftarInti))

        if (kategoriList.isEmpty()) {
            Toast.makeText(this, "Katalog JSON kosong.", Toast.LENGTH_SHORT).show()
            return
        }

        val adapter1 = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, kategoriList)
        binding?.spinnerKategori?.adapter = adapter1

        binding?.spinnerKategori?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                try {
                    val katTerpilih = kategoriList[position]
                    if (katalog.has(katTerpilih)) {
                        // Pilihan dari file JSON
                        val subObj = katalog.getJSONObject(katTerpilih)
                        currentSubKategoriList = subObj.keys().asSequence().toList()
                    } else {
                        // Pilihan hasil perluasan -> sub & detail dibuat otomatis
                        currentSubKategoriList = listOf("Umum", "Konsep", "Contoh", "Latihan")
                    }
                    val adapter2 = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, currentSubKategoriList)
                    binding?.spinnerSub?.adapter = adapter2
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding?.spinnerSub?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                try {
                    val katTerpilih = binding?.spinnerKategori?.selectedItem.toString()
                    if (katalog.has(katTerpilih)) {
                        if (position >= 0 && position < currentSubKategoriList.size) {
                            val subTerpilih = currentSubKategoriList[position]
                            val detailArr = katalog.getJSONObject(katTerpilih).getJSONArray(subTerpilih)
                            currentDetailList = (0 until detailArr.length()).map { detailArr.getString(it) }
                        }
                    } else {
                        currentDetailList = listOf("Penjelasan", "Tanya Jawab", "Ringkasan", "Studi Kasus")
                    }
                    val adapter3 = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, currentDetailList)
                    binding?.spinnerDetail?.adapter = adapter3
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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

    private suspend fun generateJson(apiKey: String, topik: String, count: Int): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=$apiKey"
            val sysPrompt = "Anda adalah generator dataset tanya-jawab dalam Bahasa Indonesia untuk melatih AI. Balas HANYA JSON Array murni tanpa markdown. Format: [{\"id\": 1, \"topik\": \"$topik\", \"pertanyaan\": \"...\", \"jawaban\": \"...\"}]"
            val fullPrompt = "Buatkan $count pasang tanya-jawab yang edukatif, bervariasi, dan tidak berulang untuk topik: $topik. Jawaban jelas dan lengkap, 2-4 kalimat."

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
                    for (i in 0 until partsArr.length()) {
                        sb.append(partsArr.getJSONObject(i).optString("text", ""))
                    }
                    var text = sb.toString()
                    text = text.replace("```json", "").replace("```", "").trim()
                    return@withContext text
                }
            }
            return@withContext null
        } catch (e: Exception) {
            return@withContext null
        }
    }

    private fun saveToDownloads(fileName: String, jsonText: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { out ->
                        out.write(jsonText.toByteArray())
                    }
                }
            } else {
                val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                file.writeText(jsonText)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}