package com.example.myapplication.budget

import java.math.BigDecimal
import java.util.UUID
import java.time.LocalDate

data class FixedExpense(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: BigDecimal,
    val nextDueDate: LocalDate
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
    val totalBalance: BigDecimal = BigDecimal.ZERO,
    val fixedExpenses: List<FixedExpense> = emptyList(),
    val categories: List<BudgetCategory> = emptyList()
)
