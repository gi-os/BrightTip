package com.gios.lighttip.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gios.lighttip.data.AssignmentEntity
import com.gios.lighttip.data.ItemEntity
import com.gios.lighttip.data.PersonEntity
import com.gios.lighttip.data.ReceiptEntity
import com.gios.lighttip.data.TipRepository
import com.gios.lighttip.util.SplitResult
import com.gios.lighttip.util.computeSplit
import com.gios.lighttip.util.tipCentsFor
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

    /* ---- Tip tab ---- */

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

    /* ---- Split tab ---- */

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
