package org.softeg.slartus.forpdaplus

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.os.Handler
import android.support.constraint.Group
import android.text.InputType
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.afollestad.materialdialogs.MaterialDialog
import com.nostra13.universalimageloader.core.ImageLoader
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener
import de.hdodenhof.circleimageview.CircleImageView
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.softeg.slartus.forpdacommon.setAllOnClickListener
import org.softeg.slartus.forpdaplus.classes.FastBlur
import org.softeg.slartus.forpdaplus.classes.common.StringUtils
import org.softeg.slartus.forpdaplus.common.AppLog
import org.softeg.slartus.forpdaplus.fragments.profile.ProfileFragment
import org.softeg.slartus.forpdaplus.listtemplates.QmsContactsBrickInfo
import org.softeg.slartus.forpdaplus.prefs.Preferences
import org.softeg.slartus.forpdaplus.repositories.UserInfo
import org.softeg.slartus.forpdaplus.repositories.UserInfoRepository
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.regex.Pattern

class ShortUserInfo internal constructor(activity: MainActivity, private val view: View) {
    companion object {
        private const val TAG = "ShortUserInfo"
    }

    var mActivity: WeakReference<MainActivity> = WeakReference(activity)
    var prefs: SharedPreferences = App.getInstance().preferences
    private val imgAvatar: CircleImageView
    private val imgAvatarSquare: ImageView
    private val userBackground: ImageView
    private val userNick: TextView
    private val qmsMessages: TextView
    private val loginButton: TextView
    private val userRep: TextView

    private val profileGroup: Group
    private val avatarsGroup: Group
    var mHandler = Handler()
    var client: Client = Client.getInstance()
    private val isSquare: Boolean
    private var avatarUrl = ""

    private fun getContext() = mActivity.get()


    private val isOnline: Boolean
        get() {
            val cm = App.getInstance().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val netInfo = cm.activeNetworkInfo
            return (netInfo != null && netInfo.isConnectedOrConnecting
                    && cm.activeNetworkInfo.isAvailable
                    && cm.activeNetworkInfo.isConnected)
        }


    init {
        userNick = findViewById(R.id.userNick) as TextView
        qmsMessages = findViewById(R.id.qmsMessages) as TextView
        loginButton = findViewById(R.id.loginButton) as TextView
        userRep = findViewById(R.id.userRep) as TextView
        imgAvatar = findViewById(R.id.imgAvatar) as CircleImageView
        imgAvatarSquare = findViewById(R.id.imgAvatarSquare) as ImageView
        val infoRefresh = findViewById(R.id.infoRefresh) as ImageView
        val openLink = findViewById(R.id.openLink) as ImageView
        userBackground = findViewById(R.id.userBackground) as ImageView

        isSquare = prefs.getBoolean("isSquareAvarars", false)
        avatarsGroup = findViewById(R.id.avatar_group) as Group
        profileGroup = findViewById(R.id.profile_info_group) as Group
        profileGroup.setAllOnClickListener {
            val brickInfo = QmsContactsBrickInfo()
            MainActivity.addTab(brickInfo.title, brickInfo.name, brickInfo.createFragment())
            //ListFragmentActivity.showListFragment(getContext(), QmsContactsBrickInfo.NAME, null);
        }
        openLink.setOnClickListener {
            var url: String?
            url = StringUtils.fromClipboard(getContext())
            if (url == null) url = ""

            MaterialDialog.Builder(getContext()!!)
                    .title(R.string.go_to_link)
                    .input(App.getInstance().getString(R.string.insert_link), if (isPdaLink(url)) url else null) { _, _ ->

                    }
                    .inputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
                    .positiveText(R.string.open)
                    .negativeText(R.string.cancel)
                    .onPositive { dialog, _ ->
                        assert(dialog.inputEditText != null)
                        if (!IntentActivity.tryShowUrl(getContext(),
                                        getContext()?.getHandler(),
                                        dialog.inputEditText!!.text.toString() + "", false, false)) {
                            Toast.makeText(getContext(), R.string.links_not_supported, Toast.LENGTH_SHORT).show()
                        }
                    }
                    .show()
        }

        val imgFile = File(prefs.getString("userInfoBg", "")!!)
        if (imgFile.exists()) {
            ImageLoader.getInstance().displayImage("file://" + imgFile.path, userBackground)
        }

        avatarsGroup.setAllOnClickListener { ProfileFragment.showProfile(UserInfoRepository.instance.getId(), client.user) }
        loginButton.setOnClickListener { LoginDialog.showDialog(getContext()) }

        if (!isOnline) {
            loginButton.setText(R.string.check_connection)
        }
        infoRefresh.setOnClickListener {
            if (isOnline and client.logined) {
                updateAsyncTask().execute()
            }
        }

        App.getInstance().addToDisposable(
                UserInfoRepository.instance
                        .userInfo
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { userInfo ->
                            avatarsGroup.visibility = if (userInfo.logined) View.VISIBLE else View.GONE
                            profileGroup.visibility = if (userInfo.logined) View.VISIBLE else View.GONE
                            loginButton.visibility = if (userInfo.logined) View.GONE else View.VISIBLE
                            if (userInfo.logined && !TextUtils.isEmpty(userInfo.id)) {
                                updateAsyncTask().execute()
                            }
                            refreshQms(userInfo)
                        })
    }

    private fun findViewById(id: Int): View {
        return view.findViewById(id)
    }

    private fun refreshQms() {
        UserInfoRepository.instance.userInfo.value?.let {
            refreshQms(it)
        }

    }

