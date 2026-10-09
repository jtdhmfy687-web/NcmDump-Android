package com.ncmdump.app

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout

class AudioFragment : Fragment() {

    private lateinit var tabLayout: TabLayout
    private val parseFragment = AudioParseFragment()
    private val convertFragment = AudioConvertFragment()
    private val editFragment = AudioEditFragment()
    private var currentTab = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_audio, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tabLayout = view.findViewById(R.id.tabLayout)

        tabLayout.addTab(tabLayout.newTab().setText("音频解析"))
        tabLayout.addTab(tabLayout.newTab().setText("音频转换"))
        tabLayout.addTab(tabLayout.newTab().setText("音频剪辑"))

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .add(R.id.audioContent, parseFragment)
                .add(R.id.audioContent, convertFragment)
                .add(R.id.audioContent, editFragment)
                .hide(convertFragment)
                .hide(editFragment)
                .commit()
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                switchTab(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun switchTab(position: Int) {
        val transaction = childFragmentManager.beginTransaction()
        listOf(parseFragment, convertFragment, editFragment).forEach { transaction.hide(it) }
        when (position) {
            0 -> transaction.show(parseFragment)
            1 -> transaction.show(convertFragment)
            2 -> transaction.show(editFragment)
        }
        transaction.commit()
        currentTab = position
    }

    // 宿主Activity回调：文件选择结果
    fun onFilesSelected(uris: List<Uri>) {
        when (currentTab) {
            0 -> parseFragment.onFilesSelected(uris)
            1 -> uris.firstOrNull()?.let { convertFragment.onFileSelected(it) }
            2 -> editFragment.onFilesSelected(uris)
        }
    }

    // 宿主Activity回调：目录选择结果
    fun onDirSelected(uri: Uri) {
        if (currentTab == 0) parseFragment.onDirSelected(uri)
    }
}
