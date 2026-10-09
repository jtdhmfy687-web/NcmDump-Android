package com.ncmdump.app

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {

    private lateinit var tvVersion: TextView
    private lateinit var tvOutputDir: TextView
    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = requireContext().getSharedPreferences("ncmdump", android.content.Context.MODE_PRIVATE)

        tvVersion = view.findViewById(R.id.tvVersion)
        tvOutputDir = view.findViewById(R.id.tvOutputDir)

        try {
            val pkgInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            tvVersion.text = "版本 ${pkgInfo.versionName}"
        } catch (_: Exception) {
            tvVersion.text = "版本 1.4"
        }

        view.findViewById<View>(R.id.cardThemeSettings).setOnClickListener {
            startActivity(Intent(requireContext(), ThemeActivity::class.java))
        }

        view.findViewById<View>(R.id.cardGithub).setOnClickListener {
            val url = "https://github.com/muling0721/Analysis-Tool"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    override fun onResume() {
        super.onResume()
        val lastDir = prefs.getString("last_output_dir", "")
        tvOutputDir.text = if (lastDir.isNullOrEmpty()) "默认（应用目录）" else lastDir
    }
}
