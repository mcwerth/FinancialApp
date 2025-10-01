package com.example.myapplication.budget

import android.content.Context
import androidx.core.content.edit
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

class BudgetRepository private constructor(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val stateFlow = MutableStateFlow(loadState())

    val state: StateFlow<BudgetState> = stateFlow

    fun addIncome(amount: BigDecimal, incomeType: IncomeType) {
        if (amount <= BigDecimal.ZERO) return
        val current = stateFlow.value
        val newTotal = current.totalIncome.add(amount)
        val fixedExpenses = current.fixedExpenses
        val updatedCategories = when (incomeType) {
            IncomeType.PAYCHECK -> resetCategoriesForIncome(newTotal, fixedExpenses, current.categories)
            IncomeType.SUPPLEMENTAL -> applySupplementalIncome(amount, current.categories)
        }
        updateState(
            current.copy(
                totalIncome = newTotal,
                categories = updatedCategories,
                lastIncomeType = incomeType
            )
        )
    }

    fun addFixedExpense(name: String, amount: BigDecimal) {
        if (name.isBlank() || amount <= BigDecimal.ZERO) return
        val current = stateFlow.value
        val updatedExpenses = current.fixedExpenses + FixedExpense(name = name.trim(), amount = amount)
        val refreshedCategories = resetCategoriesForIncome(current.totalIncome, updatedExpenses, current.categories)
        updateState(current.copy(fixedExpenses = updatedExpenses, categories = refreshedCategories))
    }

    fun removeFixedExpense(id: String) {
        val current = stateFlow.value
        val updatedExpenses = current.fixedExpenses.filterNot { it.id == id }
        val refreshedCategories = resetCategoriesForIncome(current.totalIncome, updatedExpenses, current.categories)
        updateState(current.copy(fixedExpenses = updatedExpenses, categories = refreshedCategories))
    }

    fun addCategory(name: String, percentage: Int): Boolean {
        val trimmedName = name.trim()
        if (trimmedName.isBlank() || percentage <= 0) return false
        val current = stateFlow.value
        val totalPercentage = current.categories.sumOf { it.percentage } + percentage
        if (totalPercentage > 100) {
            return false
        }
        val newCategories = current.categories + BudgetCategory(
            name = trimmedName,
            percentage = percentage,
            allocatedAmount = BigDecimal.ZERO,
            remainingAmount = BigDecimal.ZERO
        )
        val refreshed = resetCategoriesForIncome(current.totalIncome, current.fixedExpenses, newCategories)
        updateState(current.copy(categories = refreshed))
        return true
    }

    fun updateCategoryPercentage(id: String, newPercentage: Int): Boolean {
        if (newPercentage <= 0) return false
        val current = stateFlow.value
        val otherTotal = current.categories.filterNot { it.id == id }.sumOf { it.percentage }
        if (otherTotal + newPercentage > 100) return false
        val updated = current.categories.map {
            if (it.id == id) it.copy(percentage = newPercentage) else it
        }
        val refreshed = resetCategoriesForIncome(current.totalIncome, current.fixedExpenses, updated)
        updateState(current.copy(categories = refreshed))
        return true
    }

    fun removeCategory(id: String) {
        val current = stateFlow.value
        val updated = current.categories.filterNot { it.id == id }
        val refreshed = resetCategoriesForIncome(current.totalIncome, current.fixedExpenses, updated)
        updateState(current.copy(categories = refreshed))
    }

    fun recordCategorySpend(categoryId: String, amount: BigDecimal): Boolean {
        if (amount <= BigDecimal.ZERO) return false
        val current = stateFlow.value
        val updated = current.categories.map { category ->
            if (category.id == categoryId) {
                val newRemaining = category.remainingAmount.subtract(amount)
                if (newRemaining < BigDecimal.ZERO) {
                    return false
                }
                category.copy(remainingAmount = newRemaining.coerceAtLeast(BigDecimal.ZERO))
            } else {
                category
            }
        }
        updateState(current.copy(categories = updated))
        return true
    }

    private fun resetCategoriesForIncome(
        totalIncome: BigDecimal,
        fixedExpenses: List<FixedExpense>,
        categories: List<BudgetCategory>
    ): List<BudgetCategory> {
        if (categories.isEmpty()) return categories
        val available = totalIncome.subtract(fixedExpenses.fold(BigDecimal.ZERO) { acc, expense -> acc.add(expense.amount) })
            .coerceAtLeast(BigDecimal.ZERO)
        return categories.map { category ->
            val allocation = calculateShare(available, category.percentage)
            category.copy(
                allocatedAmount = allocation,
                remainingAmount = allocation
            )
        }
    }

    private fun applySupplementalIncome(amount: BigDecimal, categories: List<BudgetCategory>): List<BudgetCategory> {
        if (categories.isEmpty()) return categories
        val totalPercentage = categories.sumOf { it.percentage }
        if (totalPercentage == 0) return categories
        return categories.map { category ->
            val additional = calculateShare(amount, category.percentage, totalPercentage)
            category.copy(
                allocatedAmount = category.allocatedAmount.add(additional),
                remainingAmount = category.remainingAmount.add(additional)
            )
        }
    }

    private fun calculateShare(
        pool: BigDecimal,
        percentage: Int,
        denominator: Int = 100
    ): BigDecimal {
        if (pool <= BigDecimal.ZERO || percentage <= 0 || denominator <= 0) return BigDecimal.ZERO
        val ratio = BigDecimal(percentage).divide(BigDecimal(denominator), SCALE, RoundingMode.HALF_UP)
        return pool.multiply(ratio).setScale(2, RoundingMode.HALF_UP)
    }

    private fun updateState(newState: BudgetState) {
        stateFlow.value = newState
        persistState(newState)
    }

    private fun persistState(state: BudgetState) {
        val json = JSONObject().apply {
            put(KEY_TOTAL_INCOME, state.totalIncome.toPlainString())
            put(KEY_LAST_INCOME_TYPE, state.lastIncomeType?.name)
            put(KEY_FIXED_EXPENSES, JSONArray().apply {
                state.fixedExpenses.forEach { expense ->
                    put(
                        JSONObject().apply {
                            put(KEY_ID, expense.id)
                            put(KEY_NAME, expense.name)
                            put(KEY_AMOUNT, expense.amount.toPlainString())
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
            val totalIncome = json.optString(KEY_TOTAL_INCOME, "0").toBigDecimalOrZero()
            val lastIncomeType = json.optString(KEY_LAST_INCOME_TYPE, null)?.let { IncomeType.valueOf(it) }
            val fixedExpenses = json.optJSONArray(KEY_FIXED_EXPENSES)?.let { array ->
                buildList {
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        add(
                            FixedExpense(
                                id = item.optString(KEY_ID, UUID.randomUUID().toString()),
                                name = item.optString(KEY_NAME),
                                amount = item.optString(KEY_AMOUNT, "0").toBigDecimalOrZero()
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
                totalIncome = totalIncome,
                fixedExpenses = fixedExpenses,
                categories = categories,
                lastIncomeType = lastIncomeType
            )
        } catch (t: Throwable) {
            BudgetState()
        }
    }

    companion object {
        private const val PREFS_NAME = "budget_prefs"
        private const val KEY_STATE_JSON = "state_json"
        private const val KEY_TOTAL_INCOME = "total_income"
        private const val KEY_LAST_INCOME_TYPE = "last_income_type"
        private const val KEY_FIXED_EXPENSES = "fixed_expenses"
        private const val KEY_CATEGORIES = "categories"
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_AMOUNT = "amount"
        private const val KEY_PERCENTAGE = "percentage"
        private const val KEY_ALLOCATED = "allocated"
        private const val KEY_REMAINING = "remaining"
        private const val SCALE = 4

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
    BigDecimal(this)
} catch (e: NumberFormatException) {
    BigDecimal.ZERO
}
