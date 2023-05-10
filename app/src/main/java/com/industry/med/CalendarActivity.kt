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
import android.view.Gravity
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import com.yandex.div.core.Div2Context
import com.yandex.div.core.DivConfiguration
import okhttp3.*
import org.json.JSONObject
import com.industry.med.databinding.CalendarBinding
import com.yandex.div2.*
import kotlinx.coroutines.*
import java.util.*

private var coord = false

class CalendarActivity : AppCompatActivity(), LocationListener {
    private lateinit var binding: CalendarBinding
    private lateinit var serverJson: String
    private lateinit var divContext: Div2Context
    private lateinit var setting: SharedPreferences
    private var token = ""
    private var doctor = ""
    private var date = ""

    @RequiresApi(Build.VERSION_CODES.P)
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CalendarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setting = getSharedPreferences("setting", Context.MODE_PRIVATE)
        val card = setting.getInt("card", 0)
        token = setting.getString("token", "").toString()
        doctor = setting.getString("doctor", "").toString()
        coord = intent.getBooleanExtra("cord", false)

        supportActionBar?.hide()

        val progress = findViewById<ProgressBar>(R.id.progress)
        val bottomNavigation: BottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigation.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_UNLABELED
        val items: MenuItem = bottomNavigation.menu.getItem(card)
        items.isChecked = true

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (!coord && token != "") {
                val layout = binding.root.findViewById<LinearLayout>(R.id.main_layout)
                val text = TextView(this@CalendarActivity)
                text.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                text.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                text.typeface = Typeface.create(null,500,false)
                text.setPadding(15, 15,15,0)
                text.setTextColor(Color.BLACK)
                text.text = "Разрешите приложению доступ к геолокации в настройках устройства, раздел Локация - Разрешения для приложений - Med - разрешить, и нажмите обновить"
                layout.addView(text)

                val button = Button(this@CalendarActivity)
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
                    val addIntent = Intent(this, CalendarActivity::class.java)
                    startActivity(addIntent)
                    finish()
                }

                bottomNavigation.visibility = BottomNavigationView.GONE
                progress.visibility = ProgressBar.GONE
                return
            }
        } else {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, 0f, this)
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
                    val addIntent = Intent(this, CallingActivity::class.java)
                    addIntent.putExtra("biometric", true)
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

                }
            }

            return@setOnItemSelectedListener true
        }

        divContext = Div2Context(
            baseContext = this@CalendarActivity,
            configuration = createDivConfiguration()
        )

        val datePicker: DatePicker = findViewById(R.id.datePicker)
        val today = Calendar.getInstance()
        datePicker.minDate = today.timeInMillis

        datePicker.init(today[Calendar.YEAR], today[Calendar.MONTH], today[Calendar.DAY_OF_MONTH]) { _, year, monthOfYear, dayOfMonth ->
            val m = if ((monthOfYear + 1) <= 9) {
                "0"
            } else {
                ""
            }

            date = year.toString() + (m + (monthOfYear + 1)) + dayOfMonth.toString()
            load()
        }

        load()
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
    private fun load() {
        GlobalScope.launch(Dispatchers.Main) {
            val client = OkHttpClient()

            val json = client.loadText("https://api.florazon.net/laravel/public/med?json=calendar&token=$token&date=$date")

            if (json != null) {
                serverJson = json

                if (serverJson != "") {
                    val divJson = JSONObject(serverJson)
                    val templateJson = divJson.optJSONObject("templates")
                    val cardJson = divJson.getJSONObject("card")

                    val oldView = binding.root.findViewById<LinearLayout>(R.id.main_layout)
                    (oldView.parent as ViewGroup).removeView(oldView)

                    val div = LinearLayout(this@CalendarActivity)

                    div.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    div.orientation = LinearLayout.VERTICAL
                    div.id = R.id.main_layout
                    div.addView(DivViewFactory(divContext, templateJson).createView(cardJson))
                    binding.root.findViewById<LinearLayout>(R.id.box_layout).addView(div)
                } else {
                    Toast.makeText(this@CalendarActivity, "Ошибка загрузки данных", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onLocationChanged(location: Location) {
        val latitude = location.latitude
        val longitude = location.longitude

        GlobalScope.launch(Dispatchers.Main) {
            val client = OkHttpClient()

            client.loadText("https://api.florazon.net/laravel/public/med?token=$token&latitude=$latitude&longitude=$longitude")
        }
    }

    override fun onProviderEnabled(provider: String) {
        if (!coord) {
            startActivity(Intent(this, CalendarActivity::class.java))
            finish()
        }
    }

    override fun onProviderDisabled(provider: String) {
        Toast.makeText(this, "Включите геолокацию на ващем устройстве", Toast.LENGTH_SHORT).show()

        if (coord) {
            startActivity(Intent(this, CalendarActivity::class.java))
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}