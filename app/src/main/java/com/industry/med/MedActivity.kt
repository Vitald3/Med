package com.industry.med

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import com.industry.med.databinding.MainBinding
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import com.yandex.div.DivDataTag
import com.yandex.div.core.Div2Context
import com.yandex.div.core.DivActionHandler
import com.yandex.div.core.DivConfiguration
import com.yandex.div.core.DivViewFacade
import com.yandex.div.core.images.*
import com.yandex.div.core.view2.Div2View
import com.yandex.div.data.DivParsingEnvironment
import com.yandex.div.json.ParsingErrorLogger
import com.yandex.div2.DivAction
import com.yandex.div2.DivData
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.NestedScrollView
import com.yandex.div.core.font.DivTypefaceProvider
import javax.inject.Inject

class MedActivity : AppCompatActivity() {
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
            baseContext = this@MedActivity,
            configuration = createDivConfiguration()
        )

        val request = Request.Builder().url("https://api.florazon.net/laravel/public/med?json=home").build()
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

                                val div = LinearLayout(this@MedActivity)

                                div.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                                div.orientation = LinearLayout.VERTICAL
                                div.id = R.id.main_layout
                                div.addView(DivViewFactory(divContext, templateJson).createView(cardJson))
                                binding.root.findViewById<NestedScrollView>(R.id.scroll).addView(div)

                                val editor: SharedPreferences.Editor = setting.edit()
                                editor.putInt("card", 0)
                                editor.apply()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(this@MedActivity, "Ошибка загрузки данных", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(this@MedActivity, "Ошибка загрузки данных", Toast.LENGTH_LONG).show()
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

class YandexSansDivTypefaceProvider @Inject constructor(
    private val context: Context
) : DivTypefaceProvider {

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