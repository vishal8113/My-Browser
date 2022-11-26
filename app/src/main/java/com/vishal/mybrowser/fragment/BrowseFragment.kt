package com.vishal.mybrowser.fragment

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.text.SpannableStringBuilder
import android.util.Base64
import android.view.*
import android.webkit.*
import androidx.core.app.ShareCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.snackbar.Snackbar
import com.vishal.mybrowser.R
import com.vishal.mybrowser.activity.MainActivity
import com.vishal.mybrowser.activity.changeTab
import com.vishal.mybrowser.databinding.FragmentBrowseBinding
import java.io.ByteArrayOutputStream

class BrowseFragment(private var urlNew: String) : Fragment() {

    lateinit var binding: FragmentBrowseBinding
    var fv: Bitmap? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_browse, container, false)
        binding = FragmentBrowseBinding.bind(view)

        registerForContextMenu(binding.webView)

        binding.webView.apply {
            when{
                URLUtil.isValidUrl(urlNew) -> loadUrl(urlNew)
                urlNew.contains(".com", ignoreCase = true) -> loadUrl(urlNew)
                else -> loadUrl("https://duckduckgo.com/?q=$urlNew")
            }
        }

        return view
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onResume() {
        MainActivity.tabsList[MainActivity.myPager.currentItem].name = binding.webView.url.toString()
        MainActivity.tabsBtn.text = MainActivity.tabsList.size.toString()

        binding.webView.setDownloadListener { url, _, _, _, _ -> startActivity(Intent(Intent.ACTION_VIEW).setData(
            Uri.parse(url))) }

        val mainActivityRef = requireActivity() as MainActivity

        mainActivityRef.binding.refreshBtn.visibility = View.VISIBLE
        mainActivityRef.binding.refreshBtn.setOnClickListener{
            binding.webView.reload()
        }

        super.onResume()
        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object: WebViewClient(){

                override fun onLoadResource(view: WebView?, url: String?) {
                    super.onLoadResource(view, url)
                    if(MainActivity.isDesktopSite){
                        view?.evaluateJavascript("document.querySelector('meta[name=\"viewport\"]').setAttribute('content'," +
                                " 'width=1024px, initial-scale=' + (document.documentElement.clientWidth / 1024));",null)
                    }
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    mainActivityRef.binding.topSearchBar.text = SpannableStringBuilder(url)
                    MainActivity.tabsList[MainActivity.myPager.currentItem].name = url.toString()
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    mainActivityRef.binding.progressBar.progress = 0
                    mainActivityRef.binding.progressBar.visibility = View.VISIBLE
                    if(url!!.contains("you",ignoreCase = false)) mainActivityRef.binding.root.transitionToEnd()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    mainActivityRef.binding.progressBar.visibility = View.GONE
                    binding.webView.zoomOut()
                }
            }
            webChromeClient = object: WebChromeClient(){
                override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                    super.onReceivedIcon(view, icon)
                    try{
                        mainActivityRef.binding.webIcon.setImageBitmap(icon)
                        fv = icon
                        MainActivity.bookmarkIndex = mainActivityRef.isBookmarked(view?.url!!)
                        if(MainActivity.bookmarkIndex != -1){
                            val array = ByteArrayOutputStream()
                            icon!!.compress(Bitmap.CompressFormat.PNG, 100, array)
                            MainActivity.bookmarkList[MainActivity.bookmarkIndex].image = array.toByteArray()
                        }
                    } catch(e: Exception) { }
                }
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {

                    super.onShowCustomView(view, callback)
                    binding.webView.visibility = View.GONE
                    binding.customView.visibility = View.VISIBLE
                    binding.customView.addView(view)
                    mainActivityRef.binding.root.transitionToEnd()
                }

                override fun onHideCustomView() {
                    super.onHideCustomView()
                    binding.webView.visibility = View.VISIBLE
                    binding.customView.visibility = View.GONE
                }

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    mainActivityRef.binding.progressBar.progress = newProgress
                }
            }


            binding.webView.setOnTouchListener { _, motionEvent ->
                mainActivityRef.binding.root.onTouchEvent(motionEvent)
                return@setOnTouchListener false
            }

            binding.webView.reload()
        }
    }

    override fun onPause() {
        super.onPause()
        (requireActivity() as MainActivity).saveBookmarks()
        binding.webView.apply {
            clearMatches()
            clearHistory()
            clearFormData()
            clearCache(true)
            clearSslPreferences()

            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)

        val result = binding.webView.hitTestResult
        when(result.type){
            WebView.HitTestResult.IMAGE_TYPE -> {
                menu.add("View Image")
                menu.add("Save Image")
                menu.add("Share")
                menu.add("Close")
            }
            WebView.HitTestResult.SRC_ANCHOR_TYPE, WebView.HitTestResult.ANCHOR_TYPE -> {
                menu.add("Open in New Tab")
                menu.add("Open Tab in background")
                menu.add("Share")
                menu.add("close")
            }
            WebView.HitTestResult.EDIT_TEXT_TYPE, WebView.HitTestResult.UNKNOWN_TYPE -> {}
            else -> {
                menu.add("Open in New Tab")
                menu.add("Open Tab in background")
                menu.add("Share")
                menu.add("Close")
            }
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {

        val message = Handler().obtainMessage()
        binding.webView.requestFocusNodeHref(message)
        val url = message.data.getString("url")
        val imgUrl = message.data.getString("src")

        when(item.title){
            "Open in New Tab" -> {
                changeTab(url.toString(), BrowseFragment(url.toString()))
            }
            "Open Tab in background" -> {
                changeTab(url.toString(), BrowseFragment(url.toString()), isBackground = true)
            }

            "View Image" -> {
                if(imgUrl != null) {
                    if (imgUrl.contains("base64")) {

                        val pureBytes = imgUrl.substring(imgUrl.indexOf(",") + 1)
                        val decodedBytes = Base64.decode(pureBytes, Base64.DEFAULT)
                        val finalImg =
                            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

                        val imgView = ShapeableImageView(requireContext())
                        imgView.setImageBitmap(finalImg)

                        val imgDialog = MaterialAlertDialogBuilder(requireContext()).setView(imgView).create()
                        imgDialog.show()

                        imgView.layoutParams.width = Resources.getSystem().displayMetrics.widthPixels
                        imgView.layoutParams.height = (Resources.getSystem().displayMetrics.heightPixels * .75).toInt()
                        imgView.requestLayout()

                        imgDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    }
                    else changeTab(imgUrl, BrowseFragment(imgUrl))
                }
            }

            "Save Image" -> {
                if(imgUrl != null) {
                    if (imgUrl.contains("base64")) {

                        val pureBytes = imgUrl.substring(imgUrl.indexOf(",") + 1)
                        val decodedBytes = Base64.decode(pureBytes, Base64.DEFAULT)
                        val finalImg =
                            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

                        MediaStore.Images.Media.insertImage(
                            requireActivity().contentResolver,
                            finalImg,
                            "Image",
                            null
                        )
                        Snackbar.make(binding.root, "IMage Saved Successfully \uD83D\uDE0D", 3000).show()
                    }
                    else startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(imgUrl)))
                }
            }
            "Share" -> {
                val tempUrl = url ?: imgUrl
                if(tempUrl != null){
                    if(tempUrl.contains("base64")){

                        val pureBytes = tempUrl.substring(tempUrl.indexOf(",") + 1)
                        val decodedBytes = Base64.decode(pureBytes, Base64.DEFAULT)
                        val finalImg = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

                        val path = MediaStore.Images.Media.insertImage(requireActivity().contentResolver, finalImg, "Image", null)

                        ShareCompat.IntentBuilder(requireContext()).setChooserTitle("Sharing Url! \uD83D\uDE43")
                            .setType("image/*")
                            .setStream(Uri.parse(path))
                            .startChooser()
                    }
                    else {
                        ShareCompat.IntentBuilder(requireContext()).setChooserTitle("Sharing Url! \uD83D\uDE43")
                            .setType("text/plain").setText(tempUrl)
                            .startChooser()
                    }

                }
                else Snackbar.make(binding.root, "Not a Valid url \uD83D\uDE1F", 3000).show()
            }
            "Close" -> {}
        }

        return super.onContextItemSelected(item)
    }

}