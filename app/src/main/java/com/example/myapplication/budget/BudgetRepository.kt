package com.example.myapplication.budget

import android.content.Context
import androidx.core.content.edit
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

class BudgetRepository private constructor(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val stateFlow = MutableStateFlow(applyDueFixedExpenses(loadState()))

    val state: StateFlow<BudgetState> = stateFlow

    fun refreshDueExpenses() {
        val current = stateFlow.value
        val processed = applyDueFixedExpenses(current)
        if (processed != current) {
            updateState(processed)
        }
    }

    fun addIncome(amount: BigDecimal) {
        if (amount <= BigDecimal.ZERO) return
        val current = ensureUpToDate()
        val normalizedAmount = amount.normalize()
        val newBalance = current.totalBalance.add(normalizedAmount).normalize()
        val updatedCategories = distributeToCategories(current.categories, normalizedAmount)
        updateState(current.copy(totalBalance = newBalance, categories = updatedCategories))
    }

    fun addFixedExpense(name: String, amount: BigDecimal, dueDate: LocalDate) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank() || amount <= BigDecimal.ZERO) return
        val current = ensureUpToDate()
        val newExpense = FixedExpense(
            name = trimmedName,
            amount = amount.normalize(),
            nextDueDate = dueDate
        )
        val updatedExpenses = current.fixedExpenses + newExpense
        updateState(current.copy(fixedExpenses = updatedExpenses))
        refreshDueExpenses()
    }

    fun updateFixedExpenseDueDate(id: String, dueDate: LocalDate) {
        val current = ensureUpToDate()
        val updatedExpenses = current.fixedExpenses.map { expense ->
            if (expense.id == id) expense.copy(nextDueDate = dueDate) else expense
        }
        updateState(current.copy(fixedExpenses = updatedExpenses))
        refreshDueExpenses()
    }

    fun removeFixedExpense(id: String) {
        val current = ensureUpToDate()
        val updatedExpenses = current.fixedExpenses.filterNot { it.id == id }
        updateState(current.copy(fixedExpenses = updatedExpenses))
    }

    fun addCategory(name: String, percentage: Int): Boolean {
        val trimmedName = name.trim()
        if (trimmedName.isBlank() || percentage <= 0) return false
        val current = ensureUpToDate()
        val totalPercentage = current.categories.sumOf { it.percentage } + percentage
        if (totalPercentage > 100) {
            return false
        }
        val newCategory = BudgetCategory(
            name = trimmedName,
            percentage = percentage,
            allocatedAmount = BigDecimal.ZERO,
            remainingAmount = BigDecimal.ZERO
        )
        updateState(current.copy(categories = current.categories + newCategory))
        return true
    }

    fun updateCategoryPercentage(id: String, newPercentage: Int): Boolean {
        if (newPercentage <= 0) return false
        val current = ensureUpToDate()
        val otherTotal = current.categories.filterNot { it.id == id }.sumOf { it.percentage }
        if (otherTotal + newPercentage > 100) return false
        val updated = current.categories.map { category ->
            if (category.id == id) category.copy(percentage = newPercentage) else category
        }
        updateState(current.copy(categories = updated))
        return true
    }

    fun removeCategory(id: String) {
        val current = ensureUpToDate()
        updateState(current.copy(categories = current.categories.filterNot { it.id == id }))
    }

    fun recordCategorySpend(categoryId: String, amount: BigDecimal): Boolean {
        if (amount <= BigDecimal.ZERO) return false
        val current = ensureUpToDate()
        var success = false
        val updatedCategories = current.categories.map { category ->
            if (category.id == categoryId) {
                val newRemaining = category.remainingAmount.subtract(amount)
                if (newRemaining < BigDecimal.ZERO) {
                    return false
                }
                success = true
                category.copy(remainingAmount = newRemaining.normalize())
            } else {
                category
            }
        }
        if (!success) return false
        val newBalance = current.totalBalance.subtract(amount).normalize()
        updateState(current.copy(totalBalance = newBalance, categories = updatedCategories))
        return true
    }

    private fun ensureUpToDate(): BudgetState {
        val current = stateFlow.value
        val processed = applyDueFixedExpenses(current)
        return if (processed != current) {
            stateFlow.value = processed
            persistState(processed)
            processed
        } else {
            current
        }
    }

    private fun distributeToCategories(categories: List<BudgetCategory>, amount: BigDecimal): List<BudgetCategory> {
        if (categories.isEmpty() || amount <= BigDecimal.ZERO) return categories
        val totalPercentage = categories.sumOf { it.percentage }
        if (totalPercentage <= 0) return categories
        val normalizedPool = amount.normalize()
        var remainder = normalizedPool
        return categories.mapIndexed { index, category ->
            val share = if (index == categories.lastIndex) {
                remainder
            } else {
                val calculated = calculateShare(normalizedPool, category.percentage, totalPercentage)
                remainder = remainder.subtract(calculated).normalize()
                calculated
            }
            category.copy(
                allocatedAmount = category.allocatedAmount.add(share).normalize(),
                remainingAmount = category.remainingAmount.add(share).normalize()
            )
        }
    }

    private fun calculateShare(pool: BigDecimal, percentage: Int, denominator: Int): BigDecimal {
        if (pool <= BigDecimal.ZERO || percentage <= 0 || denominator <= 0) return BigDecimal.ZERO
        val ratio = BigDecimal(percentage)
            .divide(BigDecimal(denominator), SCALE, RoundingMode.HALF_UP)
        return pool.multiply(ratio).setScale(2, RoundingMode.HALF_UP)
    }

    private fun applyDueFixedExpenses(state: BudgetState): BudgetState {
        if (state.fixedExpenses.isEmpty()) return state
        val today = LocalDate.now()
        var balance = state.totalBalance
        var changed = false
        val updatedExpenses = state.fixedExpenses.map { expense ->
            var nextDue = expense.nextDueDate
            var expenseApplied = false
            while (!nextDue.isAfter(today)) {
                balance = balance.subtract(expense.amount).normalize()
                nextDue = nextDue.plusMonths(1)
                expenseApplied = true
            }
            if (expenseApplied) {
                changed = true
                expense.copy(nextDueDate = nextDue)
            } else {
                expense
            }
        }
        return if (changed) {
            state.copy(totalBalance = balance.normalize(), fixedExpenses = updatedExpenses)
        } else {
            state
        }
    }

    private fun updateState(newState: BudgetState) {
        stateFlow.value = newState
        persistState(newState)
    }

    private fun persistState(state: BudgetState) {
        val json = JSONObject().apply {
            put(KEY_TOTAL_BALANCE, state.totalBalance.toPlainString())
            put(KEY_FIXED_EXPENSES, JSONArray().apply {
                state.fixedExpenses.forEach { expense ->
                    put(
                        JSONObject().apply {
                            put(KEY_ID, expense.id)
                            put(KEY_NAME, expense.name)
                            put(KEY_AMOUNT, expense.amount.toPlainString())
                            put(KEY_DUE_DATE, expense.nextDueDate.format(DATE_FORMATTER))
                        }
                    )
                }
            })
            put(KEY_CATEGORIES, JSONArray().apply {
                state.categories.forEach { category ->
                    put(
                        JSONObject().apply {
                            put(KEY_ID, category.id)
                            put(KEY_NAME, category.name)
                            put(KEY_PERCENTAGE, category.percentage)
                            put(KEY_ALLOCATED, category.allocatedAmount.toPlainString())
                            put(KEY_REMAINING, category.remainingAmount.toPlainString())
                        }
                    )
                }
            })
        }
        preferences.edit {
            putString(KEY_STATE_JSON, json.toString())
        }
    }

    private fun loadState(): BudgetState {
        val raw = preferences.getString(KEY_STATE_JSON, null) ?: return BudgetState()
        return try {
            val json = JSONObject(raw)
            val totalBalance = json.optString(KEY_TOTAL_BALANCE, "0").toBigDecimalOrZero()
            val fixedExpenses = json.optJSONArray(KEY_FIXED_EXPENSES)?.let { array ->
                buildList {
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val dueDate = item.optString(KEY_DUE_DATE, null)?.let { storedDate ->
                            runCatching { LocalDate.parse(storedDate, DATE_FORMATTER) }.getOrNull()
                        } ?: LocalDate.now()
                        add(
                            FixedExpense(
                                id = item.optString(KEY_ID, UUID.randomUUID().toString()),
                                name = item.optString(KEY_NAME),
                                amount = item.optString(KEY_AMOUNT, "0").toBigDecimalOrZero(),
                                nextDueDate = dueDate
                            )
                        )
                    }
                }
            } ?: emptyList()
            val categories = json.optJSONArray(KEY_CATEGORIES)?.let { array ->
                buildList {
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        add(
                            BudgetCategory(
                                id = item.optString(KEY_ID, UUID.randomUUID().toString()),
                                name = item.optString(KEY_NAME),
                                percentage = item.optInt(KEY_PERCENTAGE),
                                allocatedAmount = item.optString(KEY_ALLOCATED, "0").toBigDecimalOrZero(),
                                remainingAmount = item.optString(KEY_REMAINING, "0").toBigDecimalOrZero()
                            )
                        )
                    }
                }
            } ?: emptyList()
            BudgetState(
                totalBalance = totalBalance,
                fixedExpenses = fixedExpenses,
                categories = categories
            )
        } catch (t: Throwable) {
            BudgetState()
        }
    }

    companion object {
        private const val PREFS_NAME = "budget_prefs"
        private const val KEY_STATE_JSON = "state_json"
        private const val KEY_TOTAL_BALANCE = "total_balance"
        private const val KEY_FIXED_EXPENSES = "fixed_expenses"
        private const val KEY_CATEGORIES = "categories"
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_AMOUNT = "amount"
        private const val KEY_DUE_DATE = "due_date"
        private const val KEY_PERCENTAGE = "percentage"
        private const val KEY_ALLOCATED = "allocated"
        private const val KEY_REMAINING = "remaining"
        private const val SCALE = 6
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        @Volatile
        private var INSTANCE: BudgetRepository? = null

        fun getInstance(context: Context): BudgetRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BudgetRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

private fun String.toBigDecimalOrZero(): BigDecimal = try {
    BigDecimal(this).setScale(2, RoundingMode.HALF_UP)
} catch (e: NumberFormatException) {
    BigDecimal.ZERO
}

private fun BigDecimal.normalize(): BigDecimal = this.setScale(2, RoundingMode.HALF_UP)
