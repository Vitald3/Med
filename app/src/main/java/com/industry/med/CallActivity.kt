package com.industry.med

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.*
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.view.Gravity
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import com.google.gson.Gson
import com.industry.med.databinding.MainBinding
import com.yandex.div.core.*
import com.yandex.div2.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream

private var coord = false
private var guid = ""
private var fileGuid = ""
var cont: CallActivity? = null

class CallActivity : AppCompatActivity(), LocationListener {
    private lateinit var binding: MainBinding
    private lateinit var serverJson: String
    private lateinit var divContext: Div2Context
    private lateinit var setting: SharedPreferences
    private var token = ""
    private var doctor = ""

    @RequiresApi(Build.VERSION_CODES.P)
    @SuppressLint("SetTextI18n")
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setting = getSharedPreferences("setting", Context.MODE_PRIVATE)
        val card = setting.getInt("card", 0)
        token = setting.getString("token", "").toString()
        doctor = setting.getString("doctor", "").toString()
        cont = this

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
            if (!coord && token != "") {
                val layout = binding.root.findViewById<LinearLayout>(R.id.main_layout)
                val text = TextView(this@CallActivity)
                text.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                text.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text.typeface = Typeface.create(null,500,false)
                text.setPadding(15, 15,15,0)
                text.setTextColor(Color.BLACK)
                text.text = "Разрешите приложению доступ к геолокации в настройках устройства, раздел Локация - Разрешения для приложений - Med - разрешить, и нажмите обновить"
                layout.addView(text)

                val button = Button(this@CallActivity)
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
                    ContextCompat.startActivity(this, Intent(this, CallingActivity::class.java), null)
                    finish()
                }

