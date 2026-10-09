package com.ncmdump.app

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment

class AudioEditFragment : Fragment() {

    private lateinit var tabMode: TabHost
    private lateinit var btnSelectTrim: Button
    private lateinit var btnSelectMerge: Button
    private lateinit var btnTrim: Button
    private lateinit var btnMerge: Button
    private lateinit var etStartTime: EditText
    private lateinit var etDuration: EditText
    private lateinit var tvTrimFile: TextView
    private lateinit var tvMergeFiles: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar

    private var trimFile: Uri? = null
    private val mergeFiles = mutableListOf<Uri>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_audio_edit, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnSelectTrim = view.findViewById(R.id.btnSelectTrim)
        btnSelectMerge = view.findViewById(R.id.btnSelectMerge)
        btnTrim = view.findViewById(R.id.btnTrim)
        btnMerge = view.findViewById(R.id.btnMerge)
        etStartTime = view.findViewById(R.id.etStartTime)
        etDuration = view.findViewById(R.id.etDuration)
        tvTrimFile = view.findViewById(R.id.tvTrimFile)
        tvMergeFiles = view.findViewById(R.id.tvMergeFiles)
        tvStatus = view.findViewById(R.id.tvStatus)
        progressBar = view.findViewById(R.id.progressBar)

        btnSelectTrim.setOnClickListener {
            try { (requireActivity() as MainActivity).openFilePicker() }
            catch (e: Exception) { Toast.makeText(requireContext(), "打开文件选择器失败", Toast.LENGTH_SHORT).show() }
        }

        btnSelectMerge.setOnClickListener {
            try { (requireActivity() as MainActivity).openFilePicker() }
            catch (e: Exception) { Toast.makeText(requireContext(), "打开文件选择器失败", Toast.LENGTH_SHORT).show() }
        }

        btnTrim.setOnClickListener {
            val file = trimFile
            if (file == null) {
                Toast.makeText(requireContext(), "请先选择音频文件", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val startTime = etStartTime.text.toString().ifEmpty { "00:00:00" }
            val duration = etDuration.text.toString().ifEmpty { "00:00:10" }
            startTrim(file, startTime, duration)
        }

        btnMerge.setOnClickListener {
            if (mergeFiles.size < 2) {
                Toast.makeText(requireContext(), "至少需要两个音频文件", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startMerge()
        }
    }

    fun onFileSelected(uri: Uri) {
        // 根据当前可见的按钮判断是剪辑还是合并
        // 简单处理：如果剪辑文件为空，设置为剪辑文件；否则添加到合并列表
        if (trimFile == null) {
            trimFile = uri
            tvTrimFile.text = "已选择: ${FileUtils.getFileName(requireContext(), uri)}"
        } else {
            mergeFiles.add(uri)
            updateMergeList()
        }
    }

    fun onFilesSelected(uris: List<Uri>) {
        mergeFiles.clear()
        mergeFiles.addAll(uris)
        updateMergeList()
    }

    private fun updateMergeList() {
        val sb = StringBuilder("已选择 ${mergeFiles.size} 个文件:\n")
        mergeFiles.forEachIndexed { index, uri ->
            sb.append("${index + 1}. ${FileUtils.getFileName(requireContext(), uri)}\n")
        }
        tvMergeFiles.text = sb.toString()
    }

    private fun startTrim(inputUri: Uri, startTime: String, duration: String) {
        btnTrim.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "正在剪辑..."

        FfmpegHelper.trimAudio(requireContext(), inputUri, startTime, duration) { success, result ->
            requireActivity().runOnUiThread {
                btnTrim.isEnabled = true
                progressBar.visibility = View.GONE
                if (success) {
                    tvStatus.text = "剪辑完成\n输出: $result"
                    Toast.makeText(requireContext(), "剪辑成功", Toast.LENGTH_SHORT).show()
                } else {
                    tvStatus.text = "剪辑失败: $result"
                    Toast.makeText(requireContext(), "剪辑失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startMerge() {
        btnMerge.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "正在合并..."

        FfmpegHelper.mergeAudio(requireContext(), mergeFiles) { success, result ->
            requireActivity().runOnUiThread {
                btnMerge.isEnabled = true
                progressBar.visibility = View.GONE
                if (success) {
                    tvStatus.text = "合并完成\n输出: $result"
                    Toast.makeText(requireContext(), "合并成功", Toast.LENGTH_SHORT).show()
                } else {
                    tvStatus.text = "合并失败: $result"
                    Toast.makeText(requireContext(), "合并失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
