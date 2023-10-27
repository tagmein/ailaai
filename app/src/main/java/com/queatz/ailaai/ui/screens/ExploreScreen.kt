package com.queatz.ailaai.ui.screens

import android.app.Activity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.ailaai.api.cards
import app.ailaai.api.myGeo
import app.ailaai.api.savedCards
import at.bluesource.choicesdk.maps.common.LatLng
import com.queatz.ailaai.R
import com.queatz.ailaai.data.api
import com.queatz.ailaai.extensions.*
import com.queatz.ailaai.helpers.LocationSelector
import com.queatz.ailaai.helpers.locationSelector
import com.queatz.ailaai.ui.components.*
import com.queatz.ailaai.ui.state.latLngSaver
import com.queatz.ailaai.ui.theme.ElevationDefault
import com.queatz.ailaai.ui.theme.PaddingDefault
import com.queatz.db.Card
import com.queatz.db.Person
import io.ktor.utils.io.*
import kotlinx.coroutines.launch

var exploreInitialCategory: String? = null

@Composable
fun ExploreScreen(navController: NavController, me: () -> Person?) {
    val state = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var value by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf(exploreInitialCategory) }
    var categories by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var geo: LatLng? by rememberSaveable(stateSaver = latLngSaver()) { mutableStateOf(null) }
    var mapGeo: LatLng? by rememberSaveable(stateSaver = latLngSaver()) { mutableStateOf(null) }
    var shownValue by rememberSaveable { mutableStateOf("") }
    var cards by remember { mutableStateOf(emptyList<Card>()) }
    var hasInitialCards by rememberStateOf(false)
    var isLoading by rememberStateOf(true)
    var isError by rememberStateOf(false)
    var showAsMap by rememberSavableStateOf(false)
    var offset by remember { mutableIntStateOf(0) }
    val limit = 20
    var hasMore by rememberStateOf(true)
    var shownGeo: LatLng? by rememberSaveable(stateSaver = latLngSaver()) { mutableStateOf(null) }
    val locationSelector = locationSelector(
        geo,
        { geo = it },
        navController.context as Activity
    )
    var tab by rememberSavableStateOf(MainTab.Friends)
    var shownTab by rememberSaveable { mutableStateOf(tab) }

    LaunchedEffect(geo) {
        geo?.let {
            api.myGeo(it.toGeo())
        }
    }

    fun updateCategories() {
        selectedCategory = selectedCategory ?: exploreInitialCategory
        categories = ((exploreInitialCategory?.let(::listOf) ?: emptyList()) + cards
            .flatMap { it.categories ?: emptyList() })
            .distinct()
        exploreInitialCategory = null
    }

    fun onNewPage(page: List<Card>, clear: Boolean) {
        val oldSize = if (clear) 0 else cards.size
        cards = if (clear) {
            page
        } else {
            (cards + page).distinctBy { it.id }
        }
        updateCategories()
        offset = cards.size
        hasMore = cards.size > oldSize
        isError = false
        isLoading = false
        shownGeo = geo
        shownValue = value
        shownTab = tab

        if (clear) {
            scope.launch {
                state.scrollToTop()
            }
        }
    }

    suspend fun loadMore(clear: Boolean = false) {
        val geo = (mapGeo?.takeIf { showAsMap } ?: geo)!!
        if (clear) {
            offset = 0
            hasMore = true
            isLoading = true
            cards = emptyList()
        }
        when (tab) {
            MainTab.Friends,
            MainTab.Local -> {
                api.cards(
                    geo.toGeo(),
                    offset = offset,
                    limit = limit,
                    search = value.notBlank,
                    public = tab == MainTab.Local,
                    onError = { ex ->
                        if (ex is CancellationException || ex is InterruptedException) {
                            // Ignore, probably geo or search value changed, keep isLoading = true
                        } else {
                            isLoading = false
                            isError = true
                        }
                    }
                ) {
                    onNewPage(it, clear)
                }
            }

            MainTab.Saved -> {
                api.savedCards(
                    offset,
                    limit,
                    value.notBlank,
                    onError = { ex ->
                        if (ex is CancellationException || ex is InterruptedException) {
                            // Ignore, probably geo or search value changed, keep isLoading = true
                        } else {
                            isLoading = false
                            isError = true
                        }
                    }) {
                    onNewPage(it.mapNotNull { it.card }, clear)
                }
            }
        }
    }

    LaunchedEffect(geo, mapGeo, value, tab) {
        if (geo == null && mapGeo == null) {
            return@LaunchedEffect
        }

        if (hasInitialCards) {
            hasInitialCards = false
            return@LaunchedEffect
        }

        // Don't reload if moving < 100m
        if (shownGeo != null && (mapGeo?.takeIf { showAsMap } ?: geo)!!.distance(shownGeo!!) < 100 && shownValue == value && shownTab == tab) {
            return@LaunchedEffect
        }

        // The map doesn't clear for geo updates, but should for value and tab changes
        loadMore(clear = !showAsMap || shownValue != value || shownTab != tab)
    }

    LocationScaffold(
        geo,
        locationSelector,
        navController,
        appHeader = {
            AppHeader(
                navController,
                stringResource(R.string.explore),
                {},
                me
            ) {
                ScanQrCodeButton(navController)
            }
        }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppHeader(
                navController,
                stringResource(R.string.explore),
                {
                    scope.launch {
                        state.scrollToTop()
                    }
                },
                me
            ) {
                IconButton({
                    showAsMap = !showAsMap
                }) {
                    Icon(Icons.Outlined.Map, stringResource(R.string.map))
                }
                ScanQrCodeButton(navController)
            }

            val cardsOfCategory = if (selectedCategory == null) cards else cards.filter {
                it.categories?.contains(
                    selectedCategory
                ) == true
            }

            MainTabs(tab, { tab = it })
            if (showAsMap) {
                Box(
                    contentAlignment = Alignment.BottomCenter,
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    MapScreen(navController, cardsOfCategory) {
                        mapGeo = it
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(PaddingDefault),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(PaddingDefault * 2)
                            .widthIn(max = 480.dp)
                            .fillMaxWidth()
                    ) {
                        SearchContent(
                            locationSelector,
                            isLoading,
                            categories,
                            selectedCategory
                        ) {
                            selectedCategory = it
                        }
                        SearchFieldAndAction(
                            value,
                            { value = it },
                            placeholder = stringResource(R.string.search),
                            action = {
                                Icon(Icons.Outlined.Edit, stringResource(R.string.your_cards))
                            },
                            onAction = {
                                navController.navigate("me")
                            },
                        )
                    }
                }
            } else {
                CardList(
                    state = state,
                    cards = cardsOfCategory,
                    isMine = { it.person == me()?.id },
                    geo = geo,
                    onChanged = {
                        scope.launch {
                            loadMore(clear = true)
                        }
                    },
                    isLoading = isLoading,
                    isError = isError,
                    value = value,
                    valueChange = { value = it },
                    navController = navController,
                    placeholder = stringResource(R.string.search),
                    hasMore = hasMore,
                    onLoadMore = {
                        loadMore()
                    },
                    action = {
                        Icon(Icons.Outlined.Edit, stringResource(R.string.your_cards))
                    },
                    onAction = {
                        navController.navigate("me")
                    },
                    modifier = Modifier
                        .swipeMainTabs { tab = tab.next(it) }
                ) {
                    SearchContent(
                        locationSelector,
                        isLoading,
                        categories,
                        selectedCategory
                    ) {
                        selectedCategory = it
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchContent(
    locationSelector: LocationSelector,
    isLoading: Boolean,
    categories: List<String>,
    category: String?,
    onCategory: (String?) -> Unit
) {
    if (locationSelector.isManual) {
        ElevatedButton(
            elevation = ButtonDefaults.elevatedButtonElevation(ElevationDefault * 2),
            onClick = {
                locationSelector.reset()
            }
        ) {
            Text(
                stringResource(R.string.reset_location),
                modifier = Modifier.padding(end = PaddingDefault)
            )
            Icon(Icons.Outlined.Clear, stringResource(R.string.reset_location))
        }
    }
    if (categories.size > 2 && !isLoading) {
        var viewport by remember { mutableStateOf(Size(0f, 0f)) }
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .onPlaced { viewport = it.boundsInParent().size }
                .horizontalFadingEdge(viewport, scrollState)
        ) {
            categories.forEachIndexed { index, it ->
                OutlinedButton(
                    {
                        onCategory(
                            if (category == it) {
                                null
                            } else {
                                it
                            }
                        )
                    },
                    border = IconButtonDefaults.outlinedIconToggleButtonBorder(
                        true,
                        category == it
                    ),
                    colors = if (category != it) ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground
                    ) else ButtonDefaults.buttonColors(),
                    modifier = Modifier.padding(end = PaddingDefault)
                ) {
                    Text(it)
                }
            }
        }
    }
}
