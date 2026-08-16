package com.gios.lighttip.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gios.lighttip.data.AssignmentEntity
import com.gios.lighttip.data.ItemEntity
import com.gios.lighttip.data.PersonEntity
import com.gios.lighttip.data.ReceiptEntity
import com.gios.lighttip.data.RateRepository
import com.gios.lighttip.data.TipRepository
import com.gios.lighttip.data.convertMinor
import com.gios.lighttip.data.unitRateText
import com.gios.lighttip.util.CalcState
import com.gios.lighttip.util.SplitResult
import com.gios.lighttip.util.backspace
import com.gios.lighttip.util.clear
import com.gios.lighttip.util.computeSplit
import com.gios.lighttip.util.decimal
import com.gios.lighttip.util.digit
import com.gios.lighttip.util.equals
import com.gios.lighttip.util.negate
import com.gios.lighttip.util.operator
import com.gios.lighttip.util.percent
import com.gios.lighttip.util.tipCentsFor
import com.gios.lighttip.util.toMoneyCents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

val TIP_PRESETS = listOf(10, 15, 18, 20, 22)
const val DEFAULT_TIP_PERCENT = 20

private const val ONE_DAY_MILLIS = 24L * 60 * 60 * 1000
/** Past three days the rate has visibly moved on most pairs, so the label starts shouting. */
private const val STALE_AFTER_MILLIS = 3 * ONE_DAY_MILLIS

data class TipUiState(
    val amountCents: Long = 0L,
    val tipPercent: Int = DEFAULT_TIP_PERCENT,
    val isCustomPercent: Boolean = false,
) {
    val tipCents: Long get() = tipCentsFor(amountCents, tipPercent)
    val totalCents: Long get() = amountCents + tipCents
}

data class ReceiptUiState(
    val receipt: ReceiptEntity? = null,
    val items: List<ItemEntity> = emptyList(),
    val people: List<PersonEntity> = emptyList(),
    val assignments: List<AssignmentEntity> = emptyList(),
) {
    val split: SplitResult
        get() = computeSplit(
            people = people,
            items = items,
            assignments = assignments,
            taxCents = receipt?.taxCents ?: 0L,
            tipPercent = receipt?.tipPercent ?: DEFAULT_TIP_PERCENT,
        )

    fun peopleOn(itemId: String): List<PersonEntity> {
        val ids = assignments.filter { it.itemId == itemId }.map { it.personId }.toSet()
        return people.filter { it.id in ids }
    }
}

class TipViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TipRepository(app)
    private val rates = RateRepository(app)

    /* ---- Mode ---- */

    private val _mode = MutableStateFlow(CalcMode.ofOrdinal(repo.lastMode()))
    val mode: StateFlow<CalcMode> = _mode.asStateFlow()

    fun setMode(next: CalcMode) {
        _mode.value = next
        repo.setLastMode(next.ordinal)
        // Opening the converter is the moment to try for fresh rates: it is the only
        // point where we know the person is about to read one, and doing it here means
        // the common case never needs the refresh button at all.
        if (next == CalcMode.Currency) refreshRates(force = false)
    }

    /* ---- Calculator mode ---- */

    private val _calc = MutableStateFlow(CalcState())
    val calc: StateFlow<CalcState> = _calc.asStateFlow()

    fun onCalcKey(key: CalcKey) = _calc.update { state ->
        when (key) {
            is CalcKey.Digit -> state.digit(key.value)
            is CalcKey.Operator -> state.operator(key.op)
            CalcKey.Decimal -> state.decimal()
            CalcKey.Equals -> state.equals()
            CalcKey.Clear -> state.clear()
            CalcKey.Backspace -> state.backspace()
            CalcKey.Percent -> state.percent()
            CalcKey.Negate -> state.negate()
        }
    }

    /**
     * Hand whatever is on the calculator to the tip screen as the bill and go there. The
     * value crosses as cents, so the two screens never disagree about the amount by a
     * rounding step.
     */
    fun calcToTip() {
        val cents = _calc.value.value.toMoneyCents()
        _tip.update { it.copy(amountCents = cents) }
        setMode(CalcMode.Tip)
    }

    /* ---- Currency mode ---- */

    private val _currency = MutableStateFlow(
        rates.lastPair().let { (from, to) -> CurrencyUiState(from = from, to = to) },
    )
    val currency: StateFlow<CurrencyUiState> = _currency.asStateFlow()

    /**
     * Declared above the `init` that fills it, and it has to stay there: Kotlin runs
     * property initialisers and init blocks in source order, so a `var x = null` sitting
     * below the block would run *after* it and quietly wipe the loaded table.
     */
    private var rateTable: RateRepository.RateTable? = null

    init {
        // Paint whatever is cached immediately; the network, if there is any, catches up.
        applyRates(rates.cached())
        if (_mode.value == CalcMode.Currency) refreshRates(force = false)
    }

    /** Digits push in from the right here too, in the minor units of the source currency. */
    fun currencyDigit(digit: Int) = updateCurrency {
        val next = it.amountMinor * 10 + digit
        if (next > 99_999_999_99L) it else it.copy(amountMinor = next)
    }

    fun currencyBackspace() = updateCurrency { it.copy(amountMinor = it.amountMinor / 10) }

    fun currencyClear() = updateCurrency { it.copy(amountMinor = 0L) }

    fun currencySwap() = updateCurrency { state ->
        // Swap carries the converted figure up into the entry, so hitting swap twice is
        // not a way to lose the number you just worked out.
        val carried = state.convertedMinor ?: state.amountMinor
        rates.setLastPair(state.to, state.from)
        state.copy(from = state.to, to = state.from, amountMinor = carried)
    }

    fun currencyPick(slot: CurrencySlot, code: String) = updateCurrency { state ->
        val next = when (slot) {
            CurrencySlot.From -> state.copy(from = code)
            CurrencySlot.To -> state.copy(to = code)
        }
        rates.setLastPair(next.from, next.to)
        next
    }

    fun refreshRates(force: Boolean = true) {
        if (_currency.value.refreshing) return
        val cached = rates.cached()
        // A table fetched today is today's table — the source only publishes daily, so
        // re-fetching on every visit would spend the radio for an identical answer.
        if (!force && cached != null && ageMillis(cached.fetchedAtMillis) < ONE_DAY_MILLIS) return
        _currency.update { it.copy(refreshing = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val fetched = rates.fetch()
            _currency.update { it.copy(refreshing = false) }
            if (fetched != null) applyRates(fetched)
        }
    }

    private fun applyRates(table: RateRepository.RateTable?) {
        rateTable = table
        updateCurrency { it }
    }

    /**
     * One place computes the derived half of the currency state — converted amount, rate
     * line, age — so no caller can mutate the pair and forget to recompute the figure
     * underneath it.
     */
    private fun updateCurrency(edit: (CurrencyUiState) -> CurrencyUiState) = _currency.update {
        val base = edit(it)
        val table = rateTable
        val map = table?.rates.orEmpty()
        base.copy(
            convertedMinor = convertMinor(base.amountMinor, base.from, base.to, map),
            unitRate = unitRateText(base.from, base.to, map),
            codes = map.keys.sorted(),
            ageLabel = ageLabelFor(table),
            stale = table != null && ageMillis(table.fetchedAtMillis) >= STALE_AFTER_MILLIS,
        )
    }

    private fun ageLabelFor(table: RateRepository.RateTable?): String {
        if (table == null) return "No rates yet — tap REFRESH once you have signal"
        val days = ageMillis(table.fetchedAtMillis) / ONE_DAY_MILLIS
        return when {
            days <= 0L -> "Rates from today"
            days == 1L -> "Rates from yesterday"
            else -> "Rates are $days days old"
        }
    }

    private fun ageMillis(then: Long): Long =
        (System.currentTimeMillis() - then).coerceAtLeast(0L)

    /* ---- Tip mode ---- */

    private val _tip = MutableStateFlow(TipUiState())
    val tip: StateFlow<TipUiState> = _tip.asStateFlow()

    /** Digits push in from the right, capped so the readout cannot overflow its row. */
    fun pushDigit(digit: Int) = _tip.update {
        val next = it.amountCents * 10 + digit
        if (next > 99_999_999L) it else it.copy(amountCents = next)
    }

    fun backspace() = _tip.update { it.copy(amountCents = it.amountCents / 10) }

    fun clearAmount() = _tip.update { it.copy(amountCents = 0L) }

    fun setTipPercent(percent: Int) = _tip.update {
        val clamped = percent.coerceIn(0, 100)
        it.copy(tipPercent = clamped, isCustomPercent = clamped !in TIP_PRESETS)
    }

    /* ---- Receipt mode ---- */

    val receipts: StateFlow<List<ReceiptEntity>> = repo.observeReceipts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _apiKey = MutableStateFlow(repo.getApiKey())
    val apiKeyState: StateFlow<String> = _apiKey.asStateFlow()

    fun setApiKey(key: String) {
        repo.setApiKey(key)
        _apiKey.value = repo.getApiKey()
    }

    fun recentNames(): List<String> = repo.recentNames()

    fun newCaptureFile(): File = repo.newCaptureFile()

    fun addFromFile(file: File) {
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repo.addFromFile(file) }
            _busy.value = false
        }
    }

    fun receiptState(receiptId: String): StateFlow<ReceiptUiState> = combine(
        repo.observeReceipt(receiptId),
        repo.observeItems(receiptId),
        repo.observePeople(receiptId),
        repo.observeAssignments(receiptId),
    ) { receipt, items, people, assignments ->
        ReceiptUiState(receipt, items, people, assignments)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReceiptUiState())

    fun setReceiptTip(receiptId: String, percent: Int) =
        viewModelScope.launch(Dispatchers.IO) { repo.setTipPercent(receiptId, percent) }

    fun toggleAssignment(receiptId: String, itemId: String, personId: String, assigned: Boolean) =
        viewModelScope.launch(Dispatchers.IO) {
            repo.setAssigned(receiptId, itemId, personId, assigned)
        }

    fun addPerson(receiptId: String, name: String) =
        viewModelScope.launch(Dispatchers.IO) { repo.addPerson(receiptId, name) }

    fun renamePerson(person: PersonEntity, name: String) =
        viewModelScope.launch(Dispatchers.IO) { repo.renamePerson(person, name) }

    fun deletePerson(person: PersonEntity) =
        viewModelScope.launch(Dispatchers.IO) { repo.deletePerson(person) }

    fun clearAssignments(receiptId: String) =
        viewModelScope.launch(Dispatchers.IO) { repo.clearAssignments(receiptId) }

    fun rescan(receiptId: String) {
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repo.rescan(receiptId) }
            _busy.value = false
        }
    }

    fun deleteReceipt(receipt: ReceiptEntity) =
        viewModelScope.launch(Dispatchers.IO) { repo.deleteReceipt(receipt) }
}
