package com.lighttip.calc

import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val TIP_PRESETS = listOf(10, 15, 18, 20, 22)
const val DEFAULT_TIP_PERCENT = 20

/** No-state view model for screens that only navigate (camera, album, QR, choosers). */
class EmptyViewModel : LightViewModel<Unit>()

data class TipUiState(
    val amountCents: Long = 0L,
    val tipPercent: Int = DEFAULT_TIP_PERCENT,
    val isCustomPercent: Boolean = false,
) {
    val tipCents: Long get() = tipCentsFor(amountCents, tipPercent)
    val totalCents: Long get() = amountCents + tipCents
}

class TipViewModel(
    val repository: TipRepository,
    private val database: TipDatabase,
) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow(TipUiState())
    val state: StateFlow<TipUiState> = _state.asStateFlow()

    /** Digits push in from the right, capped so the display cannot overflow its row. */
    fun pushDigit(digit: Int) = _state.update {
        val next = it.amountCents * 10 + digit
        if (next > 99_999_999L) it else it.copy(amountCents = next)
    }

    fun backspace() = _state.update { it.copy(amountCents = it.amountCents / 10) }

    fun clear() = _state.update { it.copy(amountCents = 0L) }

    fun selectPreset(percent: Int) =
        _state.update { it.copy(tipPercent = percent, isCustomPercent = false) }

    fun setCustomPercent(percent: Int) = _state.update {
        val clamped = percent.coerceIn(0, 100)
        it.copy(tipPercent = clamped, isCustomPercent = clamped !in TIP_PRESETS)
    }

    override fun onCleared() {
        repository.close()
        database.close()
        super.onCleared()
    }
}

/** Standalone percentage keypad, reused for the custom tip on both tabs. */
class PercentViewModel(initial: Int) : LightViewModel<Int>() {
    private val _percent = MutableStateFlow(initial.coerceIn(0, 100))
    val percent: StateFlow<Int> = _percent.asStateFlow()

    fun pushDigit(digit: Int) {
        val next = _percent.value * 10 + digit
        if (next <= 100) _percent.value = next
    }

    fun backspace() {
        _percent.value = _percent.value / 10
    }

    fun clear() {
        _percent.value = 0
    }
}

class SplitListViewModel(private val repository: TipRepository) : LightViewModel<Unit>() {
    val receipts: StateFlow<List<ReceiptEntity>> = repository.observeReceipts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(receipt: ReceiptEntity) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteReceipt(receipt) }
    }

    fun rescan(receiptId: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.rescan(receiptId) }
    }
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

class ReceiptViewModel(
    val repository: TipRepository,
    val receiptId: String,
) : LightViewModel<Unit>() {

    val state: StateFlow<ReceiptUiState> = combine(
        repository.observeReceipt(receiptId),
        repository.observeItems(receiptId),
        repository.observePeople(receiptId),
        repository.observeAssignments(receiptId),
    ) { receipt, items, people, assignments ->
        ReceiptUiState(receipt, items, people, assignments)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReceiptUiState())

    fun setTipPercent(percent: Int) {
        viewModelScope.launch(Dispatchers.IO) { repository.setTipPercent(receiptId, percent) }
    }

    fun toggle(itemId: String, personId: String, assigned: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setAssigned(receiptId, itemId, personId, assigned)
        }
    }

    fun addPerson(name: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.addPerson(receiptId, name) }
    }

    fun renamePerson(person: PersonEntity, name: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.renamePerson(person, name) }
    }

    fun deletePerson(person: PersonEntity) {
        viewModelScope.launch(Dispatchers.IO) { repository.deletePerson(person) }
    }

    fun rescan() {
        viewModelScope.launch(Dispatchers.IO) { repository.rescan(receiptId) }
    }

    fun clearAssignments() {
        viewModelScope.launch(Dispatchers.IO) { repository.clearAssignments(receiptId) }
    }

    fun deleteReceipt(receipt: ReceiptEntity) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteReceipt(receipt) }
    }
}

data class TextEntryUiState(
    val draft: String = "",
    val inputSession: Int = 0,
)

/** Backs the LP3 keyboard screens: API key entry and person names. */
class TextEntryViewModel(
    private val load: suspend () -> String,
    private val save: suspend (String) -> Unit,
) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow(TextEntryUiState())
    val state: StateFlow<TextEntryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = TextEntryUiState(draft = load(), inputSession = 1)
        }
    }

    fun submit(value: String, onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { save(value) }
            onDone()
        }
    }
}

/** Tracks a single keyboard session so the editor re-seeds when the initial text changes. */
class NameEntryViewModel(initial: String) : LightViewModel<String>() {
    private val _session = MutableStateFlow(if (initial.isBlank()) 0 else 1)
    val session: StateFlow<Int> = _session.asStateFlow()
}
