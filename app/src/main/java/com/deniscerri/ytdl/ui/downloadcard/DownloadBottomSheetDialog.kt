package com.deniscerri.ytdl.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadCardViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdl.database.viewmodel.MusicViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.receiver.ShareActivity
import com.deniscerri.ytdl.ui.BaseActivity
import com.deniscerri.ytdl.ui.more.cookies.WebViewActivity
import com.deniscerri.ytdl.util.LastUsedDownloadSettings
import com.deniscerri.ytdl.util.UiUtil
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL


class DownloadBottomSheetDialog : BottomSheetDialogFragment() {
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager2: ViewPager2
    private lateinit var fragmentAdapter : DownloadFragmentAdapter
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var downloadCardViewModel: DownloadCardViewModel
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var commandTemplateViewModel : CommandTemplateViewModel
    private lateinit var sharedPreferences : SharedPreferences
    private lateinit var updateItem : Button
    private lateinit var view: View
    private lateinit var shimmerLoading :ShimmerFrameLayout
    private lateinit var title : View
    private lateinit var shimmerLoadingSubtitle : ShimmerFrameLayout
    private lateinit var subtitle : TextView
    private lateinit var loadingSubtitle : TextView
    private lateinit var musicBtn : Button
    private lateinit var downloadBtn : Button
    private lateinit var refreshBtn : Button
    private lateinit var parentActivity: BaseActivity
    private lateinit var musicViewModel: MusicViewModel

    /**
     * What the card is busy with, which is the whole of what its subtitle says and whether the
     * download button is a download button at all. Video info comes first: nothing else about
     * the item is knowable until it lands.
     */
    private enum class CardStatus { FetchingVideo, VideoFailed, SearchingSong, SongFailed, Ready }

    /**
     * How the video info fetch ended, owned by the card itself.
     *
     * It is deliberately not read back off [ResultViewModel.updateResultData]: that flow is an
     * event channel, emptied again as soon as the result is consumed, so asking it afterwards
     * says "nothing here" for a fetch that in fact succeeded. The outcome is recorded once, by
     * whichever event settles it, and every reader agrees from then on.
     *
     * [Running] is marked where the fetch is started rather than observed from it, since a fetch
     * can finish before the collectors exist and a state flow replays only where it ended up.
     */
    private enum class VideoFetch { None, Running, Loaded, Failed }

    private var videoFetch = VideoFetch.None


    private lateinit var result: ResultItem
    private lateinit var type: DownloadType
    private var ignoreDuplicates: Boolean = false
    private var disableUpdateData : Boolean = false
    private var currentDownloadItem: DownloadItem? = null
    private var incognito: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        historyViewModel = ViewModelProvider(requireActivity())[HistoryViewModel::class.java]
        resultViewModel = ViewModelProvider(requireActivity())[ResultViewModel::class.java]
        commandTemplateViewModel = ViewModelProvider(requireActivity())[CommandTemplateViewModel::class.java]
        downloadCardViewModel = ViewModelProvider(requireActivity())[DownloadCardViewModel::class.java]
        musicViewModel = ViewModelProvider(requireActivity())[MusicViewModel::class.java]
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val res = downloadCardViewModel.resultItem
        val dwl = downloadCardViewModel.downloadItem

        type = arguments?.getSerializable("type") as DownloadType
        disableUpdateData = arguments?.getBoolean("disableUpdateData") == true
        ignoreDuplicates = arguments?.getBoolean("ignore_duplicates") == true

        if (res == null){
            dismiss()
            return
        }
        result = res
        currentDownloadItem = dwl
        //an item being reopened keeps its own setting, a fresh card reopens on the last one used
        incognito = currentDownloadItem?.incognito ?: LastUsedDownloadSettings.lastIncognito(
            sharedPreferences, sharedPreferences.getBoolean("incognito", false)
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val downloadItem = getDownloadItem()
        downloadCardViewModel.setResultItem(result)
        downloadCardViewModel.setDownloadItem(downloadItem)
        arguments?.putSerializable("type", downloadItem.type)
    }

    @SuppressLint("RestrictedApi", "InflateParams")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        view = LayoutInflater.from(context).inflate(R.layout.download_bottom_sheet, null)
        dialog.setContentView(view)
        dialog.window?.navigationBarColor = SurfaceColors.SURFACE_1.getColor(requireActivity())
        parentActivity = activity as BaseActivity

