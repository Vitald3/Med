package com.industry.med

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import com.industry.med.databinding.MainBinding
import com.yandex.div.core.Div2Context
import com.yandex.div.core.DivConfiguration
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import androidx.core.content.ContextCompat.startActivity
import androidx.core.widget.NestedScrollView

class CallActivity : AppCompatActivity() {
    private lateinit var binding: MainBinding
    private lateinit var serverJson: String
    private lateinit var divContext: Div2Context
    private lateinit var setting: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setting = getSharedPreferences("setting", Context.MODE_PRIVATE)
        val card = setting.getInt("card", 0)

        supportActionBar?.hide()

        val bottomNavigation: BottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigation.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_UNLABELED
        val items: MenuItem = bottomNavigation.menu.getItem(card)
        items.isChecked = true

        bottomNavigation.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.home_link -> {
                    startActivity(this, Intent(this, MedActivity::class.java), null)
                }
                R.id.calendar_link -> {

                }
                R.id.calling_link -> {
                    startActivity(this, Intent(this, CallingActivity::class.java), null)
                }
                R.id.profile_link -> {

                }
            }

            return@setOnItemSelectedListener true
        }

        divContext = Div2Context(
            baseContext = this@CallActivity,
            configuration = createDivConfiguration()
        )

        val guid = intent.getStringExtra("guid").toString()

        val request = Request.Builder().url("https://api.florazon.net/laravel/public/med?json=call&guid=$guid").build()
        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        throw Exception("Запрос к серверу не был успешен: ${response.code} ${response.message}")
                    }

                    val json = response.body!!.string()

                    runOnUiThread {
                        serverJson = json

                        val progress = findViewById<ProgressBar>(R.id.progress)
                        progress.visibility = ProgressBar.GONE

                        if (serverJson != "") {
                            try {
                                val divJson = JSONObject(serverJson)
                                val templateJson = divJson.optJSONObject("templates")
                                val cardJson = divJson.getJSONObject("card")

                                val oldView = binding.root.findViewById<LinearLayout>(R.id.main_layout)
                                (oldView.parent as ViewGroup).removeView(oldView)

                                val div = LinearLayout(this@CallActivity)

                                div.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                                div.orientation = LinearLayout.VERTICAL
                                div.id = R.id.main_layout
                                div.addView(DivViewFactory(divContext, templateJson).createView(cardJson))
                                binding.root.findViewById<NestedScrollView>(R.id.scroll).addView(div)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(this@CallActivity, "Ошибка загрузки данных", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(this@CallActivity, "Ошибка загрузки данных", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        })
    }

    private fun createDivConfiguration(): DivConfiguration {
        return DivConfiguration.Builder(MedDivImageLoader(this))
            .actionHandler(UIDiv2ActionHandler(this))
            .supportHyphenation(true)
            .typefaceProvider(YandexSansDivTypefaceProvider(this))
            .visualErrorsEnabled(true)
            .build()
    }
}