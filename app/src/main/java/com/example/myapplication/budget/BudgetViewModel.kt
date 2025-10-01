package com.example.myapplication.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BudgetViewModel(private val repository: BudgetRepository) : ViewModel() {
    val uiState: StateFlow<BudgetUiState> = repository.state
        .map { state ->
            BudgetUiState(
                totalIncome = state.totalIncome,
                totalFixedExpenses = state.totalFixedExpenses,
                availableForAllocation = state.availableForAllocation,
                categories = state.categories,
                fixedExpenses = state.fixedExpenses
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BudgetUiState()
        )

    private val _events = MutableSharedFlow<BudgetEvent>()
    val events: SharedFlow<BudgetEvent> = _events

    fun addIncome(amount: BigDecimal, type: IncomeType) {
        if (amount <= BigDecimal.ZERO) return
        repository.addIncome(amount, type)
        emitEvent(BudgetEvent.IncomeRecorded)
    }

    fun addFixedExpense(name: String, amount: BigDecimal) {
        repository.addFixedExpense(name, amount)
        emitEvent(BudgetEvent.FixedExpenseAdded)
    }

    fun removeFixedExpense(id: String) {
        repository.removeFixedExpense(id)
    }

    fun addCategory(name: String, percentage: Int): Boolean {
        val added = repository.addCategory(name, percentage)
        if (!added) {
            emitEvent(BudgetEvent.InvalidCategoryPercentage)
        } else {
            emitEvent(BudgetEvent.CategoryAdded)
        }
        return added
    }

    fun updateCategoryPercentage(id: String, newPercentage: Int) {
        val updated = repository.updateCategoryPercentage(id, newPercentage)
        if (!updated) {
            emitEvent(BudgetEvent.InvalidCategoryPercentage)
        }
    }

    fun removeCategory(id: String) {
        repository.removeCategory(id)
    }

    fun recordCategorySpend(id: String, amount: BigDecimal) {
        val success = repository.recordCategorySpend(id, amount)
        if (success) {
            emitEvent(BudgetEvent.SpendRecorded)
        } else {
            emitEvent(BudgetEvent.InvalidSpend)
        }
    }

    private fun emitEvent(event: BudgetEvent) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }
}

data class BudgetUiState(
    val totalIncome: BigDecimal = BigDecimal.ZERO,
    val totalFixedExpenses: BigDecimal = BigDecimal.ZERO,
    val availableForAllocation: BigDecimal = BigDecimal.ZERO,
    val categories: List<BudgetCategory> = emptyList(),
    val fixedExpenses: List<FixedExpense> = emptyList()
)

sealed interface BudgetEvent {
    data object IncomeRecorded : BudgetEvent
    data object FixedExpenseAdded : BudgetEvent
    data object CategoryAdded : BudgetEvent
    data object InvalidCategoryPercentage : BudgetEvent
    data object SpendRecorded : BudgetEvent
    data object InvalidSpend : BudgetEvent
}

class BudgetViewModelFactory(private val repository: BudgetRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BudgetViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BudgetViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
