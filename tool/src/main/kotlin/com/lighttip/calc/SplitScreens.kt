package com.lighttip.calc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightLazyScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

private const val ROW_UNITS = 3f

/* ---------------------------------------------------------------- Split list */

class SplitListScreen(
    sealedActivity: SealedLightActivity,
    private val repository: TipRepository,
) : LightScreen<Unit, SplitListViewModel>(sealedActivity) {

    override val viewModelClass: Class<SplitListViewModel> = SplitListViewModel::class.java
    override fun createViewModel() = SplitListViewModel(repository)

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val receipts by viewModel.receipts.collectAsState()

        LightTheme(colors = colors) {
            Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
                LightTopBar(
                    center = LightTopBarCenter.Text("Split a bill"),
                    rightButton = LightBarButton.LightIcon(
                        icon = LightIcons.ADD,
                        onClick = { navigateTo({ CameraCaptureScreen(it, repository) }) },
                        contentDescription = "Add receipt",
                    ),
                )
                Box(Modifier.weight(1f)) {
                    if (receipts.isEmpty()) {
                        CenterMessage(
                            "No receipts yet.\n\nTap + to photograph one.\n" +
                                "Set your Anthropic key in Settings first.",
                        )
                    } else {
                        LightLazyScrollView(uniformItemHeightGridUnits = ROW_UNITS) {
                            items(receipts, key = { it.id }) { receipt ->
                                ReceiptRow(receipt) {
                                    navigateTo({ ReceiptScreen(it, repository, receipt.id) })
                                }
                            }
                        }
                    }
                }
                LightBottomBar(
                    items = listOf(
                        LightBarButton.Text("TIP", onClick = { goBack() }),
                        LightBarButton.Text("[ SPLIT ]", onClick = {}),
                    ),
                )
            }
        }
    }
}

@Composable
private fun ReceiptRow(receipt: ReceiptEntity, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_UNITS.gridUnitsAsDp())
            .lightClickable(onClickLabel = "Open receipt", role = Role.Button) { onOpen() }
            .padding(horizontal = 1f.gridUnitsAsDp()),
        verticalArrangement = Arrangement.Center,
    ) {
        LightText(
            receipt.merchant,
            variant = LightTextVariant.Copy,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val detail = when (receipt.status) {
            ReceiptEntity.STATUS_READING -> "Reading with Claude…"
            ReceiptEntity.STATUS_FAILED -> "Tap to retry"
            ReceiptEntity.STATUS_NO_KEY -> "Set an API key, then retry"
            else -> receipt.totalCents.asMoney()
        }
        LightText(detail, variant = LightTextVariant.Detail, lighten = true, maxLines = 1)
    }
}

/* ------------------------------------------------------------- Receipt detail */

class ReceiptScreen(
    sealedActivity: SealedLightActivity,
    private val repository: TipRepository,
    private val receiptId: String,
) : LightScreen<Unit, ReceiptViewModel>(sealedActivity) {

    override val viewModelClass: Class<ReceiptViewModel> = ReceiptViewModel::class.java
    override fun createViewModel() = ReceiptViewModel(repository, receiptId)

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()
        val receipt = state.receipt

        LightTheme(colors = colors) {
            Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text(receipt?.merchant ?: "Receipt"),
                    rightButton = LightBarButton.LightIcon(
                        icon = LightIcons.ELLIPSES,
                        onClick = {
                            navigateTo({ ReceiptMenuScreen(it, repository, receiptId) })
                        },
                        contentDescription = "Receipt options",
                    ),
                )
                Box(Modifier.weight(1f)) {
                    when {
                        receipt == null -> CenterMessage("…")
                        receipt.status == ReceiptEntity.STATUS_READING ->
                            CenterMessage("Reading the receipt with Claude…")
                        receipt.status == ReceiptEntity.STATUS_NO_KEY ->
                            CenterMessage("No API key set.\nAdd one in Settings, then read again\nfrom the menu above.")
                        receipt.status == ReceiptEntity.STATUS_FAILED ->
                            CenterMessage("Couldn't read that photo.\nTry \"Read again\" from the menu above.")
                        state.people.isEmpty() ->
                            CenterMessage("Add the people at the table first.\nTap PEOPLE below.")
                        else -> ItemList(state)
                    }
                }
                LightBottomBar(
                    items = listOf(
                        LightBarButton.Text(
                            "PEOPLE",
                            onClick = { navigateTo({ PeopleScreen(it, repository, receiptId) }) },
                        ),
                        LightBarButton.Text(
                            "${receipt?.tipPercent ?: DEFAULT_TIP_PERCENT}% TIP",
                            onClick = {
                                navigateTo(
                                    { PercentScreen(it, receipt?.tipPercent ?: DEFAULT_TIP_PERCENT) },
                                    resultCallback = { percent -> viewModel.setTipPercent(percent) },
                                )
                            },
                        ),
                        LightBarButton.Text(
                            "TOTALS",
                            onClick = { navigateTo({ TotalsScreen(it, repository, receiptId) }) },
                        ),
                    ),
                )
            }
        }
    }

    @Composable
    private fun ItemList(state: ReceiptUiState) {
        LightLazyScrollView(uniformItemHeightGridUnits = ROW_UNITS) {
            items(state.items, key = { it.id }) { item ->
                ItemRow(
                    item = item,
                    assigned = state.peopleOn(item.id),
                    onClick = {
                        navigateTo({ AssignScreen(it, repository, receiptId, item.id) })
                    },
                )
            }
        }
    }
}

