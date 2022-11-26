package com.vishal.mybrowser.activity

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintJob
import android.print.PrintManager
import android.view.Gravity
import android.view.WindowManager
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textview.MaterialTextView
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.vishal.mybrowser.R
import com.vishal.mybrowser.activity.MainActivity.Companion.myPager
import com.vishal.mybrowser.activity.MainActivity.Companion.tabsBtn
import com.vishal.mybrowser.adapter.TabAdapter
import com.vishal.mybrowser.databinding.ActivityMainBinding
import com.vishal.mybrowser.databinding.AlertDialogBinding
import com.vishal.mybrowser.databinding.BookmarkLayoutBinding
import com.vishal.mybrowser.databinding.TabsViewBinding
import com.vishal.mybrowser.fragment.BrowseFragment
import com.vishal.mybrowser.fragment.HomeFragment
import com.vishal.mybrowser.model.Bookmark
import com.vishal.mybrowser.model.Tab
import java.io.ByteArrayOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
    private var printJob: PrintJob? = null

    companion object{
        var tabsList: ArrayList<Tab> = ArrayList()
        private var isFullScreen: Boolean = true
        var isDesktopSite: Boolean = false
        var bookmarkList: ArrayList<Bookmark> = ArrayList()
        var bookmarkIndex: Int = -1
        lateinit var myPager: ViewPager2
        lateinit var tabsBtn: MaterialTextView
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        getAllBookmarks()

        tabsList.add(Tab("Home",HomeFragment()))
        binding.myPager.adapter = TabsAdapter(supportFragmentManager,lifecycle)
        binding.myPager.isUserInputEnabled = false
        myPager = binding.myPager
        tabsBtn = binding.tabsBtn

        initializeView()
        changeFullScreen(true)

    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBackPressed() {

        var frag: BrowseFragment? = null
        try {
            frag = tabsList[binding.myPager.currentItem].fragment as BrowseFragment
        }catch(e:Exception)
        { }

        when{
            frag?.binding?.webView?.canGoBack() == true -> frag.binding.webView.goBack()
            binding.myPager.currentItem != 0 -> {
                tabsList.removeAt(binding.myPager.currentItem)
                binding.myPager.adapter?.notifyDataSetChanged()
                binding.myPager.currentItem = tabsList.size - 1
            }
            else -> super.onBackPressed()
        }
    }

    private inner class TabsAdapter(fa: FragmentManager, lc: Lifecycle) : FragmentStateAdapter(fa,lc) {
        override fun getItemCount(): Int = tabsList.size

        override fun createFragment(position: Int): Fragment = tabsList[position].fragment
    }

    private fun initializeView(){

        binding.tabsBtn.setOnClickListener {
            val viewTabs = layoutInflater.inflate(R.layout.tabs_view, binding.root, false)
            val bindingTabs = TabsViewBinding.bind(viewTabs)

            val dialogTabs = MaterialAlertDialogBuilder(this, R.style.roundCornerDialog).setView(viewTabs)
                .setTitle("Select Tab")
                .setPositiveButton("Home"){self, _ ->
                    changeTab("Home", HomeFragment())
                    self.dismiss()}
                .setNeutralButton("Google"){self, _ ->
                    changeTab("Google", BrowseFragment("www.google.com"))
                    self.dismiss()}
                .create()

            bindingTabs.tabsRv.setHasFixedSize(true)
            bindingTabs.tabsRv.layoutManager = LinearLayoutManager(this)
            bindingTabs.tabsRv.adapter = TabAdapter(this, dialogTabs)

            dialogTabs.show()

            val pBtn = dialogTabs.getButton(AlertDialog.BUTTON_POSITIVE)
            val nBtn = dialogTabs.getButton(AlertDialog.BUTTON_NEUTRAL)

            pBtn.isAllCaps = false
            nBtn.isAllCaps = false

            pBtn.setTextColor(Color.BLACK)
            nBtn.setTextColor(Color.BLACK)

            pBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(
                ResourcesCompat.getDrawable(resources, R.drawable.ic_home, theme)
                , null, null, null)
            nBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(
                ResourcesCompat.getDrawable(resources, R.drawable.ic_add, theme)
                , null, null, null)
        }

        binding.settingBtn.setOnClickListener{
            var frag: BrowseFragment? = null
            try {
                frag = tabsList[binding.myPager.currentItem].fragment as BrowseFragment
            }catch(e:Exception)
            { }

            val view = layoutInflater.inflate(R.layout.alert_dialog, binding.root, false)
            val dialogBinding = AlertDialogBinding.bind(view)

            val dialog = MaterialAlertDialogBuilder(this).setView(view).create()

            dialog.window?.apply {
                attributes.gravity = Gravity.BOTTOM
                attributes.y = 50
                setBackgroundDrawable(ColorDrawable(0xffffffff.toInt()))
            }
            dialog.show()

            if(isFullScreen){
                dialogBinding.fullScreenBtn.apply {
                    setIconResource(R.drawable.ic_fullscreen_exit)
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.cool_blue))
                }
            }

            if(isFullScreen){
                dialogBinding.fullScreenBtn.apply {
                    setIconResource(R.drawable.ic_fullscreen_exit)
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.cool_blue))
                }
            }

            frag?.let {
                bookmarkIndex = isBookmarked(it.binding.webView.url!!)
                if(bookmarkIndex != -1){
                dialogBinding.bookmarkBtn.apply {
                    setIconResource(R.drawable.ic_bookmark)
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.cool_blue))
                }
            } }


            dialogBinding.backBtn.setOnClickListener{
                onBackPressed()
            }

            dialogBinding.nextBtn.setOnClickListener{
                frag?.apply {
                    if(binding.webView.canGoForward())
                        binding.webView.goForward()
                }
            }

            dialogBinding.saveBtn.setOnClickListener {
                dialog.dismiss()
                if(frag != null)
                    saveAsPdf(web = frag.binding.webView)
                else Snackbar.make(binding.root, "WebPage Not Found \uD83E\uDD2D",3000).show()
            }

            dialogBinding.fullScreenBtn.setOnClickListener {
                it as MaterialButton
                isFullScreen = if(isFullScreen) {
                    changeFullScreen(false)
                    it.setIconResource(R.color.black)
                    it.setTextColor(ContextCompat.getColor(this, R.color.black))
                    false
                }
                else {
                    changeFullScreen(true)
                    it.setIconResource(R.drawable.ic_fullscreen_exit)
                    it.setTextColor(ContextCompat.getColor(this, R.color.cool_blue))
                    true
                }
            }

            dialogBinding.desktopBtn.setOnClickListener {
                it as MaterialButton

                frag?.binding?.webView?.apply {
                    isDesktopSite = if(isDesktopSite) {
                        settings.userAgentString = null
                        it.setIconResource(R.color.black)
                        it.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.black))
                        false
                    }
                    else {
                        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:107.0) Gecko/20100101 Firefox/107.0"
                        settings.useWideViewPort = true
                        evaluateJavascript("document.querySelector('meta[name=\"viewport\"]').setAttribute('content'," +
                                " 'width=1024px, initial-scale=' + (document.documentElement.clientWidth / 1024));",null)
                        it.setIconResource(R.drawable.ic_desktop)
                        it.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.cool_blue))
                        true
                    }
                    reload()
                    dialog.dismiss()
                }
            }

            dialogBinding.bookmarkBtn.setOnClickListener {
                frag?.let {
                    if(bookmarkIndex == -1){
                        val viewB = layoutInflater.inflate(R.layout.bookmark_layout, binding.root, false)
                        val bBinding = BookmarkLayoutBinding.bind(viewB)
                        val dialogB = MaterialAlertDialogBuilder(this)
                            .setTitle("Add Bookmark")
                            .setMessage("Url:${it.binding.webView.url}")
                            .setPositiveButton("Add"){self, _ ->
                                try {
                                    val array = ByteArrayOutputStream()
                                    it.fv?.compress(Bitmap.CompressFormat.PNG, 100, array)
                                    bookmarkList.add(
                                        Bookmark(name = bBinding.bookmarkTitle.text.toString(), url = it.binding.webView.url!!, array.toByteArray()))
                                }catch (e: Exception){
                                    bookmarkList.add(
                                        Bookmark(name = bBinding.bookmarkTitle.text.toString(), url = it.binding.webView.url!!))
                                }
                                self.dismiss()}
                            .setNegativeButton("Cancel"){self, _ -> self.dismiss()}
                            .setView(viewB).create()
                        dialogB.show()
                        bBinding.bookmarkTitle.setText(it.binding.webView.title)
                    } else {
                        val dialogB = MaterialAlertDialogBuilder(this)
                            .setTitle("Remove Bookmark")
                            .setMessage("Url:${it.binding.webView.url}")
                            .setPositiveButton("Remove"){self, _ ->
                                bookmarkList.removeAt(bookmarkIndex)
                                self.dismiss()}
                            .setNegativeButton("Cancel"){self, _ -> self.dismiss()}
                            .create()
                        dialogB.show()
                    }
                }
                dialog.dismiss()
            }

        }
    }

    override fun onResume() {
        super.onResume()
        printJob?.let {
            when{
                it.isCompleted -> Snackbar.make(binding.root, "Page Saved Successfully -> ${it.info.label}",3000).show()
                it.isFailed -> Snackbar.make(binding.root, "Failed -> ${it.info.label}",3000).show()
            }
        }
    }

    private fun saveAsPdf(web: WebView) {

        val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager

        val jobName = "${URL(web.url).host}_${SimpleDateFormat("HH:mm d_MMM_yy", Locale.ENGLISH)
            .format(Calendar.getInstance().time)}"

        val printAdapter = web.createPrintDocumentAdapter(jobName)
        val printAttributes = PrintAttributes.Builder()
        printJob = printManager.print(jobName, printAdapter, printAttributes.build())
    }

    private fun changeFullScreen(enable: Boolean){
        if(enable){
            WindowCompat.setDecorFitsSystemWindows(window,false)
            WindowInsetsControllerCompat(window, binding.root).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else{
            WindowCompat.setDecorFitsSystemWindows(window, true)
            WindowInsetsControllerCompat(window, binding.root).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun isBookmarked(url: String): Int{
        bookmarkList.forEachIndexed{index, bookmark ->
            if(bookmark.url == url) return index
        }
        return -1
    }

    fun saveBookmarks(){
        val editor = getSharedPreferences("BOOKMARKS", MODE_PRIVATE).edit()

        val data = GsonBuilder().create().toJson(bookmarkList)
        editor.putString("bookmarkList", data)

        editor.apply()
    }

    fun getAllBookmarks(){
        bookmarkList = ArrayList()
        val editor = getSharedPreferences("BOOKMARKS", MODE_PRIVATE)
        val data = editor.getString("bookmarkList", null)
        if(data != null)
        {
            val list: ArrayList<Bookmark> = GsonBuilder().create().fromJson(data, object: TypeToken<kotlin.collections.ArrayList<Bookmark>>(){}.type)
            bookmarkList.addAll(list)
        }
    }

}

@SuppressLint("NotifyDataSetChanged")
fun changeTab(url: String, fragment: Fragment, isBackground: Boolean = false){
    MainActivity.tabsList.add(Tab(name = url, fragment = fragment))
    myPager.adapter?.notifyDataSetChanged()
    tabsBtn.text = MainActivity.tabsList.size.toString()

    if(!isBackground) myPager.currentItem = MainActivity.tabsList.size - 1
}

fun checkForInternet(context: Context) : Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = connectivityManager.activeNetwork?: return false
        val activeNetwork =connectivityManager.getNetworkCapabilities(network)?: return false

        return when{
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    } else {
        @Suppress("DEPRECATION") val networkInfo =
            connectivityManager.activeNetworkInfo?: return false
        @Suppress("DEPRECATION")
        return networkInfo.isConnected
    }
}