package com.ncmdump.app

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment

class AudioConvertFragment : Fragment() {

    private lateinit var btnSelect: Button
    private lateinit var btnConvert: Button
    private lateinit var spinnerFormat: Spinner
    private lateinit var spinnerBitrate: Spinner
    private lateinit var tvStatus: TextView
    private lateinit var tvFileList: TextView
    private lateinit var progressBar: ProgressBar

    private var selectedFile: Uri? = null
    private val bitrates = listOf("128k", "192k", "256k", "320k")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_audio_convert, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnSelect = view.findViewById(R.id.btnSelect)
        btnConvert = view.findViewById(R.id.btnConvert)
        spinnerFormat = view.findViewById(R.id.spinnerFormat)
        spinnerBitrate = view.findViewById(R.id.spinnerBitrate)
        tvStatus = view.findViewById(R.id.tvStatus)
        tvFileList = view.findViewById(R.id.tvFileList)
        progressBar = view.findViewById(R.id.progressBar)

        // 格式选择器
        val formatAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            FfmpegHelper.audioFormats
        )
        spinnerFormat.adapter = formatAdapter
        spinnerFormat.setSelection(0)

        // 码率选择器
        val bitrateAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            bitrates
        )
        spinnerBitrate.adapter = bitrateAdapter
        spinnerBitrate.setSelection(1) // 默认192k

        btnSelect.setOnClickListener {
            try { (requireActivity() as MainActivity).openFilePicker() }
            catch (e: Exception) { Toast.makeText(requireContext(), "打开文件选择器失败", Toast.LENGTH_SHORT).show() }
        }

        btnConvert.setOnClickListener {
            val file = selectedFile
            if (file == null) {
                Toast.makeText(requireContext(), "请先选择音频文件", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val format = spinnerFormat.selectedItem.toString()
            val bitrate = spinnerBitrate.selectedItem.toString()
            startConvert(file, format, bitrate)
        }
    }

    fun onFileSelected(uri: Uri) {
        selectedFile = uri
        tvFileList.text = "已选择: ${FileUtils.getFileName(requireContext(), uri)}"
    }

    private fun startConvert(inputUri: Uri, format: String, bitrate: String) {
        btnConvert.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "正在转换..."

        FfmpegHelper.convertAudio(requireContext(), inputUri, format, bitrate) { success, result ->
            requireActivity().runOnUiThread {
                btnConvert.isEnabled = true
                progressBar.visibility = View.GONE
                if (success) {
                    tvStatus.text = "转换完成\n输出: $result"
                    Toast.makeText(requireContext(), "转换成功", Toast.LENGTH_SHORT).show()
                } else {
                    tvStatus.text = "转换失败: $result"
                    Toast.makeText(requireContext(), "转换失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