@Composable
private fun ItemRow(item: ItemEntity, assigned: List<PersonEntity>, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_UNITS.gridUnitsAsDp())
            .lightClickable(onClickLabel = "Assign ${item.name}", role = Role.Button) { onClick() }
            .padding(horizontal = 1f.gridUnitsAsDp()),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LightText(
                item.name,
                variant = LightTextVariant.Copy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            LightText(item.priceCents.asMoney(), variant = LightTextVariant.Copy)
        }
        val tag = if (assigned.isEmpty()) {
            "unassigned"
        } else {
            assigned.joinToString(" ") { initialsOf(it.name) }
        }
        LightText(
            tag,
            variant = LightTextVariant.Detail,
            lighten = assigned.isEmpty(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/* -------------------------------------------------------------- Assign screen */

class AssignScreen(
    sealedActivity: SealedLightActivity,
    private val repository: TipRepository,
    private val receiptId: String,
    private val itemId: String,
) : LightScreen<Unit, ReceiptViewModel>(sealedActivity) {

    override val viewModelClass: Class<ReceiptViewModel> = ReceiptViewModel::class.java
    override fun createViewModel() = ReceiptViewModel(repository, receiptId)

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()
        val item = state.items.firstOrNull { it.id == itemId }
        val assignedIds = state.assignments.filter { it.itemId == itemId }.map { it.personId }.toSet()

        LightTheme(colors = colors) {
            Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.TwoLineDetail(
                        line1 = item?.name ?: "Item",
                        line2 = perHeadDetail(item, assignedIds.size),
                    ),
                )
                Hairline()
                LightLazyScrollView(uniformItemHeightGridUnits = ROW_UNITS) {
                    items(state.people, key = { it.id }) { person ->
                        val checked = person.id in assignedIds
                        SelectableRow(
                            label = person.name,
                            checked = checked,
                            onToggle = { viewModel.toggle(itemId, person.id, !checked) },
                        )
                    }
                }
            }
        }
    }

    private fun perHeadDetail(item: ItemEntity?, headCount: Int): String = when {
        item == null -> ""
        headCount == 0 -> "${item.priceCents.asMoney()} · nobody yet"
        headCount == 1 -> item.priceCents.asMoney()
        else -> "${item.priceCents.asMoney()} · ${(item.priceCents / headCount).asMoney()} each"
    }
}

