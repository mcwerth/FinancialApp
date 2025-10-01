package com.example.myapplication.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.time.LocalDate
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
            val totalAllocated = state.categories.fold(BigDecimal.ZERO) { acc, category ->
                acc.add(category.allocatedAmount)
            }
            val totalRemaining = state.categories.fold(BigDecimal.ZERO) { acc, category ->
                acc.add(category.remainingAmount)
            }
            BudgetUiState(
                currentBalance = state.totalBalance,
                totalAllocated = totalAllocated,
                totalRemaining = totalRemaining,
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

    fun refresh() {
        repository.refreshDueExpenses()
    }

    fun addIncome(amount: BigDecimal) {
        if (amount <= BigDecimal.ZERO) return
        repository.addIncome(amount)
        emitEvent(BudgetEvent.IncomeRecorded)
    }

    fun addFixedExpense(name: String, amount: BigDecimal, dueDate: LocalDate) {
        repository.addFixedExpense(name, amount, dueDate)
        emitEvent(BudgetEvent.FixedExpenseAdded)
    }

    fun updateFixedExpenseDueDate(id: String, dueDate: LocalDate) {
        repository.updateFixedExpenseDueDate(id, dueDate)
        emitEvent(BudgetEvent.FixedExpenseUpdated)
    }

    fun removeFixedExpense(id: String) {
        repository.removeFixedExpense(id)
        emitEvent(BudgetEvent.FixedExpenseRemoved)
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
        } else {
            emitEvent(BudgetEvent.CategoryUpdated)
        }
    }

    fun removeCategory(id: String) {
        repository.removeCategory(id)
        emitEvent(BudgetEvent.CategoryRemoved)
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
    val currentBalance: BigDecimal = BigDecimal.ZERO,
    val totalAllocated: BigDecimal = BigDecimal.ZERO,
    val totalRemaining: BigDecimal = BigDecimal.ZERO,
    val categories: List<BudgetCategory> = emptyList(),
    val fixedExpenses: List<FixedExpense> = emptyList()
)

sealed interface BudgetEvent {
    data object IncomeRecorded : BudgetEvent
    data object FixedExpenseAdded : BudgetEvent
    data object FixedExpenseUpdated : BudgetEvent
    data object FixedExpenseRemoved : BudgetEvent
    data object CategoryAdded : BudgetEvent
    data object CategoryUpdated : BudgetEvent
    data object CategoryRemoved : BudgetEvent
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
