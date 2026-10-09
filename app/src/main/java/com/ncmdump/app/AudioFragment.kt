package com.ncmdump.app

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment

class AudioFragment : Fragment() {

    private var currentChild: Fragment? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_audio, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.audioContent, MenuFragment())
                .commit()
        }
        childFragmentManager.addOnBackStackChangedListener {
            applyBgToChild()
        }
    }

    private fun applyBgToChild() {
        val bgUri = (activity as? MainActivity)?.getCustomBackgroundUri()
        if (bgUri != null) {
            childFragmentManager.fragments.forEach { frag ->
                frag.view?.let { (activity as? MainActivity)?.applyBackgroundToView(it, bgUri) }
            }
        }
    }

    fun openChild(fragment: Fragment) {
        currentChild = fragment
        childFragmentManager.beginTransaction()
            .setCustomAnimations(
                androidx.appcompat.R.anim.abc_slide_in_bottom,
                androidx.appcompat.R.anim.abc_fade_out,
                androidx.appcompat.R.anim.abc_fade_in,
                androidx.appcompat.R.anim.abc_slide_out_bottom
            )
            .replace(R.id.audioContent, fragment)
            .addToBackStack(null)
            .commit()
        view?.post { applyBgToChild() }
    }

    fun goBack(): Boolean {
        if (childFragmentManager.backStackEntryCount > 0) {
            childFragmentManager.popBackStack()
            currentChild = null
            return true
        }
        return false
    }

    // 宿主Activity回调：文件选择结果
    fun onFilesSelected(uris: List<Uri>) {
        when (currentChild) {
            is AudioParseFragment -> (currentChild as AudioParseFragment).onFilesSelected(uris)
            is AudioConvertFragment -> uris.firstOrNull()?.let { (currentChild as AudioConvertFragment).onFileSelected(it) }
            is AudioEditFragment -> (currentChild as AudioEditFragment).onFilesSelected(uris)
        }
    }

    // 宿主Activity回调：目录选择结果
    fun onDirSelected(uri: Uri) {
        (currentChild as? AudioParseFragment)?.onDirSelected(uri)
    }

    // 菜单Fragment
    class MenuFragment : Fragment() {
        override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
        ): View? {
            return inflater.inflate(R.layout.fragment_audio_menu, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            view.findViewById<LinearLayout>(R.id.menuParse).setOnClickListener {
                (parentFragment as AudioFragment).openChild(AudioParseFragment())
            }
            view.findViewById<LinearLayout>(R.id.menuConvert).setOnClickListener {
                (parentFragment as AudioFragment).openChild(AudioConvertFragment())
            }
            view.findViewById<LinearLayout>(R.id.menuEdit).setOnClickListener {
                (parentFragment as AudioFragment).openChild(AudioEditFragment())
            }
        }
    }
}
