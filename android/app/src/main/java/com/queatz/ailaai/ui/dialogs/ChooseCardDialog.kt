package com.queatz.ailaai.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.ailaai.api.myCollaborations
import com.queatz.ailaai.R
import com.queatz.ailaai.data.api
import com.queatz.ailaai.extensions.rememberStateOf
import com.queatz.ailaai.ui.components.CardItem
import com.queatz.ailaai.ui.components.DialogBase
import com.queatz.ailaai.ui.theme.pad
import com.queatz.db.Card

@Composable
fun ChooseCardDialog(
    onDismissRequest: () -> Unit,
    onCard: (String) -> Unit
) {
    DialogBase(onDismissRequest) {
        Column(
            modifier = Modifier
                .padding(3.pad)
        ) {
            ChooseCardSelector(
                modifier = Modifier.weight(1f)
            ) {
                onDismissRequest()
                onCard(it)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(1.pad, Alignment.End),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    {
                        onDismissRequest()
                    }
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ChooseCardSelector(
    modifier: Modifier = Modifier,
    onCard: (String) -> Unit
) {
    var searchCardsValue by rememberStateOf("")
    var myCards by rememberStateOf(listOf<Card>())
    var shownCards by rememberStateOf(listOf<Card>())
    val keyboardController = LocalSoftwareKeyboardController.current!!
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(myCards, searchCardsValue) {
        shownCards = if (searchCardsValue.isBlank()) myCards else myCards.filter {
            it.conversation?.contains(searchCardsValue, true) == true ||
                    it.name?.contains(searchCardsValue, true) == true ||
                    it.location?.contains(searchCardsValue, true) == true
        }
    }

    LaunchedEffect(Unit) {
        // todo, also allow choosing someone else's card?
        api.myCollaborations {
            myCards = it
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    OutlinedTextField(
        searchCardsValue,
        onValueChange = { searchCardsValue = it },
        label = { Text(stringResource(R.string.search_cards)) },
        shape = MaterialTheme.shapes.large,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onSearch = {
            keyboardController.hide()
        }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 1.pad)
            .focusRequester(focusRequester)
    )
    LazyVerticalGrid(
        horizontalArrangement = Arrangement.spacedBy(1.pad, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(1.pad, Alignment.Top),
        columns = GridCells.Adaptive(120.dp),
        modifier = modifier
    ) {
        items(shownCards, { it.id!! }) {
            CardItem(
                {
                    onCard(it.id!!)
                },
                card = it,
                isChoosing = true
            )
        }
    }
}
