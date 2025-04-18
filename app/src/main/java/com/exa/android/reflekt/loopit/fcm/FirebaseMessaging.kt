package com.exa.android.reflekt.loopit.fcm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.exa.android.reflekt.R
import com.exa.android.reflekt.loopit.data.remote.main.Repository.FirestoreService
import com.exa.android.reflekt.loopit.util.CurChatManager.activeChatId
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import java.util.Random
import javax.inject.Inject

@AndroidEntryPoint
class FirebaseService : FirebaseMessagingService() {

    @Inject
    lateinit var repository: FirestoreService // Inject Repository to update tokens

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New Token: $token")
        repository.updateToken(token) // Save new FCM token in Firestore
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d("FireStore Operation", "From: ${message.from} Data: ${message.data}")

        val senderId = message.data["senderId"] ?: return
        if (senderId == Firebase.auth.currentUser?.uid) return // Ignore self messages

        val chatId = message.data["chatId"] ?: return
        if (chatId == activeChatId) return // Ignore messages from the active chat

        val imageUrl = "https://www.w3schools.com/w3images/avatar2.png"
        val title = message.data["title"] ?: "New Message"
        val body = message.data["body"] ?: "You have a new message"

        showNotification(title, body, imageUrl, senderId, chatId)
        Log.d("FireStore Operation", "From: ${message.from} Data: $chatId, $senderId, $title, $body")
    }

    private fun showNotification(
        senderName: String,
        message: String,
        imageUrl: String?,
        senderId: String,
        chatId : String
    ) {
        val channelId = "messages"
        val notificationId = chatId.hashCode() // different for each chat

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e("Post Notification", "Notification permission not granted!")
                return
            }
        }

        // ✅ Create Notification Channel (For Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for chat messages"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }

        // Retrieve stored messages
        val sharedPreferences = getSharedPreferences("chat_notifications", Context.MODE_PRIVATE)
        val messagesSet = sharedPreferences.getStringSet(chatId, mutableSetOf())!!.toMutableSet()
        // fetching all unread message of that chat Id of all time
        // Add the new message
        messagesSet.add("$senderName: $message")

        // Save updated messages
        sharedPreferences.edit().putStringSet(chatId, messagesSet).apply()

        // Create MessagingStyle notification
        val messagingStyle = NotificationCompat.MessagingStyle(Person.Builder().setName("Chat").build())
            .setConversationTitle(senderName) // Group title

        for (msg in messagesSet) {
            messagingStyle.addMessage(msg, System.currentTimeMillis(), senderName)
        }


        // Create deep link URI
        val deepLinkUri = Uri.parse("reflekt://chat/$senderId")

        // Intent to open the deep link
        val deepLinkIntent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // ✅ Create PendingIntent
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId, // unique identifier for the pending intent
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

//        // ✅ Define "Person" for the sender
//        val personBuild = Person.Builder().setName(senderName)
//
//        // Optional: Use a default user icon
//        if (imageUrl.isNullOrEmpty()) personBuild.setIcon(
//            IconCompat.createWithResource(
//                this,
//                R.drawable.chat_img3
//            )
//        )
//
//        val person = personBuild.build()
//
//        // ✅ Use MessagingStyle for better message categorization
//        val messagingStyle = NotificationCompat.MessagingStyle(person)
//            .setConversationTitle(senderName)
//            .addMessage(message, System.currentTimeMillis(), person)

        // ✅ Build Notification with the "Conversation" style
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setStyle(messagingStyle) //Makes it a conversation notification
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setShortcutId(senderName) //Groups messages from the same sender
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        // ✅ Load Profile Image (Optional)
        if (!imageUrl.isNullOrEmpty()) {
            Glide.with(this)
                .asBitmap()
                .load(imageUrl)
                .transform(CircleCrop())
                .into(object : com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: com.bumptech.glide.request.transition.Transition<in Bitmap>?
                    ) {
                        builder.setLargeIcon(resource) // Set circular image as large icon
                        NotificationManagerCompat.from(this@FirebaseService)
                            .notify(notificationId, builder.build())
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        } else {
            NotificationManagerCompat.from(this)
                .notify(notificationId, builder.build())
        }
    }
}

/*
    private fun sendNotification(title: String?, message: String?) {
        val channelId = "chat_notifications"
        val notificationId = 1001
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = NotificationManagerCompat.from(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Chat Messages", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e("Notification", "Notification permission not granted!")
            return //  Exit without showing notification
        }
        notificationManager.notify(notificationId, builder.build())
    }
}
*/