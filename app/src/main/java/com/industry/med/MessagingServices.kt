package com.industry.med

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
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


private var guidM = ""

@SuppressLint("UnspecifiedImmutableFlag", "WrongConstant")
fun NotificationManager.sendNotification(messageBody: String, title: String, applicationContext: Context) {
    val contentIntent = Intent(applicationContext, CallActivity::class.java)
    contentIntent.putExtra("guid", guidM)
    contentIntent.putExtra("cord", true)

    var contentPendingIntent: PendingIntent? = null

    contentPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.getActivity(applicationContext, 0, contentIntent, PendingIntent.FLAG_MUTABLE)
    } else {
        PendingIntent.getActivity(applicationContext, 0, contentIntent, PendingIntent.FLAG_ONE_SHOT)
    }

    val defaultSoundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    val builder = NotificationCompat.Builder(applicationContext, "Default")
        .setSmallIcon(R.drawable.border)
        .setContentTitle(title)
        .setContentText(messageBody)
        .setContentIntent(contentPendingIntent)
        .setSound(defaultSoundUri)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)

    notify(0, builder.build())
}

fun NotificationManager.cancelNotifications() {
    cancelAll()
}

class MessagingServices : FirebaseMessagingService() {
    @SuppressLint("RestrictedApi")
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        remoteMessage.data.let {
            Log.d(TAG, "Message data payload: " + remoteMessage.data)
            if (remoteMessage.data["body"] != null && remoteMessage.data["title"] != null && remoteMessage.data["guid"] != null) {
                val mDisplayAlert = Intent(this, DisplayAlert::class.java)
                mDisplayAlert.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                mDisplayAlert.putExtra("title", remoteMessage.data["title"])
                mDisplayAlert.putExtra("body", remoteMessage.data["body"])
                mDisplayAlert.putExtra("guid", remoteMessage.data["guid"])
                startActivity(mDisplayAlert)
            }

            if (remoteMessage.data["guid"] != null) {
                guidM = remoteMessage.data["guid"].toString()
            }
        }

        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            sendNotification(it.body!!, it.title!!)
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        if (com.industry.med.token != "") sendRegistrationToServer(token)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun sendRegistrationToServer(token: String) {
        GlobalScope.launch(Dispatchers.Main) {
            val client = OkHttpClient()

            val jsonObject = JSONObject()

            jsonObject.put("Token", com.industry.med.token)
            jsonObject.put("TokenFairBase", token)

            val jsonObjectString = jsonObject.toString()

            val requestBody = jsonObjectString.toRequestBody("application/json".toMediaTypeOrNull())
            client.loadText("$apiUrl/firebase", requestBody)
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