@Composable
private fun SelectableRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_UNITS.gridUnitsAsDp())
            .lightClickable(onClickLabel = label, role = Role.Checkbox) { onToggle() }
            .padding(horizontal = 1f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightIcon(
            icon = if (checked) LightIcons.SELECT_ON else LightIcons.SELECT_OFF,
            size = 1.6f,
            contentDescription = if (checked) "Selected" else "Not selected",
        )
        Box(Modifier.size(0.75f.gridUnitsAsDp()))
        LightText(
            label,
            variant = LightTextVariant.Copy,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/* -------------------------------------------------------------- People screen */

class PeopleScreen(
    sealedActivity: SealedLightActivity,
    private val repository: TipRepository,
    private val receiptId: String,
) : LightScreen<Unit, ReceiptViewModel>(sealedActivity) {

    override val viewModelClass: Class<ReceiptViewModel> = ReceiptViewModel::class.java
    override fun createViewModel() = ReceiptViewModel(repository, receiptId)

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()

        LightTheme(colors = colors) {
            Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("People"),
                    rightButton = LightBarButton.LightIcon(
                        icon = LightIcons.ADD,
                        onClick = {
                            navigateTo(
                                { NameEntryScreen(it, "Add person", "") },
                                resultCallback = { name -> viewModel.addPerson(name) },
                            )
                        },
                        contentDescription = "Add person",
                    ),
                )
                Box(Modifier.weight(1f)) {
                    if (state.people.isEmpty()) {
                        CenterMessage("Nobody added yet.\nTap + to add the first person.")
                    } else {
                        LightLazyScrollView(uniformItemHeightGridUnits = ROW_UNITS) {
                            items(state.people, key = { it.id }) { person ->
                                MenuRow(
                                    label = person.name,
                                    detail = "edit",
                                    heightUnits = ROW_UNITS,
                                    onClick = {
                                        navigateTo({
                                            PersonEditScreen(it, repository, receiptId, person.id)
                                        })
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class PersonEditScreen(
    sealedActivity: SealedLightActivity,
    private val repository: TipRepository,
    private val receiptId: String,
    private val personId: String,
) : LightScreen<Unit, ReceiptViewModel>(sealedActivity) {

    override val viewModelClass: Class<ReceiptViewModel> = ReceiptViewModel::class.java
    override fun createViewModel() = ReceiptViewModel(repository, receiptId)

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()
        val person = state.people.firstOrNull { it.id == personId }

        LightTheme(colors = colors) {
            Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text(person?.name ?: "Person"),
                )
                if (person != null) {
                    MenuRow("Rename") {
                        navigateTo(
                            { NameEntryScreen(it, "Rename", person.name) },
                            resultCallback = { name -> viewModel.renamePerson(person, name) },
                        )
                    }
                    MenuRow("Remove from this bill") {
                        viewModel.deletePerson(person)
                        goBack()
                    }
                }
            }
        }
    }
}

/** Name entry on the LP3 keyboard. Returns the trimmed name to the caller. */
class NameEntryScreen(
    sealedActivity: SealedLightActivity,
    private val title: String,
    private val initial: String,
) : LightScreen<String, NameEntryViewModel>(sealedActivity) {

    override val viewModelClass: Class<NameEntryViewModel> = NameEntryViewModel::class.java
    override fun createViewModel() = NameEntryViewModel(initial)

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val session by viewModel.session.collectAsState()
        val input = key(session) { rememberTextFieldState(initial) }
        val keyboard = rememberKeyboardOptions()

        LightTheme(colors = colors) {
            LightTextInputEditor(
                title = title,
                editorKey = session,
                keyboardOptionsFlow = keyboard,
                state = input,
                singleLine = true,
                onSubmit = { raw ->
                    val name = raw.toString().trim()
                    if (name.isNotBlank()) goBack(name) else goBack()
                },
                onBack = { goBack() },
                submitIcon = LightIcons.ACCEPT,
                showBackButton = true,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/* ------------------------------------------------------------- Receipt menu */

class ReceiptMenuScreen(
    sealedActivity: SealedLightActivity,
    private val repository: TipRepository,
    private val receiptId: String,
) : LightScreen<Unit, ReceiptViewModel>(sealedActivity) {

    override val viewModelClass: Class<ReceiptViewModel> = ReceiptViewModel::class.java
    override fun createViewModel() = ReceiptViewModel(repository, receiptId)

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()

        LightTheme(colors = colors) {
            Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("Receipt"),
                )
                MenuRow("Read again with Claude") {
                    viewModel.rescan()
                    goBack()
                }
                MenuRow("Clear everyone's picks") {
                    viewModel.clearAssignments()
                    goBack()
                }
                state.receipt?.let { receipt ->
                    MenuRow("Delete this receipt") {
                        viewModel.deleteReceipt(receipt)
                        goBack()
                        goBack()
                    }
                }
            }
        }
    }
}

/* -------------------------------------------------------------- Totals screen */

class TotalsScreen(
    sealedActivity: SealedLightActivity,
    private val repository: TipRepository,
    private val receiptId: String,
) : LightScreen<Unit, ReceiptViewModel>(sealedActivity) {

    override val viewModelClass: Class<ReceiptViewModel> = ReceiptViewModel::class.java
    override fun createViewModel() = ReceiptViewModel(repository, receiptId)

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()
        val split = state.split
        val tipPercent = state.receipt?.tipPercent ?: DEFAULT_TIP_PERCENT

        LightTheme(colors = colors) {
            Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.TwoLineDetail(
                        line1 = "Totals",
                        line2 = "$tipPercent% tip, shared by spend",
                    ),
                )
                Hairline()
                LightLazyScrollView(uniformItemHeightGridUnits = ROW_UNITS) {
                    items(split.shares, key = { it.person.id }) { share -> ShareRow(share) }
                }
                Hairline()
                SummaryLine("Items", split.itemsSubtotalCents)
                SummaryLine("Tax", split.taxCents)
                SummaryLine("Tip ($tipPercent%)", split.tipCents)
                SummaryLine("Bill total", split.grandTotalCents, emphasis = true)
                if (!split.allAssigned) {
                    SummaryLine(
                        "${split.unassignedCount} unassigned",
                        split.unassignedCents,
                        lighten = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareRow(share: PersonShare) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_UNITS.gridUnitsAsDp())
            .padding(horizontal = 1f.gridUnitsAsDp()),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LightText(
                share.person.name,
                variant = LightTextVariant.Copy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            LightText(share.totalCents.asMoney(), variant = LightTextVariant.Subheading)
        }
        LightText(
            "${share.itemsCents.asMoney()} items · ${share.taxCents.asMoney()} tax · " +
                "${share.tipCents.asMoney()} tip",
            variant = LightTextVariant.Detail,
            lighten = true,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SummaryLine(
    label: String,
    cents: Long,
    emphasis: Boolean = false,
    lighten: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.8f.gridUnitsAsDp())
            .padding(horizontal = 1f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(
            label,
            variant = if (emphasis) LightTextVariant.Copy else LightTextVariant.Detail,
            lighten = lighten,
            modifier = Modifier.weight(1f),
        )
        LightText(
            cents.asMoney(),
            variant = if (emphasis) LightTextVariant.Copy else LightTextVariant.Detail,
            lighten = lighten,
        )
    }
}
