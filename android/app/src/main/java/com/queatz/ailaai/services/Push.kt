package com.queatz.ailaai.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import com.queatz.ailaai.R
import com.queatz.ailaai.data.json
import com.queatz.ailaai.dataStore
import com.queatz.ailaai.push.receive
import com.queatz.db.Person
import com.queatz.push.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val push by lazy {
    Push()
}

class Push {

    internal lateinit var context: Context
    var navController: NavController? = null
    var latestEvent: Lifecycle.Event? = null
    val scope = CoroutineScope(Dispatchers.Default)
    private val meKey = stringPreferencesKey("me")
    internal var meId: String? = null

    internal val latestMessageFlow = MutableSharedFlow<String?>()
    val latestMessage: Flow<String?> = latestMessageFlow

    private val eventsFlow = MutableSharedFlow<PushDataData>()
    val events: Flow<PushDataData> = eventsFlow

    private val notificationManager by lazy {
        context.getSystemService(NotificationManager::class.java)
    }

    suspend fun setMe(id: String) {
        meId = id
        context.dataStore.edit {
            it[meKey] = id
        }
    }

    fun got(data: Map<String, String>) {
        if (!data.containsKey("action")) {
            Log.w("PUSH", "Push notification does not contain 'action'")
            return
        }

        Log.d("PUSH", "Got push: ${data["action"]}")

        val action = data["action"]!!
        val push = data["data"]!!

        try {
            when (PushAction.valueOf(action)) {
                PushAction.Message -> receive(parse<MessagePushData>(push))
                PushAction.Collaboration -> receive(parse<CollaborationPushData>(push))
                PushAction.JoinRequest -> receive(parse<JoinRequestPushData>(push))
                PushAction.Group -> receive(parse<GroupPushData>(push))
            }
        } catch (ex: Throwable) {
            ex.printStackTrace()
        }
    }

    private inline fun <reified T : PushDataData> parse(action: String): T =
        json.decodeFromString<T>(action).also {
            scope.launch {
                eventsFlow.emit(it)
            }
        }

    internal fun personNameOrYou(person: Person?): String {
        return if (person?.id == meId) {
            context.getString(R.string.inline_you)
        } else {
            person?.name ?: context.getString(R.string.inline_someone)
        }
    }

    internal fun send(
        intent: Intent,
        channel: Notifications,
        groupKey: String,
        title: String,
        text: String
    ) {
        if (!notificationManager.areNotificationsEnabled()) {
            return
        }

        val builder = NotificationCompat.Builder(context, channel.key)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setGroup(groupKey)
            .setAutoCancel(true)
            .setContentIntent(TaskStackBuilder.create(context).run {
                    addNextIntentWithParentStack(intent)
                    getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                }
            )

        notificationManager.notify(groupKey, 1, builder.build())
    }

    internal fun makeGroupKey(groupId: String) = "group/$groupId"

    fun clear(groupId: String) {
        notificationManager.cancel(makeGroupKey(groupId), 1)
    }

    fun init(context: Context) {
        this.context = context

        CoroutineScope(Dispatchers.Default).launch {
            meId = context.dataStore.data.first()[meKey]
        }

        Notifications.entries.forEach { channel ->
            val notificationChannel = NotificationChannel(
                channel.key,
                context.getString(channel.channelName),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationChannel.description = context.getString(channel.description)
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }
}

enum class Notifications(@StringRes val channelName: Int, @StringRes val description: Int) {
    Messages(R.string.messages, R.string.messages_notification_channel_description),
    Host(R.string.host, R.string.host_notification_channel_description),
    Collaboration(R.string.collaboration, R.string.collaboration_notification_channel_description);
    val key get() = name.lowercase()
}
