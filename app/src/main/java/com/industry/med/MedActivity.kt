package com.industry.med

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import com.industry.med.databinding.MainBinding
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import com.yandex.div.DivDataTag
import com.yandex.div.core.*
import com.yandex.div.core.downloader.DivDownloader
import com.yandex.div.core.downloader.DivPatchDownloadCallback
import com.yandex.div.core.font.DivTypefaceProvider
import com.yandex.div.core.images.*
import com.yandex.div.core.view2.Div2View
import com.yandex.div.data.DivParsingEnvironment
import com.yandex.div.json.ParsingErrorLogger
import com.yandex.div2.DivAction
import com.yandex.div2.DivData
import com.yandex.div2.DivPatch
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject

lateinit var view: Div2View
private var coord = false
lateinit var token: String
lateinit var tokenF: String
var gps: Long = 120000

data class Json(
    val gps: Int,
    val json: String,
    val price: String = "0"
)

class MedActivity : AppCompatActivity(), BiometricAuthListener, LocationListener {
    private lateinit var binding: MainBinding
    private lateinit var serverJson: String
    private lateinit var divContext: Div2Context
    private lateinit var setting: SharedPreferences
    private lateinit var doctor: String
    private var biometric = false

    @OptIn(DelicateCoroutinesApi::class)
    @RequiresApi(Build.VERSION_CODES.P)
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setting = getSharedPreferences("setting", Context.MODE_PRIVATE)
        val card = setting.getInt("card", 0)
        token = setting.getString("token", "").toString()
        tokenF = setting.getString("tokenF", "").toString()
        doctor = setting.getString("doctor", "").toString()
        biometric = intent.getBooleanExtra("biometric", false)

        supportActionBar?.hide()

        val progress = findViewById<ProgressBar>(R.id.progress)
        val bottomNavigation: BottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigation.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_UNLABELED
        val items: MenuItem = bottomNavigation.menu.getItem(card)
        items.isChecked = true

        val lm = getSystemService(LOCATION_SERVICE) as LocationManager

