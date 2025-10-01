package com.example.myapplication.budget

import java.math.BigDecimal
import java.util.UUID

enum class IncomeType {
    PAYCHECK,
    SUPPLEMENTAL
}

data class FixedExpense(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: BigDecimal
)

data class BudgetCategory(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val percentage: Int,
    val allocatedAmount: BigDecimal,
    val remainingAmount: BigDecimal
) {
    val spentAmount: BigDecimal
        get() = allocatedAmount.subtract(remainingAmount)
}

data class BudgetState(
    val totalIncome: BigDecimal = BigDecimal.ZERO,
    val fixedExpenses: List<FixedExpense> = emptyList(),
    val categories: List<BudgetCategory> = emptyList(),
    val lastIncomeType: IncomeType? = null
) {
    val totalFixedExpenses: BigDecimal
        get() = fixedExpenses.fold(BigDecimal.ZERO) { acc, expense -> acc.add(expense.amount) }

    val availableForAllocation: BigDecimal
        get() = (totalIncome.subtract(totalFixedExpenses)).coerceAtLeast(BigDecimal.ZERO)
}

fun BigDecimal.coerceAtLeast(minimumValue: BigDecimal): BigDecimal {
    return if (this < minimumValue) minimumValue else this
}
