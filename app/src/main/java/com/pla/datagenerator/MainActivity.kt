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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding!!.root)
        } catch (e: Exception) {
            // JIKA TAMPILAN ERROR, TAMPILKAN PESAN DI LAYAR
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

    private fun prosesGenerate() {
        val apiKey = binding?.edtApiKey?.text.toString().trim()
        val manual = binding?.edtManual?.text.toString().trim()
        val jumlah = binding?.edtJumlah?.text.toString().toIntOrNull() ?: 50

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
        binding?.tvStatus?.text = "Menghubungi Gemini...\nTopik: $topikFinal"

        lifecycleScope.launch {
            val result = generateJson(apiKey, topikFinal, jumlah)
            if (result != null) {
                binding?.tvStatus?.text = "Menyimpan..."
                val safeName = topikFinal.replace(" ", "_").replace("/", "_").take(20)
                val fileName = "data_${safeName}_${System.currentTimeMillis()}.json"
                saveToDownloads(fileName, result)
                binding?.tvStatus?.text = "Berhasil!\nTersimpan di Download:\n$fileName"
            } else {
                binding?.tvStatus?.text = "Gagal. Cek API Key/Internet."
            }
            binding?.btnGenerate?.isEnabled = true
        }
    }

    private fun muatKatalog() {
        val jsonString = assets.open("katalog_topik.json").bufferedReader().use { it.readText() }
        katalog = JSONObject(jsonString)

        val kategoriList = mutableListOf<String>()
        katalog.keys().forEach { kategoriList.add(it) }

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
                    val subObj = katalog.getJSONObject(katTerpilih)
                    
                    currentSubKategoriList = subObj.keys().asSequence().toList()
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
                    if (position >= 0 && position < currentSubKategoriList.size) {
                        val katTerpilih = binding?.spinnerKategori?.selectedItem.toString()
                        val subTerpilih = currentSubKategoriList[position]
                        
                        val detailArr = katalog.getJSONObject(katTerpilih).getJSONArray(subTerpilih)
                        currentDetailList = (0 until detailArr.length()).map { detailArr.getString(it) }
                        
                        val adapter3 = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, currentDetailList)
                        binding?.spinnerDetail?.adapter = adapter3
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // Fungsi khusus untuk menampilkan pesan error di layar
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
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
            val sysPrompt = "Anda adalah generator JSON. Balas HANYA dengan JSON Array murni tanpa markdown. Format: [{\"id\": 1, \"topik\": \"$topik\", \"kategori\": \"...\", \"isi\": \"...\"}]"
            val fullPrompt = "Buatkan $count baris data untuk topik: $topik."

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

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val respBody = response.body?.string() ?: return@withContext null
                val candidates = JSONObject(respBody).getJSONArray("candidates")
                if (candidates.length() > 0) {
                    var text = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
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