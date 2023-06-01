package com.industry.med

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class DisplayAlert : AppCompatActivity() {
    internal var title: String? = null
    internal var body: String? = null
    internal var guid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = intent.extras
        if (b != null) {
            title = b!!.getString("title")
            body = b!!.getString("body")
            guid = b!!.getString("guid")
            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            builder.setTitle(title)
            builder.setMessage(body).setCancelable(false).setPositiveButton("Открыть") { _, _ ->
                val addIntent = Intent(this, CallActivity::class.java)
                addIntent.putExtra("guid", guid)
                addIntent.putExtra("cord", false)
                ContextCompat.startActivity(this, addIntent, null)
                finish()
            }.setNegativeButton("Закрыть") {_, _ ->}
            val alertDialog = builder.create()
            alertDialog.show()
        }
    }
    companion object {
        var b: Bundle? = null
    }
}