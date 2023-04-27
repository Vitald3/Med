package com.industry.med

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import com.industry.med.databinding.CallingBinding
import com.yandex.div.core.Div2Context
import com.yandex.div.core.DivConfiguration
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.*


class CallingActivity : AppCompatActivity() {
    private lateinit var binding: CallingBinding
    private var serverJson = ""
    private lateinit var status: String
    private var search = ""
    private lateinit var divContext: Div2Context
    private lateinit var setting: SharedPreferences
    private var page = 1
    var emptyData = false
    private var loader: ProgressBar? = null
    private var nestedSV: NestedScrollView? = null

    @SuppressLint("DiscouragedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CallingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setting = getSharedPreferences("setting", Context.MODE_PRIVATE)
        val card = setting.getInt("card", 0)

        supportActionBar?.hide()

        status = intent.getStringExtra("status").toString()

        val bottomNavigation: BottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigation.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_UNLABELED
        val items: MenuItem = bottomNavigation.menu.getItem(card)
        items.isChecked = true

        bottomNavigation.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.home_link -> {
                    ContextCompat.startActivity(this, Intent(this, MedActivity::class.java), null)
                }
                R.id.calendar_link -> {

                }
                R.id.calling_link -> {
                    val addIntent = Intent(this, CallingActivity::class.java)
                    addIntent.putExtra("status", status)
                    ContextCompat.startActivity(this, addIntent, null)
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

        val request = Request.Builder().url("https://api.florazon.net/laravel/public/med?json=callings&calling_page=1&status=$status").build()
        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val json = response.body!!.string()

                    runOnUiThread {
                        val progress = findViewById<ProgressBar>(R.id.progress)
                        progress.visibility = ProgressBar.GONE

                        if (!response.isSuccessful) {
                            Toast.makeText(this@CallingActivity, "Ошибка загрузки данных - ${response.code} ${response.message}", Toast.LENGTH_LONG).show()
                        } else {
                            serverJson = json
                            val divJson = JSONObject(serverJson)
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
                        }
                    }
                }
            }
        })
    }

    private fun loadApi(x: Int) {
        if (x == 0) loader!!.visibility = ProgressBar.VISIBLE

        val request = Request.Builder().url("https://api.florazon.net/laravel/public/med?json=callings&calling_page=$page&status=$status&search=$search").build()
        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        Toast.makeText(this@CallingActivity, "Ошибка загрузки данных", Toast.LENGTH_LONG).show()
                    }

                    serverJson = response.body!!.string()

                    runOnUiThread {
                        if (serverJson != "") {
                            try {
                                val divJson = JSONObject(serverJson)
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
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(this@CallingActivity, "Ошибка загрузки данных", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            emptyData = true
                        }

                        if (x == 0) loader!!.visibility = ProgressBar.GONE
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