                bottomNavigation.visibility = BottomNavigationView.GONE
                progress.visibility = ProgressBar.GONE
                return
            }
        } else {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, gps, 0f, this)
        }

        divContext = Div2Context(
            baseContext = this@CallActivity,
            configuration = createDivConfiguration()
        )

        bottomNavigation.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.home_link -> {
                    val addIntent = Intent(this, MedActivity::class.java)
                    addIntent.putExtra("biometric", true)
                    if (coord) addIntent.putExtra("cord", true)
                    startActivity(addIntent)
                    finish()
                }
                R.id.calendar_link -> {
                    val addIntent = Intent(this, CalendarActivity::class.java)
                    if (coord) addIntent.putExtra("cord", true)
                    startActivity(addIntent)
                    finish()
                }
                R.id.calling_link -> {
                    val addIntent = Intent(this, CallingActivity::class.java)
                    if (coord) addIntent.putExtra("cord", true)
                    startActivity(addIntent)
                    finish()
                }
                R.id.profile_link -> {
                    view.setVariable("submit", "1")
                }
            }

            return@setOnItemSelectedListener true
        }

        guid = intent.getStringExtra("guid").toString()
        val pay = intent.getStringExtra("pay")

        GlobalScope.launch(Dispatchers.Main) {
            val client = OkHttpClient()

            val json = if (pay != null) {
                client.loadText("https://api.florazon.net/laravel/public/med?json=call&guid=$guid&token=$token&doctor=$doctor&pay=$pay")
            } else {
                client.loadText("https://api.florazon.net/laravel/public/med?json=call&guid=$guid&token=$token&doctor=$doctor")
            }

            progress.visibility = ProgressBar.GONE

            if (json != null) {
                serverJson = json

                progress.visibility = ProgressBar.GONE

                if (serverJson != "") {
                    val gson = Gson().fromJson(serverJson, Json::class.java)
                    val divJson = JSONObject(gson.json)
                    gps = gson.gps.toLong()
                    val templateJson = divJson.optJSONObject("templates")
                    val cardJson = divJson.getJSONObject("card")

                    val oldView = binding.root.findViewById<LinearLayout>(R.id.main_layout)
                    (oldView.parent as ViewGroup).removeView(oldView)

                    val div = LinearLayout(this@CallActivity)

                    div.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    div.orientation = LinearLayout.VERTICAL
                    div.id = R.id.main_layout
                    view = DivViewFactory(divContext, templateJson).createView(cardJson)
                    div.addView(view)
                    binding.root.findViewById<NestedScrollView>(R.id.scroll).addView(div)

                    if (pay != "0" && pay != null) {
                        view.setVariable("pay", "1")
                    }
                } else {
                    Toast.makeText(this@CallActivity, "Ошибка загрузки данных", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun queryName(uri: Uri): String {
        val returnCursor: Cursor = contentResolver.query(uri, null, null, null, null)!!
        val nameIndex: Int = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        returnCursor.moveToFirst()
        val name: String = returnCursor.getString(nameIndex)
        returnCursor.close()
        return name
    }

    @OptIn(DelicateCoroutinesApi::class)
    private val pickImageFromGalleryForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            val strGuid = fileGuid.replace("-", "")
            view.setVariable("plus$strGuid", "0")
            view.setVariable("loader$strGuid", "1")
            val intent = result.data

            val imageUri: Uri? = intent?.data
            val bitmap = convertUriToBitmap(imageUri!!)
            val fileName = queryName(imageUri)

            getStringImage(bitmap).let {
                GlobalScope.launch(Dispatchers.Main) {
                    val client = OkHttpClient()

                    val jsonObject = JSONObject()
                    jsonObject.put("token", token)
                    jsonObject.put("RequestGUID", guid)
                    jsonObject.put("FileTypeGUID", fileGuid)
                    jsonObject.put("FileName", fileName)
                    jsonObject.put("FileBase64", it)
                    jsonObject.put("FileTypeDescription", "")
                    val jsonObjectString = jsonObject.toString()
                    val requestBody = jsonObjectString.toRequestBody("application/json".toMediaTypeOrNull())

                    val json = client.loadText("https://api.florazon.net/laravel/public/upload", requestBody)

                    if (json != null && json == "Данные успешно сохранены") {
                        view.setVariable("loader$strGuid", "0")
                        view.setVariable("gal$strGuid", "1")
                    } else {
                        view.setVariable("loader$strGuid", "0")
                        view.setVariable("plus$strGuid", "1")
                    }
                }
            }
        }
    }

    private fun getStringImage(bmp: Bitmap): String {
        val image = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 100, image)
        val imageBytes: ByteArray = image.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.DEFAULT)
    }

    private fun convertUriToBitmap(uri: Uri) : Bitmap{
        return if (Build.VERSION.SDK_INT < 28) {
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        } else {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        }
    }

    fun pickImageFromGallery(uri: Uri) {
        val pickIntent = Intent(Intent.ACTION_PICK)

        if (uri.getQueryParameter("guid") != null) {
            fileGuid = uri.getQueryParameter("guid")!!
        }

        pickIntent.setDataAndType(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "image/*"
        )

        pickImageFromGalleryForResult.launch(pickIntent)
    }

    private fun createDivConfiguration(): DivConfiguration {
        return DivConfiguration.Builder(MedDivImageLoader(this))
            .actionHandler(UIDiv2ActionHandlerCall(this))
            .divDownloader(DemoDivDownloader(this, setting))
            .supportHyphenation(true)
            .typefaceProvider(YandexSansDivTypefaceProvider(this))
            .visualErrorsEnabled(true)
            .build()
    }

    fun alert(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onLocationChanged(location: Location) {
        val latitude = location.latitude
        val longitude = location.longitude

        GlobalScope.launch(Dispatchers.Main) {
            val client = OkHttpClient()

            client.loadText("https://api.florazon.net/laravel/public/coord?token=$token&latitude=$latitude&longitude=$longitude")
        }
    }

    override fun onProviderEnabled(provider: String) {
        if (!coord) {
            startActivity(Intent(this, CallActivity::class.java))
            finish()
        }
    }

    override fun onProviderDisabled(provider: String) {
        Toast.makeText(this, "Включите геолокацию на ващем устройстве", Toast.LENGTH_SHORT).show()

        if (coord) {
            startActivity(Intent(this, CallActivity::class.java))
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}

class UIDiv2ActionHandlerCall(private val callActivity: CallActivity) : DivActionHandler() {
    override fun handleAction(action: DivAction, view: DivViewFacade): Boolean {
        super.handleAction(action, view)
        if (action.url == null) return false
        val uri = action.url!!.evaluate(view.expressionResolver)
        return handleActivityActionUrl(uri)
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun handleActivityActionUrl(uri: Uri): Boolean {
        when (uri.getQueryParameter("gallery")) {
            "1" -> {
                callActivity.pickImageFromGallery(uri)
            }
        }

        when (uri.getQueryParameter("put")) {
            "1" -> {
                GlobalScope.launch(Dispatchers.Main) {
                    val client = OkHttpClient()
                    val params = uri.queryParameterNames
                    val jsonObject = JSONObject()

                    jsonObject.put("token", token)
                    jsonObject.put("RequestGUID", guid)

                    params.forEach {
                        jsonObject.put(it, uri.getQueryParameter(it))
                    }

                    val jsonObjectString = jsonObject.toString()

                    val requestBody = jsonObjectString.toRequestBody("application/json".toMediaTypeOrNull())

                    val json = client.loadText("https://api.florazon.net/laravel/public/save", requestBody)

                    if (json != null) {
                        callActivity.alert(json.toString())
                    } else {
                        callActivity.alert("Ошибка, попробуйте еще раз")
                    }
                }
            }
        }

        when (uri.getQueryParameter("activity")) {
            "calling" -> startActivityAction(CallingActivity::class.java, uri)
            "call" -> startActivityAction(CallActivity::class.java, uri)
            "med" -> startActivityAction(MedActivity::class.java, uri)
            else -> return false
        }
        return true
    }

    private fun startActivityAction(klass: Class<out Activity>, uri: Uri) {
        val addIntent = Intent(callActivity.applicationContext, klass)
        addIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        if (uri.getQueryParameter("status") != null) {
            addIntent.putExtra("status", uri.getQueryParameter("status")!!)
        }

        if (uri.getQueryParameter("guid") != null) {
            addIntent.putExtra("guid", uri.getQueryParameter("guid")!!)
        }

        if (uri.getQueryParameter("pay") != null) {
            addIntent.putExtra("pay", uri.getQueryParameter("pay")!!)
        }

        if (coord) {
            addIntent.getBooleanExtra("cord", true)
        }

        ContextCompat.startActivity(callActivity.applicationContext, addIntent, null)
    }
}