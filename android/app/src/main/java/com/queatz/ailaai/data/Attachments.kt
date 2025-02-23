package com.queatz.ailaai.data

import com.queatz.ailaai.extensions.inList
import com.queatz.db.*
import kotlinx.serialization.json.*
import java.util.Collections.emptyList

fun Message.getAttachment() = attachment?.asMessageAttachment()

fun Message.getAttachments() = attachments?.mapNotNull { it.asMessageAttachment() } ?: emptyList()

fun Message.getAllAttachments(): List<MessageAttachment> = getAttachment().inList() + getAttachments()

private fun String.asMessageAttachment(): MessageAttachment? {
    return try {
        val jsonElement = json.decodeFromString<JsonElement>(this)
        when (jsonElement.jsonObject["type"]?.jsonPrimitive?.contentOrNull) {
            "reply" -> json.decodeFromJsonElement<ReplyAttachment>(jsonElement)
            "card" -> json.decodeFromJsonElement<CardAttachment>(jsonElement)
            "photos" -> json.decodeFromJsonElement<PhotosAttachment>(jsonElement)
            "audio" -> json.decodeFromJsonElement<AudioAttachment>(jsonElement)
            "videos" -> json.decodeFromJsonElement<VideosAttachment>(jsonElement)
            "story" -> json.decodeFromJsonElement<StoryAttachment>(jsonElement)
            "sticker" -> json.decodeFromJsonElement<StickerAttachment>(jsonElement)
            else -> null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
