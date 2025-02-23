package com.queatz.ailaai.ui.screens

import android.graphics.Point
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.compose.ui.zIndex
import androidx.core.graphics.div
import androidx.core.graphics.plus
import androidx.core.view.doOnAttach
import androidx.core.view.doOnDetach
import androidx.navigation.NavController
import at.bluesource.choicesdk.maps.common.*
import at.bluesource.choicesdk.maps.common.Map
import coil.compose.AsyncImage
import com.queatz.ailaai.R
import com.queatz.ailaai.data.api
import com.queatz.ailaai.dataStore
import com.queatz.ailaai.databinding.LayoutMapBinding
import com.queatz.ailaai.extensions.*
import com.queatz.ailaai.helpers.geoKey
import com.queatz.ailaai.ui.dialogs.EditCardDialog
import com.queatz.ailaai.ui.dialogs.Menu
import com.queatz.ailaai.ui.dialogs.menuItem
import com.queatz.ailaai.ui.state.latLngSaver
import com.queatz.ailaai.ui.theme.pad
import com.queatz.db.Card
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow

@Composable
fun MapScreen(
    navController: NavController,
    cards: List<Card>,
    onGeo: (LatLng) -> Unit
) {
    val context = LocalContext.current
    var position by rememberSaveable(stateSaver = latLngSaver()) {
        mutableStateOf(null)
    }
    var mapType by rememberSavableStateOf(Map.MAP_TYPE_NORMAL)
    var zoom by rememberSavableStateOf<Float?>(null)
    val scope = rememberCoroutineScope()
    val disposable = remember { CompositeDisposable() }
    var cameraPosition by rememberStateOf<CameraPosition?>(null)
    var composed by rememberStateOf(false)
    var map: Map? by remember { mutableStateOf(null) }
    var mapView: LayoutMapBinding? by remember { mutableStateOf(null) }
    val recenter = remember { MutableSharedFlow<Pair<LatLng, Float?>>() }
    var showMapClickMenu by remember { mutableStateOf<LatLng?>(null) }

    LaunchedEffect(Unit) {
        if (position == null) {
            val geo =
                context.dataStore.data.first()[geoKey]?.split(",")?.map { it.toDouble() } // todo user LocationSelector
            position = LatLng(geo?.get(0) ?: 0.0, geo?.get(1) ?: 0.0)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            disposable.dispose()
        }
    }

    LaunchedEffect(position) {
        if (position != null) {
            onGeo(position!!)
        }
    }

    LaunchedEffect(cameraPosition) {
        mapType = if ((cameraPosition?.zoom ?: 0f) > 20f) {
            Map.MAP_TYPE_HYBRID
        } else {
            Map.MAP_TYPE_NORMAL
        }
    }

    LaunchedEffect(map, mapType) {
        map?.mapType = mapType
    }

    LaunchedEffect(Unit) {
        recenter.collect {
            map?.animateCamera(
                CameraUpdateFactory.get().newCameraPosition(
                    CameraPosition.Builder()
                        .setTarget(it.first)
                        .setZoom(it.second ?: 14f)
                        .setBearing(if (it.second == null) 0f else map?.cameraPosition?.bearing ?: 0f)
                        .setTilt(if (it.second == null) 0f else map?.cameraPosition?.tilt ?: 0f)
                        .build()
                )
            )
        }
    }

    LaunchedEffect(map) {
        recenter.emit((position ?: return@LaunchedEffect) to zoom)
    }

    val duration = 200
    var cardPositions by rememberStateOf<List<Pin>>(listOf())
    var renderedCards by rememberStateOf(listOf<Card>())

    LaunchedEffect(cards) {
        val cardsWithGeo = cards.filter { it.geo != null }
        val goneCards = renderedCards.filter { rendered -> cardsWithGeo.none { it.id == rendered.id } }
        renderedCards = cardsWithGeo + goneCards
        delay(duration.toLong())
        renderedCards = renderedCards.filter { it !in goneCards }
    }

    LaunchedEffect(map, cameraPosition, renderedCards) {
        map ?: return@LaunchedEffect
        cardPositions = renderedCards.map { card ->
            Pin(
                card,
                map!!.getProjection().toScreenLocation(card.geo!!.toLatLng()!!)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        AndroidViewBinding(
            LayoutMapBinding::inflate,
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            mapView = this
            if (composed) return@AndroidViewBinding else composed = true
            mapFragmentContainerView.doOnAttach { it.doOnDetach { mapFragmentContainerView.removeAllViews() } }

            val mapFragment = mapFragmentContainerView.getFragment<MapFragment>()

            mapFragment.getMapObservable().subscribe {
                map = it
                map?.apply {
                    clear()

                    getUiSettings().isMapToolbarEnabled = true
                    getUiSettings().isMyLocationButtonEnabled = true

                    setOnMapClickListener {
                        showMapClickMenu = it
                    }

                    setOnCameraMoveListener {
                        cameraPosition = map?.cameraPosition
                    }

                    setOnCameraIdleListener {
                        position = map?.cameraPosition?.target
                        zoom = map?.cameraPosition?.zoom
                    }

                    moveCamera(
                        CameraUpdateFactory.get().newCameraPosition(
                            CameraPosition.Builder()
                                .setTarget(position ?: return@apply)
                                .setZoom(zoom ?: 14f)
                                .build()
                        )
                    )
                }
            }.let(disposable::add)
        }
        map?.let { map ->
            fun Point.near(other: Point, distance: Int) =
                abs(x - other.x) <= distance && abs(y - other.y) <= distance

            fun List<Point>.average(): Point {
                return reduce { acc, point -> acc + point } / size.toFloat()
            }

            fun tryNav(position: Point, block: () -> Unit) {
                val nearby = cardPositions.filter { it.position != position && (it.position.near(position, 100)) }
                    .map { it.position } + position
                if (nearby.size == 1) {
                    block()
                } else {
                    val geo = map.getProjection().fromScreenLocation(nearby.average())
                    scope.launch { recenter.emit(geo to map.cameraPosition.zoom + 2f) }
                }
            }

            cardPositions.forEach {
                key(it.card.id) {
                    val (card, pos) = it
                    val s = (map.cameraPosition.zoom / 16f).toDouble().pow(10.0).coerceAtLeast(.75).coerceAtMost(2.0)
                        .toFloat()
                    var size by remember { mutableStateOf(IntSize(0, 0)) }
                    var placed by remember(card.name) { mutableStateOf(false) }
                    val shown = cards.any { c -> c.id == card.id }
                    val scale = remember { Animatable(if (shown) 0f else 1f) }

                    LaunchedEffect(card.id, shown) {
                        if (shown) delay(25L * cardPositions.indexOf(it))
                        scale.animateTo(if (shown) 1f else 0f, tween(duration))
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .wrapContentSize(unbounded = true)
                            .onPlaced {
                                placed = true
                                size = it.size
                            }
                            .offset((pos.x - size.width / 2).px, (pos.y - size.height).px)
                            .zIndex(1f + pos.y)
                            .graphicsLayer(
                                scaleX = s * scale.value,
                                scaleY = s * scale.value,
                                alpha = if (!placed) 0f else scale.value,
                                transformOrigin = TransformOrigin(.5f, 1f)
                            )

                    ) {
                        OutlinedText(
                            card.name ?: "",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .clickable(
                                    MutableInteractionSource(),
                                    null
                                ) {
                                    tryNav(pos) {
                                        navController.navigate("card/${card.id!!}")
                                    }
                                }
                                .widthIn(max = 120.dp)
                        )
                        card.categories?.firstOrNull()?.let { category ->
                            OutlinedText(
                                category,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                outlineColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                outlineWidth = 4f,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(bottom = 1.pad)
                                    .clickable(
                                        MutableInteractionSource(),
                                        null
                                    ) {
                                        tryNav(pos) {
                                            navController.navigate("card/${card.id!!}")
                                        }
                                    }
                                    .widthIn(max = 120.dp)
                            )
                        }
                        AsyncImage(
                            model = card.photo?.let(api::url),
                            contentDescription = "",
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.Center,
                            modifier = Modifier
                                .requiredSize(32.dp)
                                .shadow(2.dp, CircleShape)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .border(1.5f.dp, MaterialTheme.colorScheme.background, CircleShape)
                                .padding(1.5f.dp)
                                .clip(CircleShape)
                                .clickable {
                                    tryNav(pos) {
                                        navController.navigate("card/${card.id!!}")
                                    }
                                }
                        )
                    }
                }
            }
        }
    }

    var newCard by rememberStateOf<Card?>(null)

    if (newCard != null) {
        EditCardDialog(
            newCard!!,
            {
                newCard = null
            },
            create = true
        ) {
            navController.navigate("card/${it.id!!}")
        }
    }

    showMapClickMenu?.let { clickGeo ->
        Menu(
            {
                showMapClickMenu = null
            }
        ) {
            menuItem(stringResource(R.string.add_a_card)) {
                showMapClickMenu = null
                newCard = Card(geo = clickGeo.toList())
            }
            menuItem(stringResource(R.string.go_here)) {
                showMapClickMenu = null
                scope.launch {
                    recenter.emit(clickGeo to null)
                }
            }
        }
    }
}

@Composable
fun OutlinedText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.background,
    outlineColor: Color = MaterialTheme.colorScheme.onBackground,
    outlineWidth: Float = 6f,
    style: TextStyle = MaterialTheme.typography.labelLarge,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign = TextAlign.Start
) {
    Box(
        modifier = modifier
    ) {
        Text(
            text,
            color = outlineColor,
            style = style.copy(
                drawStyle = Stroke(
                    miter = outlineWidth / 2f,
                    width = outlineWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            ),
            fontWeight = fontWeight,
            textAlign = textAlign
        )
        Text(
            text,
            color = color,
            style = style,
            fontWeight = fontWeight,
            textAlign = textAlign
        )
    }
}

data class Pin(
    val card: Card,
    val position: Point
)
