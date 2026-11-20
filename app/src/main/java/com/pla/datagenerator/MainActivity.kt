package com.pla.datagenerator

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
    private lateinit var binding: ActivityMainBinding
    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGenerate.setOnClickListener {
            val apiKey = binding.edtApiKey.text.toString().trim()
            val prompt = binding.edtPrompt.text.toString().trim()
            val jumlah = binding.edtJumlah.text.toString().toIntOrNull() ?: 50

            if (apiKey.isEmpty() || prompt.isEmpty()) {
                Toast.makeText(this, "API Key dan Topik wajib diisi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnGenerate.isEnabled = false
            binding.tvStatus.text = "Menghubungi Gemini API..."

            lifecycleScope.launch {
                val result = generateJson(apiKey, prompt, jumlah)
                if (result != null) {
                    binding.tvStatus.text = "Menyimpan file..."
                    val fileName = "data_${prompt.replace(" ", "_").take(10)}_${System.currentTimeMillis()}.json"
                    saveToDownloads(fileName, result)
                    binding.tvStatus.text = "Berhasil!\nFile tersimpan di folder Download:\n$fileName"
                } else {
                    binding.tvStatus.text = "Gagal generate. Cek API Key/Internet."
                }
                binding.btnGenerate.isEnabled = true
            }
        }
    }

    private suspend fun generateJson(apiKey: String, topik: String, count: Int): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
            val sysPrompt = "Anda adalah generator JSON. Balas HANYA dengan JSON Array murni tanpa markdown. Format: [{\"id\": 1, \"topik\": \"$topik\", \"kategori\": \"...\", \"isi\": \"...\"}]"
            val fullPrompt = "Buatkan $count baris data untuk topik $topik."

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