        dialog.setOnShowListener {
            behavior = BottomSheetBehavior.from(view.parent as View)
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            if(resources.getBoolean(R.bool.isTablet) || resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE){
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.peekHeight = displayMetrics.heightPixels
            }
        }

        tabLayout = view.findViewById(R.id.download_tablayout)
        viewPager2 = view.findViewById(R.id.download_viewpager)
        updateItem = view.findViewById(R.id.update_item)
        viewPager2.isUserInputEnabled = sharedPreferences.getBoolean("swipe_gestures_download_card", true)


        //loading shimmers
        shimmerLoading = view.findViewById(R.id.shimmer_loading_title)
        title = view.findViewById(R.id.bottom_sheet_title)
        shimmerLoadingSubtitle = view.findViewById(R.id.shimmer_loading_subtitle)
        subtitle = view.findViewById(R.id.bottom_sheet_subtitle)
        loadingSubtitle = view.findViewById(R.id.bottom_sheet_loading_subtitle)

        shimmerLoading.setOnClickListener {
            lifecycleScope.launch {
                resultViewModel.cancelUpdateItemData()
                (updateItem.parent as LinearLayout).visibility = View.VISIBLE
            }
        }


        (viewPager2.getChildAt(0) as? RecyclerView)?.apply {
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        var commandTemplateNr = 0
        lifecycleScope.launch{
            withContext(Dispatchers.IO){
                commandTemplateNr = commandTemplateViewModel.getTotalNumber()
                if (!Patterns.WEB_URL.matcher(result.url).matches()) commandTemplateNr++
                if(commandTemplateNr <= 0){
                    (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(2)?.isClickable = true
                    (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(2)?.alpha = 0.3f
                }
            }
        }

        //check if the item has formats and its audio-only
        val formats = result.formats
        var isAudioOnly = formats.isNotEmpty() && formats.none { !it.format_note.contains("audio") }
        if (isAudioOnly){
            (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(1)?.isClickable = true
            (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(1)?.alpha = 0.3f
        }

        //remove outdated player url of 1hr so it can refetch it in the cut player
        if (result.creationTime > System.currentTimeMillis() - 3600000) result.urls = ""
        val fragmentManager = parentFragmentManager
        fragmentAdapter = DownloadFragmentAdapter(
            fragmentManager,
            lifecycle,
            result,
            currentDownloadItem,
            nonSpecific = result.url.endsWith(".txt"),
            isIncognito = incognito
        )

        viewPager2.adapter = fragmentAdapter
        viewPager2.isSaveFromParentEnabled = false

        /*
         * The tab the card belongs on, settled before the first layout pass so the pager lays
         * out on it directly. Picking it afterwards would show the first tab for a frame and
         * then jump, which reads as the card changing its mind about the last used type.
         */
        val startTab = when(type) {
            DownloadType.audio -> 0
            DownloadType.video -> if (isAudioOnly) 0 else 1
            else -> 2
        }
        tabLayout.getTabAt(startTab)!!.select()
        viewPager2.setCurrentItem(startTab, false)

        view.post {
            if (type == DownloadType.video && isAudioOnly) {
                Toast.makeText(context, getString(R.string.audio_only_item), Toast.LENGTH_SHORT).show()
            }

            //check if the item is coming from a text file
            val isCommandOnly = (type == DownloadType.command && !Patterns.WEB_URL.matcher(result.url).matches())
            if (isCommandOnly){
                (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(0)?.isClickable = false
                (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(0)?.alpha = 0.3f

                (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(1)?.isClickable = false
                (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(1)?.alpha = 0.3f

                (updateItem.parent as LinearLayout).visibility = View.GONE
            }
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab!!.position == 2 && commandTemplateNr == 0){
                    tabLayout.selectTab(tabLayout.getTabAt(1))
                    val s = Snackbar.make(view, getString(R.string.add_template_first), Snackbar.LENGTH_LONG)
                    val snackbarView: View = s.view
                    val snackTextView = snackbarView.findViewById<View>(com.google.android.material.R.id.snackbar_text) as TextView
                    snackTextView.maxLines = 9999999
                    s.setAction(R.string.new_template){
                        UiUtil.showCommandTemplateCreationOrUpdatingSheet(
                            item = null, context = requireActivity(), lifeCycle = this@DownloadBottomSheetDialog, commandTemplateViewModel = commandTemplateViewModel,
                            newTemplate = {
                                commandTemplateNr = 1
                                (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(2)?.isClickable = true
                                (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(2)?.alpha = 1f
                                tabLayout.selectTab(tabLayout.getTabAt(2))
                            },
                            dismissed = {

                            }
                        )
                    }
                    s.show()
                }else if (tab.position == 1 && isAudioOnly){
                    tabLayout.selectTab(tabLayout.getTabAt(0))
                    Toast.makeText(context, getString(R.string.audio_only_item), Toast.LENGTH_SHORT).show()
                }
                else{
                    viewPager2.setCurrentItem(tab.position, false)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
            }
        })

        viewPager2.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                tabLayout.selectTab(tabLayout.getTabAt(position))
                showMusicButtonFor(position)
                runCatching {
                    fragmentAdapter.updateWhenSwitching(viewPager2.currentItem)
                }
            }
        })

        viewPager2.setPageTransformer(BackgroundToForegroundPageTransformer())

        val shownFields = sharedPreferences.getStringSet("modify_download_card", requireContext().resources.getStringArray(R.array.modify_download_card_values).toSet())!!.toList()

        val scheduleBtn = view.findViewById<MaterialButton>(R.id.bottomsheet_schedule_button)
        scheduleBtn.visibility = if(shownFields.contains("schedule")){
            View.VISIBLE
        }else{
            View.GONE
        }
        downloadBtn = view.findViewById(R.id.bottomsheet_download_button)
        refreshBtn = view.findViewById(R.id.bottomsheet_refresh_button)
        musicBtn = view.findViewById(R.id.bottomsheet_music_button)
        showMusicButtonFor(viewPager2.currentItem)
        val download = downloadBtn

        //music mode lives here now, so it can be switched without opening the audio tab first
        musicBtn.setOnClickListener {
            val enabled = !musicViewModel.enabled.value
            musicViewModel.setEnabled(enabled)
            LastUsedDownloadSettings.rememberMusicMode(sharedPreferences, enabled)
        }
        refreshBtn.setOnClickListener { retryFailedWork() }


        scheduleBtn.setOnClickListener{
            UiUtil.showDatePicker(fragmentManager, sharedPreferences) {
                lifecycleScope.launch {
                    resultViewModel.cancelUpdateItemData()
                    resultViewModel.cancelUpdateFormatsItemData()
                }

                scheduleBtn.isEnabled = false
                download.isEnabled = false
                val item: DownloadItem = getDownloadItem()
                item.status = DownloadRepository.Status.Scheduled.toString()
                item.downloadStartTime = it.timeInMillis
                if (item.videoPreferences.alsoDownloadAsAudio){
                    val itemsToQueue = mutableListOf<DownloadItem>()
                    itemsToQueue.add(item)

                    getAlsoAudioDownloadItem(finished = { audioDownloadItem ->
                        audioDownloadItem.downloadStartTime = it.timeInMillis
                        audioDownloadItem.status = DownloadRepository.Status.Scheduled.toString()
                        itemsToQueue.add(audioDownloadItem)

                        lifecycleScope.launch {
                            val result = withContext(Dispatchers.IO){
                                downloadViewModel.queueDownloads(itemsToQueue, ignoreDuplicates)
                            }

                            if (result.message.isNotBlank()){
                                Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                            }

                            withContext(Dispatchers.Main){
                                handleDuplicatesAndDismiss(result.duplicateDownloadIDs)
                            }
                        }
                    })
                }else{
                    lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO){
                            downloadViewModel.queueDownloads(listOf(item), ignoreDuplicates)
                        }

                        if (result.message.isNotBlank()){
                            Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                        }

                        withContext(Dispatchers.Main){
                            handleDuplicatesAndDismiss(result.duplicateDownloadIDs)
                        }
                    }
                }

            }
        }
        download!!.setOnClickListener {
            lifecycleScope.launch {
                resultViewModel.cancelUpdateItemData()
                resultViewModel.cancelUpdateFormatsItemData()
                scheduleBtn.isEnabled = false
                download.isEnabled = false
                val item: DownloadItem = getDownloadItem()
                if (item.videoPreferences.alsoDownloadAsAudio){
                    val itemsToQueue = mutableListOf<DownloadItem>()
                    itemsToQueue.add(item)

                    getAlsoAudioDownloadItem(finished = {
                        itemsToQueue.add(it)

                        lifecycleScope.launch {
                            val result = withContext(Dispatchers.IO) {
                                downloadViewModel.queueDownloads(itemsToQueue, ignoreDuplicates)
                            }
                            withContext(Dispatchers.Main){
                                handleDuplicatesAndDismiss(result.duplicateDownloadIDs)
                            }
                        }
                    })
                }else{
                    val result = withContext(Dispatchers.IO) {
                        downloadViewModel.queueDownloads(listOf(item), ignoreDuplicates)
                    }
                    handleDuplicatesAndDismiss(result.duplicateDownloadIDs)
                }
            }
        }

        download.setOnLongClickListener {
            val dd = MaterialAlertDialogBuilder(requireContext())
            dd.setTitle(getString(R.string.save_for_later))
            dd.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
            dd.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                lifecycleScope.launch(Dispatchers.IO){
                    downloadViewModel.putToSaved(getDownloadItem())
                    dismiss()
                }
            }
            dd.show()
            true
        }

        val link = view.findViewById<Button>(R.id.bottom_sheet_link)
        link.visibility = if(shownFields.contains("url")){
            View.VISIBLE
        }else{
            View.GONE
        }

        if (Patterns.WEB_URL.matcher(result.url).matches()){
            link.text = result.url
            link.setOnClickListener{
                UiUtil.openLinkIntent(requireContext(), result.url)
            }
            link.setOnLongClickListener{
                UiUtil.copyLinkToClipBoard(requireContext(), result.url)
                true
            }

            //if auto-update after the card is open is off
            if (result.title.isEmpty() && currentDownloadItem == null && sharedPreferences.getBoolean("quick_download", false)) {
                (updateItem.parent as LinearLayout).visibility = View.VISIBLE
                updateItem.setOnClickListener {
                    (updateItem.parent as LinearLayout).visibility = View.GONE
                    initUpdateData()
                }
            }else{
                (updateItem.parent as LinearLayout).visibility = View.GONE
            }

        }else{
            link.visibility = View.GONE
            (updateItem.parent as LinearLayout).visibility = View.GONE
        }

        val incognitoBtn = view.findViewById<Button>(R.id.bottomsheet_incognito)
        incognitoBtn.alpha = if (incognito) 1f else 0.3f
        incognitoBtn.setOnClickListener {
            if (incognito) {
                it.alpha = 0.3f
            }else{
                it.alpha = 1f
            }

            incognito = !incognito
            fragmentAdapter.isIncognito = incognito
            LastUsedDownloadSettings.rememberIncognito(sharedPreferences, incognito)
            val onOff = if (incognito) getString(R.string.ok) else getString(R.string.disabled)
            Snackbar.make(incognitoBtn, "${getString(R.string.incognito)}: $onOff", Snackbar.LENGTH_SHORT).show()
        }


        //update in the background if there is no data
        if (!disableUpdateData) {
            if(result.title.isEmpty() && currentDownloadItem == null && !sharedPreferences.getBoolean("quick_download", false) && type != DownloadType.command){
                initUpdateData()
            }else {
                val usingGenericFormatsOrEmpty = result.formats.isEmpty() || result.formats.any { it.format_note.contains("ytdlnisgeneric") }
                if (usingGenericFormatsOrEmpty && sharedPreferences.getBoolean("update_formats", false) && !sharedPreferences.getBoolean("quick_download", false)){
                    initUpdateFormats(result)
                }
            }
        }

        lifecycleScope.launch {
            resultViewModel.uiState.collectLatest { res ->
                if (res.errorMessage != null){
                    //the dialog and the retry button are the same failure, said twice: it
                    //explains what broke, the card keeps offering the way out of it
                    setVideoFetch(VideoFetch.Failed)
                    kotlin.runCatching {
                        UiUtil.handleNoResults(requireActivity(), res.errorMessage!!,
                            url = result.url,
                            continueAnyway =  true,
                            //both buttons only dismiss the explanation: the fetch still failed,
                            //so the retry outlives the dialog and only a new one clears it
                            continued = {},
                            cookieFetch = {
                                val myIntent = Intent(requireContext(), WebViewActivity::class.java)
                                myIntent.putExtra("url", "https://${URL(result.url).host}")
                                cookiesFetchedResultLauncher.launch(myIntent)
                            },
                            //dismissing the explanation leaves the card, and its retry, standing
                            closed = {}
                        )
                    }

                    resultViewModel.uiState.update {it.copy(errorMessage  = null) }
                }
            }
        }

        /*
         * The header follows whatever the card is waiting for. Both sources are collected into
         * the same renderer so the subtitle, the shimmers and the action button can never
         * describe two different situations at once.
         */
        lifecycleScope.launch {
            resultViewModel.updatingData.collect { updating ->
                //a fetch already over by the time this collector exists still replays its end,
                //and one that ended without ever settling an outcome ended by failing
                when {
                    updating -> setVideoFetch(VideoFetch.Running)
                    videoFetch == VideoFetch.Running -> setVideoFetch(VideoFetch.Failed)
                    else -> renderStatus()
                }
            }
        }

        lifecycleScope.launch {
            musicViewModel.state.collect { renderStatus() }
        }

        lifecycleScope.launch {
            musicViewModel.enabled.collect { enabled ->
                musicBtn.alpha = if (enabled) 1f else 0.3f
                renderStatus()
            }
        }

        lifecycleScope.launch {
            resultViewModel.updatingFormats.collectLatest {
                kotlin.runCatching {
                    if (it){
                        delay(500)
                        runCatching {
                            (fragmentAdapter.fragments[0] as DownloadAudioFragment).apply {
                                view?.findViewById<LinearProgressIndicator>(R.id.format_loading_progress)?.apply {
                                    isVisible = true
                                    isClickable = true
                                    setOnClickListener {
                                        lifecycleScope.launch {
                                            resultViewModel.cancelUpdateFormatsItemData()
                                        }
                                    }
                                }
                            }
                        }
                        runCatching {
                            (fragmentAdapter.fragments[1] as DownloadVideoFragment).apply {
                                view?.findViewById<LinearProgressIndicator>(R.id.format_loading_progress)?.apply {
                                    isVisible = true
                                    isClickable = true
                                    setOnClickListener {
                                        lifecycleScope.launch {
                                            resultViewModel.cancelUpdateFormatsItemData()
                                        }
                                    }
                                }
                            }
                        }
                    }else{
                        runCatching {
                            (fragmentAdapter.fragments[0] as DownloadAudioFragment).apply {
                                view?.findViewById<LinearProgressIndicator>(R.id.format_loading_progress)?.apply {
                                    isVisible = false
                                    isClickable = false
                                }
                            }
                        }
                        runCatching {
                            (fragmentAdapter.fragments[1] as DownloadVideoFragment).apply {
                                view?.findViewById<LinearProgressIndicator>(R.id.format_loading_progress)?.apply {
                                    isVisible = false
                                    isClickable = false
                                }
                            }
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            resultViewModel.updateResultData.collectLatest { result ->
                if (result == null) return@collectLatest
                kotlin.runCatching {
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (result.size == 1 && result[0] != null) {
                            val res = result[0]!!
                            fragmentAdapter.setResultItem(res)

                            setVideoFetch(VideoFetch.Loaded)

                            val usingGenericFormatsOrEmpty = res.formats.isEmpty() || res.formats.any { it.format_note.contains("ytdlnisgeneric") }
                            downloadCardViewModel.setResultItem(res)
                            if (usingGenericFormatsOrEmpty && sharedPreferences.getBoolean("update_formats", false)){
                                initUpdateFormats(res)
                            }

                        }else if (result.size > 1) {
                            //open multi download card instead
                            if (activity is ShareActivity){
                                findNavController().navigate(R.id.action_downloadBottomSheetDialog_to_selectPlaylistItemsDialog, bundleOf(
                                    Pair("resultIDs", result.map { it!!.id }.toLongArray()),
                                ))
                            }else{
                                dismiss()
                            }
                        }else{
                            //a parse that answered with nothing usable answered with a failure
                            setVideoFetch(VideoFetch.Failed)
                        }

                        resultViewModel.updateResultData.emit(null)
                    }

                }
            }
        }

        lifecycleScope.launch {
            launch{
                downloadViewModel.alreadyExistsUiState.collectLatest { res ->
                    if (res.isNotEmpty() && activity is ShareActivity){
                        withContext(Dispatchers.Main){
                            val bundle = bundleOf(
                                Pair("duplicates", ArrayList(res))
                            )
                            delay(500)
                            findNavController().navigate(R.id.action_downloadBottomSheetDialog_to_downloadsAlreadyExistDialog2, bundle)
                        }
                        downloadViewModel.alreadyExistsUiState.value = mutableListOf()
                    }
                }
            }
        }

        lifecycleScope.launch {
            resultViewModel.updateFormatsResultData.collectLatest { formats ->
                if (formats == null) return@collectLatest
                kotlin.runCatching {
                    isAudioOnly = formats.isNotEmpty() && formats.none { !it.format_note.contains("audio") }
                    if (isAudioOnly){
                        (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(1)?.isClickable = true
                        (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(1)?.alpha = 0.3f
                        Toast.makeText(context, getString(R.string.audio_only_item), Toast.LENGTH_SHORT).show()
                        tabLayout.getTabAt(0)!!.select()
                        viewPager2.setCurrentItem(0, false)
                    }

                    lifecycleScope.launch {
                        withContext(Dispatchers.Main){
                            runCatching {
                                val f1 = fragmentAdapter.fragments[0] as DownloadAudioFragment
                                val resultItem = downloadViewModel.createResultItemFromDownload(f1.downloadItem)
                                resultItem.formats = formats
                                fragmentAdapter.setResultItem(resultItem)
                                f1.view?.findViewById<LinearProgressIndicator>(R.id.format_loading_progress)?.visibility = View.GONE
                            }
                            runCatching {
                                val f1 = fragmentAdapter.fragments[1] as DownloadVideoFragment
                                val resultItem = downloadViewModel.createResultItemFromDownload(f1.downloadItem)
                                resultItem.formats = formats
                                fragmentAdapter.setResultItem(resultItem)
                                f1.view?.findViewById<LinearProgressIndicator>(R.id.format_loading_progress)?.visibility = View.GONE
                            }
                        }

                        if (formats.isNotEmpty()){
                            result.formats = formats
                        }
                        resultViewModel.updateFormatsResultData.emit(null)
                    }
                }
            }
        }
    }

    private var cookiesFetchedResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            sharedPreferences.edit().putBoolean("use_cookies", true).apply()
            updateItem.isVisible = true
            initUpdateData()
        }
    }

    private fun getDownloadItem(selectedTabPosition: Int = tabLayout.selectedTabPosition) : DownloadItem {
        return fragmentAdapter.getDownloadItem(selectedTabPosition)
    }

    // ── Header status ──────────────────────────────────────────────────────────

    /**
     * Video info first: until it lands, nothing else about the item is knowable. The song only
     * gets to speak for the card once the video info is settled, and never for the tabs that
     * have no song to look up.
     */
    private fun currentStatus(): CardStatus = when (videoFetch) {
        VideoFetch.Running -> CardStatus.FetchingVideo
        VideoFetch.Failed -> CardStatus.VideoFailed
        else -> when {
            !musicViewModel.enabled.value -> CardStatus.Ready
            else -> when (musicViewModel.state.value) {
                is MusicViewModel.SearchState.Waiting,
                is MusicViewModel.SearchState.Loading -> CardStatus.SearchingSong
                is MusicViewModel.SearchState.Failed -> CardStatus.SongFailed
                else -> CardStatus.Ready
            }
        }
    }

    /**
     * Music mode tags an audio file, so it belongs to the audio tab and to nothing else. A list
     * of urls has no single song to look up either, so it does not get the button.
     */
    private fun showMusicButtonFor(tabPosition: Int) {
        runCatching {
            musicBtn.isVisible = tabPosition == AUDIO_TAB && !result.url.endsWith(".txt")
        }
    }

    /** Records the outcome and redraws, so no caller has to remember to do both. */
    private fun setVideoFetch(outcome: VideoFetch) {
        if (videoFetch == outcome) return
        videoFetch = outcome
        renderStatus()
    }

    /**
     * Draws the header for the current status: the title only shimmers while it is the unknown
     * one, the subtitle always says what is being waited for and sweeps while it still is, and
     * the action button offers a retry instead of a download while the card is built on nothing.
     */
    private fun renderStatus() {
        runCatching {
            val status = currentStatus()
            val fetchingVideo = status == CardStatus.FetchingVideo
            val busy = fetchingVideo || status == CardStatus.SearchingSong
            val failed = status == CardStatus.VideoFailed || status == CardStatus.SongFailed

            title.isVisible = !fetchingVideo
            shimmerLoading.isVisible = fetchingVideo
            if (fetchingVideo) shimmerLoading.startShimmer() else shimmerLoading.stopShimmer()

            subtitle.isVisible = !busy
            shimmerLoadingSubtitle.isVisible = busy
            if (busy) {
                loadingSubtitle.setText(statusText(status))
                shimmerLoadingSubtitle.startShimmer()
            } else {
                shimmerLoadingSubtitle.stopShimmer()
                subtitle.setText(statusText(status))
            }

            downloadBtn.isVisible = !failed
            refreshBtn.isVisible = failed
            if (fetchingVideo) (updateItem.parent as LinearLayout).visibility = View.GONE
        }
    }

    private fun statusText(status: CardStatus): Int = when (status) {
        CardStatus.FetchingVideo -> R.string.fetching_video_info
        CardStatus.VideoFailed -> R.string.video_info_failed
        CardStatus.SearchingSong -> R.string.searching_song
        CardStatus.SongFailed -> R.string.song_lookup_failed
        CardStatus.Ready ->
            if (musicViewModel.enabled.value &&
                musicViewModel.state.value is MusicViewModel.SearchState.NotFound
            ) R.string.song_not_found else R.string.configure_download
    }

    /** The retry the failure offered: whichever of the two lookups is the one that broke. */
    private fun retryFailedWork() {
        if (currentStatus() == CardStatus.VideoFailed) initUpdateData() else musicViewModel.retry()
    }

    private companion object {
        const val AUDIO_TAB = 0
    }

    private fun getAlsoAudioDownloadItem(finished: (it: DownloadItem) -> Unit) {
        try {
            val ff = fragmentAdapter.fragments[0] as DownloadAudioFragment
            getDownloadItem(1).videoPreferences.audioFormatIDs.apply {
                if (this.isNotEmpty()) {
                    ff.updateSelectedAudioFormat(this.first())
                }
            }
            finished(ff.downloadItem)
        }catch (e: Exception){
            val fragmentLifecycleCallback = object:
                FragmentManager.FragmentLifecycleCallbacks() {

                override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                    fragmentManager?.unregisterFragmentLifecycleCallbacks(this)
                    val ff = (f as DownloadAudioFragment)
                    ff.requireView().post {
                        ff.updateSelectedAudioFormat(getDownloadItem(1).videoPreferences.audioFormatIDs.first())
                        finished(ff.downloadItem)
                    }
                    super.onFragmentStarted(fm, f)
                }


            }

            fragmentManager?.registerFragmentLifecycleCallbacks(fragmentLifecycleCallback, true)
            viewPager2.setCurrentItem(0, true)
        }
    }

    private fun initUpdateData() {
        kotlin.runCatching {
            if (result.url.isBlank()) {
                dismiss()
                return
            }
            if (resultViewModel.updatingData.value) return

            setVideoFetch(VideoFetch.Running)
            lifecycleScope.launch(Dispatchers.IO) {
                resultViewModel.updateItemData(result)
            }
        }
    }

    private fun initUpdateFormats(res: ResultItem){
        kotlin.runCatching {
            if (resultViewModel.updatingFormats.value) return
            CoroutineScope(SupervisorJob()).launch(Dispatchers.IO) {
                resultViewModel.updateFormatItemData(res)
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        //the tab the card was left on, whether it was downloaded from or only configured
        runCatching { LastUsedDownloadSettings.remember(sharedPreferences, getDownloadItem()) }

        lifecycleScope.launch {
            resultViewModel.cancelUpdateItemData()
            resultViewModel.cancelUpdateFormatsItemData()
            super.onDismiss(dialog)
        }
    }

    private fun handleDuplicatesAndDismiss(res: List<DownloadViewModel.AlreadyExistsIDs>) {
        if (activity is ShareActivity && res.isNotEmpty()) {
            //let the lifecycle listener handle it
        }else{
            dismiss()
        }
    }
}

