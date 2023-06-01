package com.industry.med

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@SuppressLint("UnspecifiedImmutableFlag", "WrongConstant")
fun NotificationManager.sendNotification(messageBody: String, title: String, applicationContext: Context) {
    val contentIntent = Intent(applicationContext, CallingActivity::class.java)

    val contentPendingIntent = PendingIntent.getActivity(
        applicationContext,
        0,
        contentIntent,
        PendingIntent.FLAG_UPDATE_CURRENT
    )

    val eggImage = BitmapFactory.decodeResource(
        applicationContext.resources,
        R.drawable.border
    )

    val builder = NotificationCompat.Builder(
        applicationContext,
        "Default"
    )
        .setSmallIcon(R.drawable.border)
        .setContentTitle(title)
        .setContentText(messageBody)
        .setContentIntent(contentPendingIntent)
        .setLargeIcon(eggImage)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)

    notify(0, builder.build())
}

fun NotificationManager.cancelNotifications() {
    cancelAll()
}

class MessagingServices : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        remoteMessage.data.let {
            Log.d(TAG, "Message data payload: " + remoteMessage.data)
        }

        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            sendNotification(it.body!!, it.title!!)
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        if (tokenF != "") sendRegistrationToServer(tokenF)
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun sendRegistrationToServer(token: String) {
        GlobalScope.launch(Dispatchers.Main) {
            val client = OkHttpClient()

            val jsonObject = JSONObject()

            jsonObject.put("Token", com.industry.med.token)
            jsonObject.put("TokenFairBase", token)

            val jsonObjectString = jsonObject.toString()

            val requestBody = jsonObjectString.toRequestBody("application/json".toMediaTypeOrNull())
            client.loadText("https://api.florazon.net/laravel/public/firebase", requestBody)
        }
    }

    private fun sendNotification(messageBody: String, title: String) {
        val notificationManager = ContextCompat.getSystemService(applicationContext, NotificationManager::class.java) as NotificationManager
        notificationManager.sendNotification(messageBody, title, applicationContext)
    }

    companion object {
        private const val TAG = "MessagingServices"
    }
}