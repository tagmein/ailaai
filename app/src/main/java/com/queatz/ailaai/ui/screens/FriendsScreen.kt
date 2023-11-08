package com.queatz.ailaai.ui.screens

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import app.ailaai.api.*
import at.bluesource.choicesdk.maps.common.LatLng
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.queatz.ailaai.OnLifecycleEvent
import com.queatz.ailaai.R
import com.queatz.ailaai.data.api
import com.queatz.ailaai.extensions.*
import com.queatz.ailaai.helpers.locationSelector
import com.queatz.ailaai.services.messages
import com.queatz.ailaai.services.push
import com.queatz.ailaai.ui.components.*
import com.queatz.ailaai.ui.dialogs.ChooseGroupDialog
import com.queatz.ailaai.ui.dialogs.ChoosePeopleDialog
import com.queatz.ailaai.ui.dialogs.TextFieldDialog
import com.queatz.ailaai.ui.dialogs.defaultConfirmFormatter
import com.queatz.ailaai.ui.theme.PaddingDefault
import com.queatz.db.Group
import com.queatz.db.GroupExtended
import com.queatz.db.Member
import com.queatz.db.Person
import com.queatz.push.GroupPushData
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun FriendsScreen(navController: NavController, me: () -> Person?) {
    val state = rememberLazyListState()
    var searchText by rememberSaveable { mutableStateOf("") }
    var allGroups by remember { mutableStateOf(emptyList<GroupExtended>()) }
    var allHiddenGroups by remember { mutableStateOf(emptyList<GroupExtended>()) }
    var allPeople by remember { mutableStateOf(emptyList<Person>()) }
    var results by remember { mutableStateOf(emptyList<SearchResult>()) }
    var isLoading by rememberStateOf(true)
    var createGroupName by remember { mutableStateOf("") }
    var showCreateGroupName by rememberStateOf(false)
    var showCreateGroupMembers by rememberStateOf(false)
    var showPushPermissionDialog by rememberStateOf(false)
    var showHiddenGroupsDialog by rememberStateOf(false)
    val notificationPermissionState = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedHiddenGroups by rememberStateOf(listOf<Group>())
    var tab by rememberSavableStateOf(MainTab.Friends)
    var geo: LatLng? by remember { mutableStateOf(null) }
    val locationSelector = locationSelector(
        geo,
        { geo = it },
        navController.context as Activity
    )
    val reloadFlow = remember {
        MutableSharedFlow<Boolean>()
    }

    LaunchedEffect(geo) {
        geo?.let {
            api.myGeo(it.toGeo())
        }
    }

    fun update() {
        results = allPeople.map { SearchResult.Connect(it) } +
                searchText.trim().let { text ->
                    (if (text.isBlank()) allGroups else allGroups.filter {
                        (it.group?.name?.contains(text, true) ?: false) ||
                                it.members?.any {
                                    it.person?.id != me()?.id && it.person?.name?.contains(
                                        text,
                                        true
                                    ) ?: false
                                } ?: false
                    }).map { SearchResult.Group(it) }
                }
    }

    suspend fun reload(passive: Boolean = false) {
        isLoading = results.isEmpty()
        when (tab) {
            MainTab.Friends -> {
                api.groups(
                    onError = {
                        if (!passive) {
                            context.showDidntWork()
                        }
                    }
                ) {
                    allGroups = it.filter { it.group != null }
                    messages.refresh(me(), allGroups)
                }
            }

            MainTab.Local -> {
                if (geo != null) {
                    api.exploreGroups(
                        geo!!.toGeo(),
                        searchText,
                        public = true,
                        onError = {
                            if (!passive) {
                                context.showDidntWork()
                            }
                        }
                    ) {
                        allGroups = it.filter { it.group != null }
                    }
                }
            }

            else -> {}
        }
        update()
        isLoading = false
    }

    LaunchedEffect(Unit) {
        reloadFlow.collectLatest {
            reload(it)
        }
    }

    LaunchedEffect(Unit) {
        push.events
            .filterIsInstance<GroupPushData>()
            .collectLatest {
                reloadFlow.emit(true)
            }
    }

    OnLifecycleEvent { event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                reloadFlow.emit(true)
            }

            else -> {}
        }
    }

    LaunchedEffect(Unit) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (!notificationManager.areNotificationsEnabled()) {
            if (!notificationPermissionState.status.isGranted) {
                if (notificationPermissionState.status.shouldShowRationale) {
                    showPushPermissionDialog = true
                } else {
                    notificationPermissionState.launchPermissionRequest()
                }
            }
        }
    }

    LaunchedEffect(searchText) {
        if (searchText.isBlank()) {
            allPeople = emptyList()
        } else {
            api.people(searchText) {
                allPeople = it
            }
        }
        update()
        reloadFlow.emit(true)
    }

    var skipFirst by rememberStateOf(true)

    LaunchedEffect(geo) {
        if (geo == null || tab != MainTab.Local) {
            return@LaunchedEffect
        }
        if (skipFirst) {
            skipFirst = false
            return@LaunchedEffect
        }
        // todo search server, set allGroups
        reloadFlow.emit(true)
    }

    fun setTab(it: MainTab) {
        tab = it
        allGroups = emptyList()
        results = emptyList()
        allPeople = emptyList()
        isLoading = true
        scope.launch {
            state.scrollToTop()
            reloadFlow.emit(false)
        }
    }

    val tabs = listOf(MainTab.Friends, MainTab.Local)

    if (selectedHiddenGroups.isNotEmpty()) {
        AlertDialog(
            {
                selectedHiddenGroups = emptyList()
            },
            title = {
                Text(pluralStringResource(R.plurals.x_groups, selectedHiddenGroups.size, selectedHiddenGroups.size))
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(PaddingDefault)) {
                    TextButton(
                        {
                            selectedHiddenGroups = emptyList()
                        }
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(
                        {
                            scope.launch {
                                coroutineScope {
                                    allHiddenGroups
                                        .filter { group -> selectedHiddenGroups.any { it.id == group.group?.id } }
                                        .mapNotNull { groupExtended ->
                                            val member =
                                                groupExtended.members?.firstOrNull { it.person?.id == me()?.id }?.member
                                                    ?: return@mapNotNull null

                                            async {
                                                api.removeMember(member.id!!)
                                            }
                                        }.awaitAll()
                                    reloadFlow.emit(true)
                                    selectedHiddenGroups = emptyList()
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.leave), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    {
                        scope.launch {
                            coroutineScope {
                                allHiddenGroups
                                    .filter { group -> selectedHiddenGroups.any { it.id == group.group?.id } }
                                    .mapNotNull { groupExtended ->
                                        val member =
                                            groupExtended.members?.firstOrNull { it.person?.id == me()?.id }?.member
                                                ?: return@mapNotNull null

                                        async {
                                            api.updateMember(
                                                member.id!!,
                                                Member(
                                                    hide = false
                                                )
                                            )
                                        }
                                    }.awaitAll()
                                reloadFlow.emit(true)
                                selectedHiddenGroups = emptyList()
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.show))
                }
            })
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppHeader(
            navController,
            stringResource(R.string.your_groups),
            {
                scope.launch {
                    state.scrollToTop()
                }
            },
            me
        ) {
            var showMenu by rememberStateOf(false)
            IconButton(
                {
                    showMenu = true
                }
            ) {
                Icon(Icons.Outlined.MoreVert, null)
                Dropdown(showMenu, { showMenu = false }) {
                    DropdownMenuItem({
                        Text(stringResource(R.string.hidden_groups))
                    }, {
                        showMenu = false
                        showHiddenGroupsDialog = true
                    })
                }
            }
            ScanQrCodeButton(navController)
        }
        MainTabs(
            tab,
            {
                setTab(it)
            },
            tabs = tabs
        )
        LocationScaffold(
            geo,
            locationSelector,
            navController,
            enabled = tab == MainTab.Local
        ) {
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .swipeMainTabs { setTab(tabs.next(tab, it)) }
            ) {
                LazyColumn(
                    state = state,
                    contentPadding = PaddingValues(
                        PaddingDefault,
                        PaddingDefault,
                        PaddingDefault,
                        PaddingDefault + 80.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(PaddingDefault),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isLoading) {
                        item {
                            Loading()
                        }
                    } else if (results.isEmpty()) {
                        item {
                            Text(
                                stringResource(if (searchText.isBlank()) when (tab) {
                                    MainTab.Friends -> R.string.you_have_no_groups
                                    else -> R.string.no_groups_nearby
                                } else R.string.no_groups_to_show),
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(PaddingDefault * 2)
                            )
                        }
                    } else {
                        items(results, key = {
                            when (it) {
                                is SearchResult.Connect -> "connect:${it.person.id}"
                                is SearchResult.Group -> "group:${it.groupExtended.group!!.id!!}"
                            }
                        }) {
                            ContactItem(
                                navController,
                                it,
                                me(),
                                {
                                    scope.launch {
                                        reloadFlow.emit(true)
                                    }
                                },
                                when (tab) {
                                    MainTab.Friends -> GroupInfo.LatestMessage
                                    else -> GroupInfo.Members
                                }
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(PaddingDefault * 2)
                        .widthIn(max = 480.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .wrapContentWidth()
                    ) {
                        SearchField(
                            searchText,
                            { searchText = it },
                            placeholder = stringResource(R.string.search_people_and_groups),
                        )
                    }
                    FloatingActionButton(
                        onClick = {
                            createGroupName = ""
                            showCreateGroupName = true
                        },
                        modifier = Modifier
                            .padding(start = PaddingDefault * 2)
                    ) {
                        Icon(Icons.Outlined.Add, stringResource(R.string.new_group))
                    }
                }
            }
        }
    }

    if (showHiddenGroupsDialog) {
        var groups by rememberStateOf(listOf<GroupExtended>())
        ChooseGroupDialog(
            {
                showHiddenGroupsDialog = false
            },
            title = stringResource(R.string.hidden_groups),
            confirmFormatter = { stringResource(R.string.next) },
            infoFormatter = { it.seenText(context.getString(R.string.active), me()) },
            me = me(),
            groups = {
                api.hiddenGroups {
                    groups = it
                }
                allHiddenGroups = groups
                groups
            }
        ) { selected ->
            selectedHiddenGroups = selected
        }
    }

    if (showPushPermissionDialog) {
        AlertDialog(
            {
                showPushPermissionDialog = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        navController.goToSettings()
                        showPushPermissionDialog = false
                    }
                ) {
                    Text(stringResource(R.string.open_settings))
                }
            },
            text = {
                Text(stringResource(R.string.notifications_disabled_message))
            }
        )
    }

    if (showCreateGroupName) {
        TextFieldDialog(
            onDismissRequest = { showCreateGroupName = false },
            title = stringResource(R.string.group_name),
            button = stringResource(R.string.next),
            singleLine = true,
            initialValue = createGroupName,
            placeholder = stringResource(R.string.empty_group_name),
            requireModification = false
        ) { value ->
            createGroupName = value
            showCreateGroupName = false
            showCreateGroupMembers = true
        }
    }

    if (showCreateGroupMembers) {
        val someone = stringResource(R.string.someone)
        ChoosePeopleDialog(
            {
                showCreateGroupMembers = false
            },
            title = stringResource(R.string.invite_people),
            confirmFormatter = defaultConfirmFormatter(
                R.string.skip,
                R.string.new_group_with_person,
                R.string.new_group_with_people,
                R.string.new_group_with_x_people
            ) { it.name ?: someone },
            allowNone = true,
            onPeopleSelected = { people ->
                api.createGroup(people.map { it.id!! }) { group ->
                    if (createGroupName.isNotBlank()) {
                        api.updateGroup(group.id!!, Group(name = createGroupName))
                    }
                    navController.navigate("group/${group.id!!}")
                }
            },
            omit = { it.id == me()?.id }
        )
    }
}

fun GroupExtended.seenText(active: String, me: Person?): String? {
    val otherMemberLastSeen = members?.filter { it.person?.id != me?.id }?.maxByOrNull {
        it.person?.seen ?: Instant.fromEpochMilliseconds(0)
    }?.person?.seen
    return (otherMemberLastSeen ?: group?.seen)?.timeAgo()?.lowercase()?.let { timeAgo ->
        "$active $timeAgo"
    }
}
