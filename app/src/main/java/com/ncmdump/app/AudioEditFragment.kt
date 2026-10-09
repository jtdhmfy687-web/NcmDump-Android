package com.ncmdump.app

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment

class AudioEditFragment : Fragment() {

    private lateinit var btnSelectTrim: Button
    private lateinit var btnSelectMerge: Button
    private lateinit var btnTrim: Button
    private lateinit var btnMerge: Button
    private lateinit var trimView: AudioTrimView
    private lateinit var tvTrimFile: TextView
    private lateinit var tvMergeFiles: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutTrim: View

    private var trimFile: Uri? = null
    private val mergeFiles = mutableListOf<Uri>()
    private var trimStartMs: Long = 0
    private var trimEndMs: Long = 0

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
        trimView = view.findViewById(R.id.trimView)
        tvTrimFile = view.findViewById(R.id.tvTrimFile)
        tvMergeFiles = view.findViewById(R.id.tvMergeFiles)
        tvStatus = view.findViewById(R.id.tvStatus)
        progressBar = view.findViewById(R.id.progressBar)
        layoutTrim = view.findViewById(R.id.layoutTrim)

        trimView.onTrimChanged = { start, end ->
            trimStartMs = start
            trimEndMs = end
        }

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
            if (trimEndMs <= trimStartMs) {
                Toast.makeText(requireContext(), "请选择有效区间", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startTrim(file)
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
        if (trimFile == null) {
            trimFile = uri
            tvTrimFile.text = FileUtils.getFileName(requireContext(), uri)
            loadDuration(uri)
            layoutTrim.visibility = View.VISIBLE
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

    private fun loadDuration(uri: Uri) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(requireContext(), uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0
            retriever.release()
            if (duration > 0) {
                trimView.durationMs = duration
                trimStartMs = 0
                trimEndMs = duration
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "无法获取音频时长", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateMergeList() {
        val sb = StringBuilder("已选择 ${mergeFiles.size} 个文件:\n")
        mergeFiles.forEachIndexed { index, uri ->
            sb.append("${index + 1}. ${FileUtils.getFileName(requireContext(), uri)}\n")
        }
        tvMergeFiles.text = sb.toString()
    }

    private fun startTrim(inputUri: Uri) {
        btnTrim.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "正在剪辑..."

        val startTime = formatTime(trimStartMs)
        val duration = formatTime(trimEndMs - trimStartMs)

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

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
               else String.format("%02d:%02d", m, s)
    }
}
