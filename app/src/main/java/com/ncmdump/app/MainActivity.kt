package com.ncmdump.app

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var btnSelect: Button
    private lateinit var btnSelectDir: Button
    private lateinit var btnDecrypt: Button
    private lateinit var btnGrantPermission: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvFileList: TextView
    private lateinit var tvPermissionHint: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutControls: View

    private lateinit var prefs: SharedPreferences

    private val selectedFiles = mutableListOf<Uri>()
    private var outputDirUri: Uri? = null
    private var lastOutputPath: String = ""

    // 选择多个 ncm 文件
    private val pickFiles = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedFiles.clear()
            selectedFiles.addAll(uris)
            updateFileList()
        }
    }

    // 选择输出目录
    private val pickDir = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
            outputDirUri = it
            // 同时保存 Uri 字符串和真实路径
            val path = resolveTreeUriToPath(it)
            val editor = prefs.edit()
            editor.putString("last_output_uri", it.toString())
            if (path.isNotEmpty()) {
                lastOutputPath = path
                editor.putString("last_output_dir", path)
            }
            editor.apply()
            tvStatus.text = "输出目录: ${getDocumentFileName(it)}"
        }
    }

    // 传统存储权限申请（Android 10 及以下）
    private val requestLegacyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onPermissionGranted()
        } else {
            Toast.makeText(this, "存储权限被拒绝，无法使用", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("ncmdump", MODE_PRIVATE)
        lastOutputPath = prefs.getString("last_output_dir", "") ?: ""
        // 恢复上次的输出目录 Uri
        val savedUriStr = prefs.getString("last_output_uri", "")
        if (!savedUriStr.isNullOrEmpty()) {
            try {
                outputDirUri = Uri.parse(savedUriStr)
            } catch (_: Exception) {}
        }

        btnSelect = findViewById(R.id.btnSelect)
        btnSelectDir = findViewById(R.id.btnSelectDir)
        btnDecrypt = findViewById(R.id.btnDecrypt)
        btnGrantPermission = findViewById(R.id.btnGrantPermission)
        tvStatus = findViewById(R.id.tvStatus)
        tvFileList = findViewById(R.id.tvFileList)
        tvPermissionHint = findViewById(R.id.tvPermissionHint)
        progressBar = findViewById(R.id.progressBar)
        layoutControls = findViewById(R.id.layoutControls)

        btnGrantPermission.setOnClickListener {
            requestAllFilesPermission()
        }

        btnSelect.setOnClickListener {
            if (!hasStoragePermission()) {
                showPermissionRequired()
                return@setOnClickListener
            }
            try {
                pickFiles.launch(arrayOf("*/*"))
            } catch (e: Exception) {
                Toast.makeText(this, "打开文件选择器失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        btnSelectDir.setOnClickListener {
            if (!hasStoragePermission()) {
                showPermissionRequired()
                return@setOnClickListener
            }
            try {
                pickDir.launch(null)
            } catch (e: Exception) {
                Toast.makeText(this, "打开目录选择器失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        btnDecrypt.setOnClickListener {
            if (!hasStoragePermission()) {
                showPermissionRequired()
                return@setOnClickListener
            }
            if (selectedFiles.isEmpty()) {
                Toast.makeText(this, "请先选择 ncm 文件", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startDecrypt()
        }

        checkPermissionAndInit()
    }

    override fun onResume() {
        super.onResume()
        // 从设置页返回后重新检查权限
        if (hasStoragePermission()) {
            onPermissionGranted()
        }
    }

    private fun checkPermissionAndInit() {
        if (hasStoragePermission()) {
            onPermissionGranted()
        } else {
            showPermissionRequired()
        }
    }

    /**
     * 检查是否有存储访问权限
     * Android 11+ 检查 MANAGE_EXTERNAL_STORAGE，旧版检查 READ_EXTERNAL_STORAGE
     */
    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 申请所有文件访问权限
     * Android 11+ 跳转到设置页，旧版直接申请运行时权限
     */
    private fun requestAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                // 某些 ROM 不支持带包名的跳转，回退到通用设置页
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {
                    Toast.makeText(this, "请在设置中手动开启所有文件访问权限", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            requestLegacyPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun showPermissionRequired() {
        tvPermissionHint.visibility = View.VISIBLE
        btnGrantPermission.visibility = View.VISIBLE
        layoutControls.visibility = View.GONE
        tvPermissionHint.text = "需要「所有文件访问权限」才能读取和解密 ncm 文件\n\n请点击下方按钮，在设置中允许 NcmDump 访问所有文件"
    }

    private fun onPermissionGranted() {
        tvPermissionHint.visibility = View.GONE
        btnGrantPermission.visibility = View.GONE
        layoutControls.visibility = View.VISIBLE
        if (tvStatus.text.isNullOrEmpty() || tvStatus.text == "等待选择文件...") {
            tvStatus.text = "权限已授予，请选择 ncm 文件"
        }
    }

    private fun updateFileList() {
        val sb = StringBuilder()
        sb.append("已选择 ${selectedFiles.size} 个文件:\n\n")
        selectedFiles.forEachIndexed { index, uri ->
            sb.append("${index + 1}. ${getDocumentFileName(uri)}\n")
        }
        tvFileList.text = sb.toString()
    }

    private fun getDocumentFileName(uri: Uri): String {
        return try {
            var name = ""
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    name = cursor.getString(nameIndex)
                }
            }
            name.ifEmpty { uri.lastPathSegment ?: "unknown" }
        } catch (e: Exception) {
            uri.lastPathSegment ?: "unknown"
        }
    }

    /**
     * 将 Uri 拷贝到应用私有缓存目录，获取真实路径供 JNI 层使用
     */
    private fun copyUriToCache(uri: Uri): String? {
        val fileName = getDocumentFileName(uri)
        val cacheFile = java.io.File(cacheDir, fileName)
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (cacheFile.exists() && cacheFile.length() > 0) cacheFile.absolutePath else null
        } catch (e: Exception) {
            null
        }
    }

    private fun getOutputDirPath(): String {
        val defaultDir = getExternalFilesDir(null)?.absolutePath ?: cacheDir.absolutePath

        // 优先使用上次保存的输出目录
        if (lastOutputPath.isNotEmpty()) {
            val dir = java.io.File(lastOutputPath)
            if (dir.exists() && dir.isDirectory) {
                return lastOutputPath
            }
        }

        // 兜底：用保存的 Uri 重新解析路径
        outputDirUri?.let { uri ->
            val path = resolveTreeUriToPath(uri)
            if (path.isNotEmpty()) {
                val dir = java.io.File(path)
                if (dir.exists() && dir.isDirectory) {
                    lastOutputPath = path
                    return path
                }
            }
        }

        return defaultDir
    }

    /**
     * 将 SAF Tree Uri 解析为真实文件路径
     * 支持内置存储(primary:)和SD卡(xxxx-xxxx:)
     */
    private fun resolveTreeUriToPath(uri: Uri): String {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val colonIndex = docId.indexOf(':')
            if (colonIndex < 0) return ""

            val volume = docId.substring(0, colonIndex)
            val relPath = docId.substring(colonIndex + 1)

            when {
                volume == "primary" -> {
                    "${Environment.getExternalStorageDirectory()}/$relPath"
                }
                volume.matches(Regex("[A-F0-9]{4}-[A-F0-9]{4}")) -> {
                    // SD 卡路径
                    "/storage/$volume/$relPath"
                }
                else -> {
                    // 尝试通用方式
                    "/storage/$volume/$relPath"
                }
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun startDecrypt() {
        btnDecrypt.isEnabled = false
        progressBar.progress = 0
        progressBar.max = selectedFiles.size
        tvStatus.text = "正在解密..."

        thread {
            val outputDir = getOutputDirPath()
            var success = 0
            var failed = 0

            selectedFiles.forEachIndexed { index, uri ->
                val cachePath = copyUriToCache(uri)
                if (cachePath != null) {
                    try {
                        // 先获取元数据和封面（解密前从 ncm 文件读取）
                        val metadataJson = NcmCrypt.getMetadata(cachePath)
                        val coverBytes = NcmCrypt.getCoverImage(cachePath)
                        val coverMime = NcmCrypt.getCoverMime(cachePath)

                        val result = NcmCrypt.decrypt(cachePath, outputDir)
                        if (result.isNotEmpty()) {
                            // 写入元数据和封面（歌词单独生成 .lrc 文件）
                            try {
                                val json = org.json.JSONObject(metadataJson)
                                val title = json.optString("name", "")
                                val artist = json.optString("artist", "")
                                val album = json.optString("album", "")

                                MetadataWriter.write(result, title, artist, album, coverBytes, coverMime, null)

                                // 爬取歌词并单独生成 .lrc 文件
                                try {
                                    if (title.isNotEmpty()) {
                                        val lyrics = LyricFetcher.fetchLyrics(title, artist)
                                        if (!lyrics.isNullOrEmpty()) {
                                            val audioFile = java.io.File(result)
                                            val lrcName = audioFile.nameWithoutExtension + ".lrc"
                                            val lrcFile = java.io.File(audioFile.parentFile, lrcName)
                                            lrcFile.writeText(lyrics, Charsets.UTF_8)
                                        }
                                    }
                                } catch (_: Exception) {}
                            } catch (_: Exception) {}
                            success++
                        } else {
                            failed++
                        }
                    } catch (e: UnsatisfiedLinkError) {
                        failed++
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "原生库加载失败", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        failed++
                    } finally {
                        try {
                            java.io.File(cachePath).delete()
                        } catch (_: Exception) {}
                    }
                } else {
                    failed++
                }

                runOnUiThread {
                    progressBar.progress = index + 1
                    tvStatus.text = "解密中... ${index + 1}/${selectedFiles.size}"
                }
            }

            runOnUiThread {
                btnDecrypt.isEnabled = true
                tvStatus.text = "完成: 成功 $success, 失败 $failed\n输出到: $outputDir"
                Toast.makeText(this@MainActivity, "解密完成", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
