package com.queatz.ailaai.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.view.MotionEvent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import at.bluesource.choicesdk.maps.common.LatLng
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.queatz.ailaai.R
import com.queatz.ailaai.api.updateCard
import com.queatz.ailaai.data.Card
import com.queatz.ailaai.data.api
import com.queatz.ailaai.extensions.*
import com.queatz.ailaai.services.SavedIcon
import com.queatz.ailaai.services.ToggleSaveResult
import com.queatz.ailaai.services.saves
import com.queatz.ailaai.ui.dialogs.ChooseCategoryDialog
import com.queatz.ailaai.ui.theme.PaddingDefault
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.time.Duration.Companion.seconds

@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun CardItem(
    onClick: (() -> Unit)?,
    onCategoryClick: (String) -> Unit = {},
    onReply: (List<String>) -> Unit = {},
    onChange: () -> Unit = {},
    card: Card?,
    navController: NavController,
    activity: Activity? = null,
    showDistance: LatLng? = null,
    isMine: Boolean = false,
    isMineToolbar: Boolean = true,
    isChoosing: Boolean = false,
    playVideo: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(),
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
    ) {
        var hideContent by rememberStateOf(false)
        val alpha by animateFloatAsState(if (!hideContent) 1f else 0f, tween())
        val scale by animateFloatAsState(if (!hideContent) 1f else 1.125f, tween(DefaultDurationMillis * 2))
        var isSelectingText by rememberStateOf(false)
        var showSetCategory by rememberStateOf(false)
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        LaunchedEffect(hideContent) {
            if (hideContent) {
                delay(2.seconds)
                hideContent = false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(.75f)
                .motionEventSpy {
                    if (it.action == MotionEvent.ACTION_UP) {
                        isSelectingText = false
                    }
                }
                .let {
                    if (onClick != null) {
                        it.combinedClickable(
                            enabled = !isSelectingText,
                            onClick = onClick,
                            onLongClick = {
                                hideContent = true
                            }
                        )
                    } else {
                        it
                    }
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            if (card != null) {
                if (card.video != null) {
                    Video(
                        card.video!!.let(api::url),
                        modifier = Modifier.matchParentSize().scale(scale).clip(MaterialTheme.shapes.large),
                        isPlaying = playVideo
                    )
                } else if (card.photo != null) {
                    card.photo?.also {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(api.url(it))
                                .crossfade(true)
                                .build(),
                            contentDescription = "",
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.Center,
                            modifier = Modifier.matchParentSize().scale(scale)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                ShaderBrush(
                                    ImageShader(
                                        ImageBitmap.imageResource(R.drawable.bkg),
                                        tileModeX = TileMode.Repeated,
                                        tileModeY = TileMode.Repeated
                                    )
                                ),
                                alpha = .5f
                            )
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PaddingDefault),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .alpha(alpha)
                        .padding(PaddingDefault)
                        .align(Alignment.TopEnd)
                ) {
                    IconButton({
                        scope.launch {
                            when (saves.toggleSave(card)) {
                                ToggleSaveResult.Saved -> {
                                    context.toast(R.string.card_saved)
                                }

                                ToggleSaveResult.Unsaved -> {
                                    context.toast(R.string.card_unsaved)
                                }

                                else -> {
                                    context.showDidntWork()
                                }
                            }
                        }
                    }) {
                        SavedIcon(card)
                    }

                    val hasCards = (card.cardCount ?: 0) > 0
                    val distanceText = showDistance?.let {
                        if (card.geo != null) {
                            it.distance(card.latLng!!).takeIf { it < nearbyMaxDistanceKm }?.let { metersAway ->
                                when {
                                    metersAway >= 1000f -> ceil(metersAway / 1000).toInt()
                                        .let { km -> pluralStringResource(R.plurals.km_away, km, km) }

                                    else -> metersAway.approximate(10)
                                        .let { meters -> pluralStringResource(R.plurals.meters_away, meters, meters) }
                                } + (if (hasCards) " • " else "")
                            } ?: (stringResource(R.string.your_friend) + (if (hasCards) " • " else ""))
                        } else {
                            stringResource(R.string.your_friend) + (if (hasCards) " • " else "")
                        }
                    }

                    if (hasCards || distanceText != null) {
                        Text(
                            (distanceText ?: "") + if (hasCards) pluralStringResource(
                                R.plurals.number_of_cards,
                                card.cardCount ?: 0,
                                card.cardCount ?: 0
                            ) else "",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.background.copy(alpha = .8f),
                                    MaterialTheme.shapes.extraLarge
                                )
                                .padding(vertical = PaddingDefault, horizontal = PaddingDefault * 2)
                        )
                    }
                }

                val conversationScrollState = rememberScrollState()
                var conversation by remember { mutableStateOf(emptyList<ConversationItem>()) }
                val recomposeScope = currentRecomposeScope

                LaunchedEffect(conversation) {
                    recomposeScope.invalidate()
                }

                Column(
                    modifier = Modifier
                        .alpha(alpha)
                        .padding(PaddingDefault)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = .96f))
                        .animateContentSize(
                            spring(
                                stiffness = Spring.StiffnessMediumLow,
//                                dampingRatio = Spring.DampingRatioLowBouncy,
                                visibilityThreshold = IntSize.VisibilityThreshold
                            )
                        )
                        .let {
                            if (isChoosing) {
                                it.minAspectRatio(1f)
                            } else {
                                it.minAspectRatio(if (conversation.isNotEmpty()) .75f else 1.5f)
                            }
                        }
                        .padding(PaddingDefault * 1.5f)
                ) {
                    var viewport by remember { mutableStateOf(Size(0f, 0f)) }
                    CardConversation(
                        card,
                        interactable = !isChoosing,
                        onReply = onReply,
                        navController = navController,
                        isMine = isMine,
                        isMineToolbar = isMineToolbar,
                        selectingText = {
                            isSelectingText = it
                        },
                        conversationChange = {
                            conversation = it
                        },
                        onCategoryClick = {
                            if (isMine && isMineToolbar) {
                                showSetCategory = true
                            } else {
                                onCategoryClick(it)
                            }
                        },
                        onSetCategoryClick = {
                            showSetCategory = true
                        },
                        modifier = Modifier
                            .wrapContentHeight()
                            .weight(1f, fill = false)
                            .verticalScroll(conversationScrollState)
                            .onPlaced { viewport = it.boundsInParent().size }
                            .fadingEdge(viewport, conversationScrollState)
                    )

                    if (isMine && isMineToolbar && activity != null) {
                        CardToolbar(
                            navController = navController,
                            activity,
                            onChange,
                            card,
                        )
                    }
                }

                if (showSetCategory) {
                    ChooseCategoryDialog(
                        {
                            showSetCategory = false
                        },
                        { category ->
                            scope.launch {
                                api.updateCard(
                                    card.id!!,
                                    Card().apply {
                                        categories = if (category == null) emptyList() else listOf(category)
                                    }
                                ) {
                                    onChange()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Serializable
data class ConversationItem(
    var title: String = "",
    var message: String = "",
    var action: ConversationAction? = null,
    var items: MutableList<ConversationItem> = mutableListOf(),
)

enum class ConversationAction {
    Message
}

enum class CardParentType {
    Map,
    Card,
    Person,
    Offline
}