    private fun refreshQms(userInfo: UserInfo) {
        val qmsCount = userInfo.qmsCount
        if (qmsCount != 0) {
            qmsMessages.text = String.format(App.getInstance().getString(R.string.new_qms_messages), qmsCount)
        } else {
            qmsMessages.setText(R.string.no_new_qms_messages)
        }
    }

    private inner class updateAsyncTask : AsyncTask<String, Void, Boolean?>() {
        internal var reputation = ""

        override fun doInBackground(vararg urls: String): Boolean? {
            try {
                val doc = Jsoup.parse(client.performGet("http://4pda.ru/forum/index.php?showuser=" + UserInfoRepository.instance.getId()).responseBody)
                var el: Element? = doc.selectFirst("div.user-box > div.photo > img")
                if (el != null)
                    avatarUrl = el.attr("src")

                el = doc.selectFirst("div.statistic-box")
                if (el != null && el.children().size > 0) {
                    val repa = el.child(1).selectFirst("ul > li > div.area").text()
                    if (repa != null) {
                        reputation = repa
                    }
                }


            } catch (e: IOException) {
                AppLog.e(getContext(), e)
            }

            return null
        }

        override fun onPostExecute(result: Boolean?) {
            when {
                (avatarUrl == "") or (reputation == "") -> {
                    loginButton.setText(R.string.unknown_error)
                    qmsMessages.visibility = View.GONE
                }
                client.logined!! -> {
                    userNick.text = client.user
                    userRep.visibility = View.VISIBLE
                    userRep.text = String.format("%s: %s", App.getContext().getString(R.string.reputation), reputation)

                    refreshQms()
                    if (prefs.getBoolean("isUserBackground", false)) {
                        val imgFile = File(prefs.getString("userInfoBg", "")!!)
                        if (imgFile.exists()) {
                            ImageLoader.getInstance().displayImage("file:///" + imgFile.path, userBackground)
                        }
                    } else {
                        if ((avatarUrl != prefs.getString("userAvatarUrl", "")) or (prefs.getString("userInfoBg", "") == "")) {
                            ImageLoader.getInstance().loadImage(avatarUrl, object : SimpleImageLoadingListener() {
                                override fun onLoadingComplete(imageUri: String?, view: View?, loadedImage: Bitmap?) {
                                    userBackground.post {
                                        if (loadedImage == null) return@post
                                        if (loadedImage.width == 0 || loadedImage.height == 0)
                                            return@post
                                        blur(loadedImage, userBackground, avatarUrl)
                                        prefs.edit().putString("userAvatarUrl", avatarUrl).apply()
                                    }
                                }
                            })
                        } else {
                            val imgFile = File(prefs.getString("userInfoBg", "")!!)
                            if (imgFile.exists()) {
                                ImageLoader.getInstance().displayImage("file:///" + imgFile.path, userBackground)
                            }
                        }
                    }


                    ImageLoader.getInstance().displayImage(avatarUrl, if (isSquare) imgAvatarSquare else imgAvatar)
                    prefs.edit()
                            .putString("shortUserInfoRep", reputation)
                            .apply()
                    //prefs.edit().putBoolean("isLoadShortUserInfo", true).apply();
                    //prefs.edit().putString("shortAvatarUrl", avatarUrl).apply();
                }
                else -> {
                    userRep.visibility = View.GONE
                    loginButton.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun blur(bkg: Bitmap?, view: ImageView, url: String) {
        try {
            var bkg = bkg
            bkg = Bitmap.createScaledBitmap(bkg!!, view.width, view.height, false)

            val scaleFactor = 3f
            val radius = 64

            var overlay: Bitmap? = Bitmap.createBitmap((view.width / scaleFactor).toInt(),
                    (view.height / scaleFactor).toInt(), Bitmap.Config.RGB_565)
            val canvas = Canvas(overlay!!)
            canvas.translate(-view.left / scaleFactor, -view.top / scaleFactor)
            canvas.scale(1 / scaleFactor, 1 / scaleFactor)
            val paint = Paint()
            paint.flags = Paint.FILTER_BITMAP_FLAG
            canvas.drawBitmap(bkg!!, 0f, 0f, paint)

            overlay = FastBlur.doBlur(overlay, radius, true)
            view.setImageBitmap(overlay)
            storeImage(overlay, url)
        }catch (ex:Throwable){
            ex.printStackTrace()
        }

    }

    private fun storeImage(image: Bitmap?, url: String) {
        val pictureFile = getOutputMediaFile(url) ?: return
        try {
            val fos = FileOutputStream(pictureFile)
            image!!.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.close()
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "File not found: " + e.message)
        } catch (e: IOException) {
            Log.d(TAG, "Error accessing file: " + e.message)
        }

    }

    private fun getOutputMediaFile(url: String): File? {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.
        val mediaStorageDir = File(Preferences.System.getSystemDir())

        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null
            }
        }
        // Create a media file name
        val tsLong = System.currentTimeMillis() / 1000
        var name = tsLong.toString()
        val m = Pattern.compile("http://s.4pda.to/(.*?)\\.").matcher(url)
        if (m.find()) {
            name = m.group(1)
        }
        val file = mediaStorageDir.path + File.separator + name + ".png"
        prefs.edit().putString("userInfoBg", file).apply()
        return File(file)
    }

    private fun isPdaLink(url: String): Boolean {
        return Pattern.compile("4pda.ru/([^/$?&]+)", Pattern.CASE_INSENSITIVE).matcher(url).find()
    }


}