        coord = if (intent.hasExtra("cord")) {
            intent.getBooleanExtra("cord", false)
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            if (!coord && biometric && token != "") {
                val layout = binding.root.findViewById<LinearLayout>(R.id.main_layout)
                val text = TextView(this@MedActivity)
                text.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                text.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text.typeface = Typeface.create(null,500,false)
                text.setPadding(15, 15,15,0)
                text.setTextColor(Color.BLACK)
                text.text = "Разрешите приложению доступ к геолокации в настройках устройства, раздел Локация - Разрешения для приложений - Med - разрешить, и нажмите обновить"
                layout.addView(text)

                val button = Button(this@MedActivity)
                val params = LinearLayout.LayoutParams(360, LinearLayout.LayoutParams.WRAP_CONTENT)
                params.setMargins(0, 30, 0, 0)
                params.gravity = Gravity.CENTER
                button.layoutParams = params
                button.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                button.typeface = Typeface.create(null,500,false)
                button.text = "Обновить"
                button.setPadding(30, 0,30,0)
                button.setTextColor(Color.WHITE)
                button.background = ContextCompat.getDrawable(this, R.drawable.bac)
                layout.addView(button)

                button.setOnClickListener {
                    if (biometric) {
                        val addIntent = Intent(this, MedActivity::class.java)
                        addIntent.putExtra("biometric", true)
                        startActivity(addIntent)
                        finish()
                    } else {
                        startActivity(this, Intent(this, MedActivity::class.java), null)
                        finish()
                    }
                }

                bottomNavigation.visibility = BottomNavigationView.GONE
                progress.visibility = ProgressBar.GONE
                return
            }
        } else {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, gps, 0f, this)
        }

        bottomNavigation.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.home_link -> {
                    if (biometric) {
                        val addIntent = Intent(this, MedActivity::class.java)
                        addIntent.putExtra("biometric", true)
                        if (coord) addIntent.putExtra("cord", true)
                        startActivity(addIntent)
                        finish()
                    } else {
                        startActivity(this, Intent(this, MedActivity::class.java), null)
                        finish()
                    }
                }
                R.id.calendar_link -> {
                    if (coord) {
                        startActivity(this, Intent(this, CalendarActivity::class.java), null)
                        finish()
                    }
                }
                R.id.calling_link -> {
                    if (biometric && coord) {
                        startActivity(this, Intent(this, CallingActivity::class.java), null)
                        finish()
                    }
                }
                R.id.profile_link -> {

                }
            }

            return@setOnItemSelectedListener true
        }

        divContext = Div2Context(
            baseContext = this@MedActivity,
            configuration = createDivConfiguration()
        )

        if (token != "") {
            if (!biometric) {
                if (BiometricUtils.isBiometricReady(this)) {
                    BiometricUtils.showBiometricPrompt(
                        activity = this,
                        listener = this,
                        cryptoObject = null,
                    )
                } else {
                    Toast.makeText(this, "На этом устройстве не поддерживается биометрическая функция", Toast.LENGTH_SHORT).show()
                }
            } else {
                if (coord) {
                    loadAfter("https://api.florazon.net/laravel/public/med?json=home&token=$token&doctor=$doctor")

                    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            if (task.result != null && !TextUtils.isEmpty(task.result)) {
                                tokenF = task.result!!

                                GlobalScope.launch(Dispatchers.Main) {
                                    val client = OkHttpClient()

                                    val jsonObject = JSONObject()

                                    jsonObject.put("Token", token)
                                    jsonObject.put("TokenFairBase", tokenF)

                                    val jsonObjectString = jsonObject.toString()

                                    val requestBody = jsonObjectString.toRequestBody("application/json".toMediaTypeOrNull())
                                    client.loadText("https://api.florazon.net/laravel/public/firebase", requestBody)
                                }

                                val editor: SharedPreferences.Editor = setting.edit()
                                editor.putString("tokenF", tokenF)
                                editor.apply()
                            }
                        }
                    }
                } else {
                    val addIntent = Intent(this, MedActivity::class.java)
                    addIntent.putExtra("biometric", true)
                    addIntent.putExtra("cord", false)
                    startActivity(this, addIntent, null)
                    finish()
                }
            }
        } else {
            biometric = true
            loadAfter("https://api.florazon.net/laravel/public/med?json=auth")
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun loadAfter(url: String) {
        val progress = findViewById<ProgressBar>(R.id.progress)

        if (biometric) {
            progress.visibility = ProgressBar.VISIBLE

            GlobalScope.launch(Dispatchers.Main) {
                val client = OkHttpClient()

                val json = client.loadText(url)

                progress.visibility = ProgressBar.GONE
                serverJson = json.toString()

                if (serverJson != "") {
                    val gson = Gson().fromJson(serverJson, Json::class.java)
                    val divJson = JSONObject(gson.json)
                    gps = gson.gps.toLong()
                    val templateJson = divJson.optJSONObject("templates")
                    val cardJson = divJson.getJSONObject("card")

                    val oldView = binding.root.findViewById<LinearLayout>(R.id.main_layout)
                    (oldView.parent as ViewGroup).removeView(oldView)

                    val div = LinearLayout(this@MedActivity)

                    div.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    div.orientation = LinearLayout.VERTICAL
                    div.id = R.id.main_layout
                    view = DivViewFactory(divContext, templateJson).createView(cardJson)
                    div.addView(view)

                    binding.root.findViewById<NestedScrollView>(R.id.scroll).addView(div)

                    val editor: SharedPreferences.Editor = setting.edit()
                    editor.putInt("card", 0)
                    editor.apply()
                } else {
                    Toast.makeText(this@MedActivity, "Ошибка загрузки данных", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            progress.visibility = ProgressBar.GONE
        }
    }

    override fun onBiometricAuthenticateError(error: Int, errMsg: String) {
        when (error) {
            BiometricPrompt.ERROR_USER_CANCELED -> finish()
            BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                startActivity(Intent(this, MedActivity::class.java))
                finish()
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onBiometricAuthenticateSuccess(result: BiometricPrompt.AuthenticationResult) {
        biometric = true

        if (coord) {
            loadAfter("https://api.florazon.net/laravel/public/med?json=home&token=$token&doctor=$doctor")

            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    if (task.result != null && !TextUtils.isEmpty(task.result)) {
                        tokenF = task.result!!
                        println(tokenF)

                        GlobalScope.launch(Dispatchers.Main) {
                            val client = OkHttpClient()

                            val jsonObject = JSONObject()

                            jsonObject.put("Token", token)
                            jsonObject.put("TokenFairBase", tokenF)

                            val jsonObjectString = jsonObject.toString()

                            val requestBody = jsonObjectString.toRequestBody("application/json".toMediaTypeOrNull())
                            client.loadText("https://api.florazon.net/laravel/public/firebase", requestBody)
                        }

                        val editor: SharedPreferences.Editor = setting.edit()
                        editor.putString("tokenF", tokenF)
                        editor.apply()
                    }
                }
            }
        } else {
            val addIntent = Intent(this, MedActivity::class.java)
            addIntent.putExtra("biometric", true)
            addIntent.putExtra("cord", false)
            startActivity(this, addIntent, null)
            finish()
        }
    }

    private fun createDivConfiguration(): DivConfiguration {
        return DivConfiguration.Builder(MedDivImageLoader(this))
            .actionHandler(UIDiv2ActionHandler(this))
            .supportHyphenation(true)
            .divDownloader(DemoDivDownloader(this, setting))
            .typefaceProvider(YandexSansDivTypefaceProvider(this))
            .visualErrorsEnabled(true)
            .build()
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onLocationChanged(location: Location) {
        val latitude = location.latitude
        val longitude = location.longitude

        coord = true

        GlobalScope.launch(Dispatchers.Main) {
            val client = OkHttpClient()
            client.loadText("https://api.florazon.net/laravel/public/coord?token=$token&latitude=$latitude&longitude=$longitude")
        }
    }

    override fun onProviderEnabled(provider: String) {
        if (!coord) {
            val addIntent = Intent(this, MedActivity::class.java)
            if (token != "" && biometric) addIntent.putExtra("biometric", true)
            addIntent.putExtra("cord", true)
            startActivity(addIntent)
            finish()
        }
    }

    override fun onProviderDisabled(provider: String) {
        if (token != "" && biometric) Toast.makeText(this, "Включите геолокацию на ващем устройстве", Toast.LENGTH_SHORT).show()

        if (coord) {
            val addIntent = Intent(this, MedActivity::class.java)
            if (token != "" && biometric) addIntent.putExtra("biometric", true)
            addIntent.putExtra("cord", false)
            startActivity(addIntent)
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}

@Suppress("BlockingMethodInNonBlockingContext")
suspend fun OkHttpClient.loadText(uri: String, requestBody: RequestBody? = null): String? = withContext(Dispatchers.IO) {
    val request = if (requestBody != null) {
        Request.Builder().method("POST", requestBody).url(uri).build()
    } else {
        Request.Builder().url(uri).build()
    }

    return@withContext newCall(request).execute().body?.string()
}

class DemoDivDownloader(private val cont: Context, private val setting: SharedPreferences) : DivDownloader {
    private fun JSONObject.asDivPatchWithTemplates(errorLogger: ParsingErrorLogger? = null): DivPatch {
        val templates = optJSONObject("templates")
        val card = getJSONObject("patch")
        val environment = createEnvironment(errorLogger, templates)
        return DivPatch(environment, card)
    }

    private fun createEnvironment(
        errorLogger: ParsingErrorLogger?,
        templates: JSONObject?,
        componentName: String? = null
    ): DivParsingEnvironment {
        val environment = DivParsingEnvironment(errorLogger ?: ParsingErrorLogger.LOG)

        templates?.let {
            environment.parseTemplatesWithHistograms(templates, componentName)
        }

        return DivParsingEnvironment(errorLogger ?: ParsingErrorLogger.LOG)
    }

    private val parsingHistogramReporter = DivKit.getInstance(cont).parsingHistogramReporter

    private fun DivParsingEnvironment.parseTemplatesWithHistograms(templates: JSONObject, componentName: String? = null) {
        parsingHistogramReporter.measureTemplatesParsing(templates, componentName) {
            parseTemplates(templates)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun downloadPatch(divView: Div2View, downloadUrl: String, callback: DivPatchDownloadCallback): LoadReference {
        val job = GlobalScope.launch(Dispatchers.Main) {
            val client = OkHttpClient()

            val url = "$downloadUrl&token=$token"

            var json = client.loadText(url)
            println(url)

            if (json != null) {
                try {
                    if (downloadUrl.contains("analiz")) {
                        val gson = Gson().fromJson(json, Json::class.java)
                        json = gson.json
                        view.setVariable("price1", gson.price)
                    }

                    val reload = JSONObject(json).optString("reload")
                    val error = JSONObject(json).optString("error")
                    val token = JSONObject(json).optString("Token")
                    val doctor = JSONObject(json).optString("Doctor")
                    val authCode = JSONObject(json).optString("ValidationCode")
                    val alert = JSONObject(json).optString("alert")

                    if (authCode != "") {
                        view.setVariable("send_sms", "1")
                    }

                    val editor: SharedPreferences.Editor = setting.edit()

                    if (alert != "") {
                        Toast.makeText(cont, alert, Toast.LENGTH_LONG).show()
                    }

                    if (error != "") {
                        Toast.makeText(
                            cont,
                            error,
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    if (token != "") {
                        editor.putString("token", token.toString())
                        editor.apply()
                    }

                    if (doctor != "" && token != "") {
                        editor.putString("doctor", doctor.toString())
                        editor.apply()

                        if (reload == "0") {
                            val addIntent = Intent(cont, MedActivity::class.java)
                            addIntent.putExtra("biometric", true)
                            startActivity(cont, addIntent, null)
                        }
                    }

                    if (reload != "0") callback.onSuccess(JSONObject(json).asDivPatchWithTemplates())
                } catch (e: JSONException) {
                    callback.onFail()
                }
            } else {
                callback.onFail()
            }
        }
        return LoadReference {
            job.cancel("cancel all downloads")
        }
    }
}

class YandexSansDivTypefaceProvider @Inject constructor(private val context: Context) : DivTypefaceProvider {

    override fun getRegular(): Typeface {
        return ResourcesCompat.getFont(context, R.font.inter_regular) ?: Typeface.DEFAULT
    }

    override fun getMedium(): Typeface {
        return ResourcesCompat.getFont(context, R.font.inter_semibold) ?: Typeface.DEFAULT
    }

    override fun getLight(): Typeface {
        return ResourcesCompat.getFont(context, R.font.inter_regular) ?: Typeface.DEFAULT
    }

    override fun getBold(): Typeface {
        return ResourcesCompat.getFont(context, R.font.inter_bold) ?: Typeface.DEFAULT
    }

    @Deprecated("Deprecated in Java")
    override fun getRegularLegacy(): Typeface {
        /**
         *  |ya_regular| includes both regular and italic variant.
         *  It is used in rich text in legacy divs.
         */
        return ResourcesCompat.getFont(context, R.font.inter_regular) ?: Typeface.DEFAULT
    }
}

internal class DivViewFactory(private val context: Div2Context, private val templatesJson: JSONObject? = null) {
    private val environment = DivParsingEnvironment(ParsingErrorLogger.ASSERT).apply {
        if (templatesJson != null) parseTemplates(templatesJson)
    }

    fun createView(cardJson: JSONObject): Div2View {
        val divData = DivData(environment, cardJson)
        return Div2View(context).apply {
            setData(divData, DivDataTag(divData.logId))
        }
    }
}

class UIDiv2ActionHandler(private val context: Context) : DivActionHandler() {
    override fun handleAction(action: DivAction, view: DivViewFacade): Boolean {
        super.handleAction(action, view)
        if (action.url == null) return false
        val uri = action.url!!.evaluate(view.expressionResolver)
        return handleActivityActionUrl(uri)
    }

    private fun handleActivityActionUrl(uri: Uri): Boolean {
        when (uri.getQueryParameter("activity")) {
            "calling" -> startActivityAction(CallingActivity::class.java, uri)
            "call" -> startActivityAction(CallActivity::class.java, uri)
            else -> return false
        }
        return true
    }

    private fun startActivityAction(klass: Class<out Activity>, uri: Uri) {
        val addIntent = Intent(context, klass)

        if (uri.getQueryParameter("status") != null) {
            addIntent.putExtra("status", uri.getQueryParameter("status")!!)
        }

        if (uri.getQueryParameter("guid") != null) {
            addIntent.putExtra("guid", uri.getQueryParameter("guid")!!)
        }

        if (coord) {
            addIntent.getBooleanExtra("cord", true)
        }

        startActivity(context, addIntent, null)
    }
}

class MedDivImageLoader(context: Context) : DivImageLoader {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val picasso by lazy { createPicasso() }

    private fun createPicasso(): Picasso {
        return Picasso.Builder(appContext)
            .downloader(OkHttp3Downloader(appContext, DISK_CACHE_SIZE))
            .build()
    }

    override fun loadImage(imageUrl: String, callback: DivImageDownloadCallback): LoadReference {
        val imageUri = Uri.parse(imageUrl)
        val target = DownloadCallbackAdapter(imageUri, callback)
        mainHandler.post { picasso.load(imageUri).into(target) }

        return LoadReference { picasso.cancelRequest(target) }
    }

    override fun loadImage(imageUrl: String, imageView: ImageView): LoadReference {
        val target = ImageViewAdapter(imageView)
        picasso.load(Uri.parse(imageUrl)).into(target)
        return LoadReference { picasso.cancelRequest(target) }
    }

    override fun loadImageBytes(imageUrl: String, callback: DivImageDownloadCallback): LoadReference {
        return loadImage(imageUrl, callback)
    }

    private companion object {
        const val DISK_CACHE_SIZE = 16_777_216L
    }

    private inner class DownloadCallbackAdapter(
        private val imageUri: Uri,
        private val callback: DivImageDownloadCallback
    ) : Target {

        override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom) {
            callback.onSuccess(CachedBitmap(bitmap, imageUri, from.toBitmapSource()))
        }

        override fun onBitmapFailed(e: Exception, errorDrawable: Drawable?) {
            callback.onError()
        }

        override fun onPrepareLoad(placeHolderDrawable: Drawable?) = Unit
    }

    private inner class ImageViewAdapter(
        private val imageView: ImageView
    ) : Target {

        override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom) {
            imageView.setImageBitmap(bitmap)
        }

        override fun onBitmapFailed(e: Exception, errorDrawable: Drawable?) = Unit

        override fun onPrepareLoad(placeHolderDrawable: Drawable?) = Unit
    }

}

private fun Picasso.LoadedFrom.toBitmapSource(): BitmapSource {
    return when (this) {
        Picasso.LoadedFrom.MEMORY -> BitmapSource.MEMORY
        Picasso.LoadedFrom.DISK -> BitmapSource.DISK
        Picasso.LoadedFrom.NETWORK -> BitmapSource.NETWORK
    }
}