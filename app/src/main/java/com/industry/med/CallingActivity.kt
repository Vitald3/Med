package com.industry.med

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import com.google.gson.Gson
import com.industry.med.databinding.CallingBinding
import com.yandex.div.core.Div2Context
import com.yandex.div.core.DivConfiguration
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.util.*

private var coord = false

class CallingActivity : AppCompatActivity(), LocationListener {
    private lateinit var binding: CallingBinding
    private var serverJson = ""
    private lateinit var status: String
    private var search = ""
    private var token = ""
    private var doctor = ""
    private lateinit var divContext: Div2Context
    private lateinit var setting: SharedPreferences
    private var page = 1
    var emptyData = false
    private var loader: ProgressBar? = null
    private var nestedSV: NestedScrollView? = null

    @RequiresApi(Build.VERSION_CODES.P)
    @OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("DiscouragedApi", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CallingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setting = getSharedPreferences("setting", Context.MODE_PRIVATE)
        val card = setting.getInt("card", 0)
        token = setting.getString("token", "").toString()
        doctor = setting.getString("doctor", "").toString()

        supportActionBar?.hide()

        status = intent.getStringExtra("status").toString()

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
                val text = TextView(this@CallingActivity)
                text.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                text.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text.typeface = Typeface.create(null,500,false)
                text.setPadding(15, 15,15,0)
                text.setTextColor(Color.BLACK)
                text.text = "Разрешите приложению доступ к геолокации в настройках устройства, раздел Локация - Разрешения для приложений - Med - разрешить, и нажмите обновить"
                layout.addView(text)

                val button = Button(this@CallingActivity)
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
                    addIntent.putExtra("status", status)
                    if (coord) addIntent.putExtra("cord", true)
                    startActivity(addIntent)
                    finish()
                }
                R.id.profile_link -> {

                }
            }

            return@setOnItemSelectedListener true
        }

        val inputSearch: EditText = findViewById(R.id.search)

        inputSearch.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun afterTextChanged(s: Editable) {
                search = s.toString()

                search = s.toString()
                loadApi(1)
            }
        })

        inputSearch.setOnEditorActionListener { _, _, event ->
            if (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER) {
                loadApi(1)
            }
            false
        }

        nestedSV = findViewById(R.id.scroll)

        nestedSV!!.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, _, scrollY, _, _ ->
            if (scrollY == v.getChildAt(0).measuredHeight - v.measuredHeight) {
                loader?.visibility = View.VISIBLE

                if (!emptyData) {
                    page++
                    loadApi(0)
                }
            }
        })

        divContext = Div2Context(
            baseContext = this@CallingActivity,
            configuration = createDivConfiguration()
        )

        GlobalScope.launch(Dispatchers.Main) {
            val client = OkHttpClient()

            val json = client.loadText("https://api.florazon.net/laravel/public/med?json=callings&calling_page=1&status=$status&token=$token&doctor=$doctor")
            progress.visibility = ProgressBar.GONE

            if (json != null) {
                serverJson = json.toString()

                if (serverJson != "") {
                    val gson = Gson().fromJson(serverJson, Json::class.java)
                    val divJson = JSONObject(gson.json)
                    gps = gson.gps.toLong()
                    val templateJson = divJson.optJSONObject("templates")
                    val cardJson = divJson.getJSONObject("card")

                    val oldView = binding.root.findViewById<LinearLayout>(R.id.main_layout)
                    (oldView.parent as ViewGroup).removeView(oldView)

                    val div = LinearLayout(this@CallingActivity)

                    div.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    div.orientation = LinearLayout.VERTICAL
                    div.id = R.id.main_layout

                    loader = ProgressBar(this@CallingActivity)
                    loader!!.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    loader!!.visibility = ProgressBar.GONE

                    val divKit = DivViewFactory(divContext, templateJson).createView(cardJson)
                    div.addView(divKit)
                    div.addView(loader)
                    binding.root.findViewById<NestedScrollView>(R.id.scroll).addView(div)
                } else {
                    Toast.makeText(this@CallingActivity, "Ошибка загрузки данных", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun loadApi(x: Int) {
        if (x == 0) loader!!.visibility = ProgressBar.VISIBLE

        GlobalScope.launch(Dispatchers.Main) {
            val client = OkHttpClient()

            val json = client.loadText("https://api.florazon.net/laravel/public/med?json=callings&calling_page=$page&status=$status&search=$search&token=$token&doctor=$doctor")

            serverJson = json.toString()

            if (serverJson != "") {
                val gson = Gson().fromJson(serverJson, Json::class.java)
                val divJson = JSONObject(gson.json)
                gps = gson.gps.toLong()
                val templateJson = divJson.optJSONObject("templates")
                val cardJson = divJson.getJSONObject("card")

                if (x == 1) {
                    val oldView = binding.root.findViewById<LinearLayout>(R.id.main_layout)
                    (oldView.parent as ViewGroup).removeView(oldView)

                    val div = LinearLayout(this@CallingActivity)

                    div.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    div.orientation = LinearLayout.VERTICAL
                    div.id = R.id.main_layout

                    loader!!.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    loader!!.visibility = ProgressBar.GONE

                    div.addView(DivViewFactory(divContext, templateJson).createView(cardJson))

                    if (loader!!.parent != null) {
                        (loader!!.parent as ViewGroup).removeView(loader)
                    }

                    div.addView(loader)

                    binding.root.findViewById<NestedScrollView>(R.id.scroll).addView(div)
                } else {
                    binding.root.findViewById<LinearLayout>(R.id.main_layout).addView(DivViewFactory(divContext, templateJson).createView(cardJson))
                }
            } else {
                Toast.makeText(this@CallingActivity, "Ошибка загрузки данных", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun createDivConfiguration(): DivConfiguration {
        return DivConfiguration.Builder(MedDivImageLoader(this))
            .actionHandler(UIDiv2ActionHandler(this))
            .supportHyphenation(true)
            .typefaceProvider(YandexSansDivTypefaceProvider(this))
            .visualErrorsEnabled(true)
            .build()
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
            startActivity(Intent(this, CallingActivity::class.java))
            finish()
        }
    }

    override fun onProviderDisabled(provider: String) {
        Toast.makeText(this, "Включите геолокацию на ващем устройстве", Toast.LENGTH_SHORT).show()

        if (coord) {
            startActivity(Intent(this, CallingActivity::class.java))
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}