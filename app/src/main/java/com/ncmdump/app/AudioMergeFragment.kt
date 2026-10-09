package com.ncmdump.app

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment

class AudioMergeFragment : Fragment() {

    private lateinit var btnSelect: Button
    private lateinit var btnMerge: Button
    private lateinit var tvFileList: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar

    private val mergeFiles = mutableListOf<Uri>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_audio_merge, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnSelect = view.findViewById(R.id.btnSelect)
        btnMerge = view.findViewById(R.id.btnMerge)
        tvFileList = view.findViewById(R.id.tvFileList)
        tvStatus = view.findViewById(R.id.tvStatus)
        progressBar = view.findViewById(R.id.progressBar)

        btnSelect.setOnClickListener {
            try { (requireActivity() as MainActivity).openFilePicker() }
            catch (e: Exception) { Toast.makeText(requireContext(), "打开文件选择器失败", Toast.LENGTH_SHORT).show() }
        }

        btnMerge.setOnClickListener {
            if (mergeFiles.size < 2) {
                Toast.makeText(requireContext(), "至少需要两个音频文件", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startMerge()
        }
    }

    fun onFilesSelected(uris: List<Uri>) {
        mergeFiles.clear()
        mergeFiles.addAll(uris)
        updateList()
    }

    private fun updateList() {
        val sb = StringBuilder("已选择 ${mergeFiles.size} 个文件:\n\n")
        mergeFiles.forEachIndexed { index, uri ->
            sb.append("${index + 1}. ${FileUtils.getFileName(requireContext(), uri)}\n")
        }
        tvFileList.text = sb.toString()
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
