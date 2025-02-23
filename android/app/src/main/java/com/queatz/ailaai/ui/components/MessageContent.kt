package com.queatz.ailaai.ui.components

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmapOrNull
import app.ailaai.api.card
import app.ailaai.api.deleteMessage
import app.ailaai.api.sticker
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.queatz.ailaai.R
import com.queatz.ailaai.api.story
import com.queatz.ailaai.data.api
import com.queatz.ailaai.data.getAllAttachments
import com.queatz.ailaai.extensions.*
import com.queatz.ailaai.nav
import com.queatz.ailaai.services.say
import com.queatz.ailaai.ui.dialogs.Menu
import com.queatz.ailaai.ui.dialogs.RationaleDialog
import com.queatz.ailaai.ui.dialogs.menuItem
import com.queatz.ailaai.ui.permission.permissionRequester
import com.queatz.ailaai.ui.screens.exploreInitialCategory
import com.queatz.ailaai.ui.stickers.StickerPhoto
import com.queatz.ailaai.ui.story.StoryCard
import com.queatz.ailaai.ui.theme.pad
import com.queatz.db.*
import io.ktor.http.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ColumnScope.MessageContent(
    message: Message,
    isMe: Boolean,
    me: String?,
    showTime: Boolean,
    onShowTime: (Boolean) -> Unit,
    showMessageDialog: Boolean,
    onShowMessageDialog: (Boolean) -> Unit,
    getPerson: (String) -> Person?,
    getMessage: suspend (String) -> Message?,
    onReply: (Message) -> Unit,
    onDeleted: () -> Unit,
    onShowPhoto: (String) -> Unit,
    isReply: Boolean = false,
    selected: Boolean = false,
    onSelectedChange: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isMeActual = me == message.member
    var showDeleteMessageDialog by rememberStateOf(false)
    var attachedCardId by remember { mutableStateOf<String?>(null) }
    var attachedReplyId by remember { mutableStateOf<String?>(null) }
    var attachedStoryId by remember { mutableStateOf<String?>(null) }
    var attachedPhotos by remember { mutableStateOf<List<String>?>(null) }
    var attachedVideos by remember { mutableStateOf<List<String>?>(null) }
    var attachedSticker by remember { mutableStateOf<Sticker?>(null) }
    var attachedCard by remember { mutableStateOf<Card?>(null) }
    var attachedReply by remember { mutableStateOf<Message?>(null) }
    var attachedStory by remember { mutableStateOf<Story?>(null) }
    var attachedStoryNotFound by remember { mutableStateOf(false) }
    var attachedAudio by remember { mutableStateOf<String?>(null) }
    var selectedBitmap by remember { mutableStateOf<String?>(null) }
    val nav = nav
    val writeExternalStoragePermissionRequester = permissionRequester(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    var showStoragePermissionDialog by rememberStateOf(false)

    if (showStoragePermissionDialog) {
        RationaleDialog(
            {
                showStoragePermissionDialog = false
            },
            stringResource(R.string.permission_request)
        )
    }

    // todo: support multiple attachments of the same type
    // todo: right now the only possibility for 2 attachments is with message replies
    message.getAllAttachments().forEach { attachment ->
        when (attachment) {
            is CardAttachment -> {
                attachedCardId = attachment.card
            }

            is PhotosAttachment -> {
                attachedPhotos = attachment.photos
            }

            is VideosAttachment -> {
                attachedVideos = attachment.videos
            }

            is AudioAttachment -> {
                attachedAudio = attachment.audio
            }

            is ReplyAttachment -> {
                attachedReplyId = attachment.message
            }

            is StoryAttachment -> {
                attachedStoryId = attachment.story
            }

            is StickerAttachment -> {
                attachedSticker = Sticker(
                    photo = attachment.photo,
                    message = attachment.message,
                ).apply {
                    id = attachment.sticker
                }
            }
        }
    }

    if (showMessageDialog) {
        val messageString = stringResource(R.string.message)
        val savedString = stringResource(R.string.saved)
        Menu(
            {
                onShowMessageDialog(false)
            }
        ) {
            menuItem(stringResource(R.string.reply)) {
                onShowMessageDialog(false)
                onReply(message)
            }
            if (attachedPhotos?.isNotEmpty() == true && selectedBitmap != null) {
//                item(stringResource(R.string.send)) {
//                    showSendPhoto = true
//                }
                menuItem(stringResource(R.string.share)) {
                    onShowMessageDialog(false)
                    scope.launch {
                        context.imageLoader.execute(
                            ImageRequest.Builder(context)
                                .data(selectedBitmap!!)
                                .target { drawable ->
                                    scope.launch {
                                        drawable.toBitmapOrNull()?.share(context, null)
                                    }
                                }
                                .build()
                        )
                    }
                }
                menuItem(stringResource(R.string.save)) {
                    onShowMessageDialog(false)
                    scope.launch {
                        context.imageLoader.execute(
                            ImageRequest.Builder(context)
                                .data(selectedBitmap!!)
                                .target { drawable ->
                                    drawable.toBitmapOrNull()?.let { bitmap ->
                                        writeExternalStoragePermissionRequester.use(
                                            onPermanentlyDenied = {
                                                showStoragePermissionDialog = true
                                            }
                                        ) {
                                            scope.launch {
                                                bitmap.save(context)?.also {
                                                    context.toast(savedString)
                                                } ?: context.showDidntWork()
                                            }
                                        }
                                    }
                                }
                                .build()
                        )
                    }
                }
            }

            if (attachedAudio != null) {
//                item(stringResource(R.string.send)) {
//                    showSendPhoto = true
//                }
                menuItem(stringResource(R.string.share)) {
                    onShowMessageDialog(false)
                    scope.launch {
                        api.url(attachedAudio!!).shareAudio(context, null)
                    }
                }
            }

            if (message.text?.isBlank() == false) {
                menuItem(stringResource(R.string.copy)) {
                    (message.text ?: "").copyToClipboard(context, messageString)
                    context.toast(R.string.copied)
                    onShowMessageDialog(false)
                }
                menuItem(stringResource(R.string.select_multiple)) {
                    onSelectedChange?.invoke(true)
                    onShowMessageDialog(false)
                }
            }

            if (isMe && !isReply) {
                menuItem(stringResource(R.string.delete)) {
                    showDeleteMessageDialog = true
                    onShowMessageDialog(false)
                }
            }
        }
    }

    if (showDeleteMessageDialog) {
        var disableSubmit by rememberStateOf(false)
        AlertDialog(
            {
                showDeleteMessageDialog = false
            },
            title = {
                Text(stringResource(R.string.delete_this_message))
            },
            text = {
                Text(stringResource(R.string.delete_message_description))
            },
            confirmButton = {
                TextButton(
                    {
                        scope.launch {
                            disableSubmit = true
                            api.deleteMessage(message.id!!) {
                                onDeleted()
                                showDeleteMessageDialog = false
                            }
                            disableSubmit = false
                        }
                    },
                    enabled = !disableSubmit,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton({
                    showDeleteMessageDialog = false
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        attachedCardId?.let { cardId ->
            api.card(cardId, onError = {
                // todo show failed to load
            }) { attachedCard = it }
        }
    }

    LaunchedEffect(Unit) {
        attachedReplyId?.let { messageId ->
            attachedReply = getMessage(messageId)
        }
    }

    LaunchedEffect(Unit) {
        attachedStoryId?.let { storyId ->
            api.story(
                storyId,
                onError = {
                    if (it.status == HttpStatusCode.NotFound) {
                        attachedStoryNotFound = true
                    }
                }
            ) {
                attachedStory = it
            }
        }
    }

    attachedReply?.takeIf { !isReply }?.let { reply ->
        var viewport by remember { mutableStateOf(Size(0f, 0f)) }
        var showReplyTime by rememberStateOf(false)
        var showReplyMessageDialog by rememberStateOf(false)
        Box(
            modifier = Modifier
                .align(if (isMe) Alignment.End else Alignment.Start)
                .let {
                    when (isMe) {
                        true -> it.padding(start = 8.pad)
                        false -> it.padding(end = 8.pad)
                    }
                }
                .padding(horizontal = 1.pad)
                .clip(
                    when (isMe) {
                        true -> RoundedCornerShape(
                            MaterialTheme.shapes.large.topStart,
                            MaterialTheme.shapes.medium.topEnd,
                            MaterialTheme.shapes.medium.bottomEnd,
                            MaterialTheme.shapes.large.bottomStart
                        )

                        false -> RoundedCornerShape(
                            MaterialTheme.shapes.medium.topStart,
                            MaterialTheme.shapes.large.topEnd,
                            MaterialTheme.shapes.large.bottomEnd,
                            MaterialTheme.shapes.medium.bottomStart
                        )
                    }
                )
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                .combinedClickable(
                    onClick = { showReplyTime = !showReplyTime },
                    onLongClick = { showReplyMessageDialog = true }
                )
        ) {
            Box(
                modifier = Modifier
                    .align(if (isMe) Alignment.CenterEnd else Alignment.CenterStart)
                    .fillMaxHeight()
                    .heightIn(min = viewport.height.inDp()) // todo why is even it needed
                    .requiredWidth(4.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            ) {}
            Column(
                modifier = Modifier
                    .onPlaced {
                        viewport = it.boundsInParent().size
                    }
                    .let {
                        when (isMe) {
                            true -> it.padding(end = 4.dp + 1.pad)
                            false -> it.padding(start = 4.dp + 1.pad)
                        }
                    }
            ) {
                MessageContent(
                    reply,
                    isMe,
                    me,
                    showReplyTime,
                    { showReplyTime = it },
                    showReplyMessageDialog,
                    { showReplyMessageDialog = it },
                    getPerson,
                    getMessage,
                    onReply,
                    {}, // todo delete from reply
                    {}, // todo open photo in reply
                    isReply = true
                )
            }
            Icon(
                Icons.Outlined.Reply,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f),
                modifier = Modifier
                    .padding(vertical = 2.dp, horizontal = 6.dp)
                    .align(if (isMe) Alignment.TopEnd else Alignment.TopStart)
                    .requiredSize(12.dp)
            )
        }
    }

    attachedCardId?.let {
        Box(modifier = Modifier
            .padding(1.pad)
            .widthIn(max = 320.dp)
            .let {
                if (isReply) {
                    it
                } else {
                    when (isMe) {
                        true -> it.padding(start = 12.pad)
                            .align(Alignment.End)

                        false -> it.padding(end = 12.pad)
                            .align(Alignment.Start)
                    }
                }
            }
        ) {
            CardItem(
                {
                    nav.navigate("card/$it")
                },
                onCategoryClick = {
                    exploreInitialCategory = it
                    nav.navigate("explore")
                },
                card = attachedCard,
                isChoosing = true
            )
        }
    }

    attachedPhotos?.ifNotEmpty?.let { photos ->
        Column(
            verticalArrangement = Arrangement.spacedBy(1.pad),
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier
                .padding(1.pad)
                .let {
                    if (isReply) {
                        it
                    } else {
                        when (isMe) {
                            true -> it.padding(start = 12.pad)
                                .align(Alignment.End)

                            false -> it.padding(end = 12.pad)
                                .align(Alignment.Start)
                        }
                    }
                }
        ) {
            photos.forEach {
                PhotoItem(
                    it,
                    onClick = {
                        onShowPhoto(it)
                    },
                    onLongClick = {
                        selectedBitmap = api.url(it)
                        onShowMessageDialog(true)
                    }
                )
            }
        }
    }

    attachedVideos?.ifNotEmpty?.let { videos ->
        Column(
            verticalArrangement = Arrangement.spacedBy(1.pad),
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier
                .padding(1.pad)
                .let {
                    if (isReply) {
                        it
                    } else {
                        when (isMe) {
                            true -> it.padding(start = 12.pad)
                                .align(Alignment.End)

                            false -> it.padding(end = 12.pad)
                                .align(Alignment.Start)
                        }
                    }
                }
        ) {
            videos.forEach {
                var isPlaying by remember {
                    mutableStateOf(false)
                }
                // todo loading state
                Box {
                    Video(
                        it.let(api::url),
                        isPlaying = isPlaying,
                        modifier = Modifier.clip(MaterialTheme.shapes.large).clickable {
                            isPlaying = !isPlaying
                        }
                    )
                    if (!isPlaying) {
                        Icon(
                            Icons.Outlined.PlayArrow,
                            null,
                            modifier = Modifier
                                .padding(1.pad)
                                .align(Alignment.Center)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = .5f))
                                .padding(1.pad)
                        )
                    }
                }
            }
        }
    }

    attachedAudio?.let { audioUrl ->
        Card(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .padding(1.pad)
                .let {
                    if (isReply) {
                        it
                    } else {
                        if (isMe) {
                            it.padding(start = 8.pad)
                        } else {
                            it.padding(end = 8.pad)
                        }
                    }
                }
                .clip(MaterialTheme.shapes.large)
        ) {
            Audio(
                api.url(audioUrl),
                modifier = Modifier
                    .fillMaxSize()
            )
        }
    }

    attachedSticker?.photo?.let { stickerPhoto ->
        StickerPhoto(
            stickerPhoto,
            modifier = Modifier.padding(1.pad).let {
                if (isReply) {
                    it
                } else {
                    when (isMe) {
                        true -> it.padding(start = 8.pad)
                            .align(Alignment.End)

                        false -> it.padding(end = 8.pad)
                            .align(Alignment.Start)
                    }
                }
            },
            onLongClick = {
                scope.launch {
                    api.sticker(
                        id = attachedSticker?.id ?: return@launch context.showDidntWork(),
                        onError = {
                            when (it.status) {
                                HttpStatusCode.NotFound -> context.toast(R.string.sticker_pack_not_found)
                                else -> context.showDidntWork()
                            }
                        }
                    ) {
                        val id = it.pack!!
                        nav.navigate("sticker-pack/$id")
                    }
                }
            }
        ) {
            scope.launch {
                say.say(attachedSticker?.message)
            }
        }
    }

    attachedStoryId?.also { storyId ->
        StoryCard(
            attachedStory,
            isLoading = !attachedStoryNotFound,
            modifier = Modifier.padding(1.pad).then(
                if (isReply) {
                    Modifier
                } else {
                    when (isMe) {
                        true -> Modifier.padding(start = 8.pad)
                            .align(Alignment.End)

                        false -> Modifier.padding(end = 8.pad)
                            .align(Alignment.Start)
                    }
                }
            )
        ) {
            if (!attachedStoryNotFound) {
                nav.navigate("story/$storyId")
            }
        }
    }

    if (!message.text.isNullOrBlank()) {
        LinkifyText(
            message.text ?: "",
            color = if (isMeActual) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .align(if (isMe) Alignment.End else Alignment.Start)
                .padding(1.pad)
                .let {
                    if (isReply) {
                        it
                    } else {
                        when (isMe) {
                            true -> it.padding(start = 8.pad)
                            false -> it.padding(end = 8.pad)
                        }
                    }
                }
                .clip(MaterialTheme.shapes.large)
                .background(if (isMeActual) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background)
                .border(
                    if (isMeActual) 0.dp else 1.dp,
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.shapes.large
                )
                .let {
                    val selectedBorderDp by animateDpAsState(if (selected) 2.dp else 0.dp)
                    if (selectedBorderDp != 0.dp) {
                        it.border(selectedBorderDp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
                    } else {
                        it
                    }
                }
                .combinedClickable(
                    onClick = { onShowTime(!showTime) },
                    onLongClick = { onShowMessageDialog(true) }
                )
                .padding(2.pad, 1.pad)
        )
    }

    AnimatedVisibility(showTime, modifier = Modifier.align(if (isMe) Alignment.End else Alignment.Start)) {
        Text(
            "${message.createdAt!!.timeAgo()}, ${getPerson(message.member!!)?.name ?: stringResource(R.string.someone)}",
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodySmall,
            textAlign = if (isMe) TextAlign.End else TextAlign.Start,
            modifier = Modifier
                .padding(horizontal = 1.pad)
                .let {
                    if (isReply) {
                        it.padding(bottom = .5f.pad)
                    } else {
                        it
                    }
                }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoItem(photo: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    var aspect by remember(photo) {
        mutableFloatStateOf(0.75f)
    }
    var isLoaded by remember(photo) {
        mutableStateOf(false)
    }
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(api.url(photo))
            .crossfade(true)
            .build(),
        alpha = if (isLoaded) 1f else .125f,
//        placeholder = rememberVectorPainter(Icons.Outlined.Photo),
        contentScale = ContentScale.Fit,
        onSuccess = {
            isLoaded = true
            aspect = it.result.drawable.intrinsicWidth.toFloat() / it.result.drawable.intrinsicHeight.toFloat()
        },
        contentDescription = "",
        alignment = Alignment.Center,
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp /* Card elevation */))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .heightIn(min = 80.dp, max = 320.dp)
            .widthIn(min = 80.dp, max = 320.dp)
            .aspectRatio(aspect)
    )
}
