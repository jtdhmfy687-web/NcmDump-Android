package com.ncmdump.app

import android.Manifest
import android.content.Intent
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

    private val selectedFiles = mutableListOf<Uri>()
    private var outputDirUri: Uri? = null

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

        outputDirUri?.let { uri ->
            try {
                val docId = DocumentsContract.getTreeDocumentId(uri)
                if (docId.startsWith("primary:")) {
                    val relPath = docId.substringAfter("primary:")
                    val path = "${Environment.getExternalStorageDirectory()}/$relPath"
                    val dir = java.io.File(path)
                    if (dir.exists() && dir.isDirectory) {
                        return path
                    }
                }
            } catch (_: Exception) {}
        }
        return defaultDir
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
                        val result = NcmCrypt.decrypt(cachePath, outputDir)
                        if (result.isNotEmpty()) {
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
