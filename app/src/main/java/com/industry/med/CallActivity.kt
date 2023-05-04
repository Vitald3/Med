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
import androidx.core.content.ContextCompat.startActivity
import androidx.core.widget.NestedScrollView
import com.yandex.div.core.DivKit
import com.yandex.div.core.downloader.DivDownloader
import com.yandex.div.core.downloader.DivPatchDownloadCallback
import com.yandex.div.core.images.LoadReference
import com.yandex.div.core.view2.Div2View
import com.yandex.div.data.DivParsingEnvironment
import com.yandex.div.json.ParsingErrorLogger
import com.yandex.div2.*
import kotlinx.coroutines.*
import org.json.JSONException

class CallActivity : AppCompatActivity() {
    private lateinit var binding: MainBinding
    private lateinit var serverJson: String
    private lateinit var divContext: Div2Context
    private lateinit var setting: SharedPreferences

    @OptIn(DelicateCoroutinesApi::class)
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

        GlobalScope.launch(Dispatchers.Main) {
            val client = OkHttpClient()

            val json = client.loadText("https://api.florazon.net/laravel/public/med?json=call&guid=$guid")

            if (json != null) {
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

    private fun createDivConfiguration(): DivConfiguration {
        return DivConfiguration.Builder(MedDivImageLoader(this))
            .actionHandler(UIDiv2ActionHandler(this))
            .divDownloader(DemoDivDownloader(this))
            .supportHyphenation(true)
            .typefaceProvider(YandexSansDivTypefaceProvider(this))
            .visualErrorsEnabled(true)
            .build()
    }
}

@Suppress("BlockingMethodInNonBlockingContext")
suspend fun OkHttpClient.loadText(uri: String): String? = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(uri).build()
    return@withContext newCall(request).execute().body?.string()
}

class DemoDivDownloader(cont: Context) : DivDownloader {

    fun JSONObject.asDivPatchWithTemplates(errorLogger: ParsingErrorLogger? = null): DivPatch {
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

            val json = client.loadText(downloadUrl)

            if (json != null) {
                try {
                    println(json)
                    callback.onSuccess(JSONObject(json).asDivPatchWithTemplates())
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