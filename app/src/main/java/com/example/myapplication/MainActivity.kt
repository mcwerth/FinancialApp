package com.example.myapplication

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.budget.BudgetCategory
import com.example.myapplication.budget.BudgetEvent
import com.example.myapplication.budget.BudgetRepository
import com.example.myapplication.budget.BudgetViewModel
import com.example.myapplication.budget.BudgetViewModelFactory
import com.example.myapplication.budget.IncomeType
import com.example.myapplication.budget.ui.BudgetCategoryAdapter
import com.example.myapplication.budget.ui.FixedExpenseAdapter
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.databinding.DialogSpendBinding
import com.example.myapplication.databinding.DialogUpdatePercentageBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.math.BigDecimal
import java.text.NumberFormat
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val viewModel: BudgetViewModel by viewModels {
        BudgetViewModelFactory(BudgetRepository.getInstance(applicationContext))
    }

    private val currencyFormatter = NumberFormat.getCurrencyInstance()
    private val fixedExpenseAdapter = FixedExpenseAdapter()
    private val categoryAdapter by lazy {
        BudgetCategoryAdapter(
            onSpendClick = { category -> showSpendDialog(category) },
            onEditPercentageClick = { category -> showUpdatePercentageDialog(category) },
            onDeleteClick = { category -> viewModel.removeCategory(category.id) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLists()
        setupIncomeInputs()
        setupFixedExpenseInputs()
        setupCategoryInputs()
        observeState()
        observeEvents()
    }

    private fun setupLists() {
        binding.fixedExpenseList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = fixedExpenseAdapter
        }
        binding.categoryList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = categoryAdapter
        }
    }

    private fun setupIncomeInputs() {
        binding.incomeTypeToggle.check(R.id.paycheckButton)
        binding.addIncomeButton.setOnClickListener {
            val amount = binding.incomeAmountInput.text?.toString()?.toBigDecimalOrNull()
            if (amount != null && amount > BigDecimal.ZERO) {
                val type = when (binding.incomeTypeToggle.checkedButtonId) {
                    R.id.supplementalButton -> IncomeType.SUPPLEMENTAL
                    else -> IncomeType.PAYCHECK
                }
                viewModel.addIncome(amount, type)
                binding.incomeAmountInput.setText("")
            } else {
                binding.incomeAmountLayout.error = getString(R.string.income_amount_hint)
            }
        }
        binding.incomeAmountInput.doAfterTextChanged {
            binding.incomeAmountLayout.error = null
        }
    }

    private fun setupFixedExpenseInputs() {
        binding.addFixedExpenseButton.setOnClickListener {
            val name = binding.fixedExpenseNameInput.text?.toString().orEmpty()
            val amount = binding.fixedExpenseAmountInput.text?.toString()?.toBigDecimalOrNull()
            var hasError = false
            if (name.isBlank()) {
                binding.fixedExpenseNameLayout.error = getString(R.string.fixed_expense_name_hint)
                hasError = true
            }
            if (amount == null || amount <= BigDecimal.ZERO) {
                binding.fixedExpenseAmountLayout.error = getString(R.string.fixed_expense_amount_hint)
                hasError = true
            }
            if (!hasError) {
                viewModel.addFixedExpense(name, amount!!)
                binding.fixedExpenseNameInput.setText("")
                binding.fixedExpenseAmountInput.setText("")
                binding.fixedExpenseNameLayout.error = null
                binding.fixedExpenseAmountLayout.error = null
            }
        }
        binding.fixedExpenseAmountInput.doAfterTextChanged {
            binding.fixedExpenseAmountLayout.error = null
        }
        binding.fixedExpenseNameInput.doAfterTextChanged {
            binding.fixedExpenseNameLayout.error = null
        }
    }

    private fun setupCategoryInputs() {
        binding.addCategoryButton.setOnClickListener {
            val name = binding.categoryNameInput.text?.toString().orEmpty()
            val percentage = binding.categoryPercentageInput.text?.toString()?.toIntOrNull()
            var hasError = false
            if (name.isBlank()) {
                binding.categoryNameLayout.error = getString(R.string.category_name_hint)
                hasError = true
            }
            if (percentage == null) {
                binding.categoryPercentageLayout.error = getString(R.string.category_percentage_hint)
                hasError = true
            }
            if (!hasError) {
                val added = viewModel.addCategory(name, percentage!!)
                if (added) {
                    binding.categoryNameInput.setText("")
                    binding.categoryPercentageInput.setText("")
                    binding.categoryNameLayout.error = null
                    binding.categoryPercentageLayout.error = null
                }
            }
        }
        binding.categoryPercentageInput.doAfterTextChanged {
            binding.categoryPercentageLayout.error = null
        }
        binding.categoryNameInput.doAfterTextChanged {
            binding.categoryNameLayout.error = null
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.totalIncomeValue.text = currencyFormatter.format(state.totalIncome)
                binding.fixedExpensesValue.text = getString(
                    R.string.fixed_expenses_summary_placeholder,
                    currencyFormatter.format(state.totalFixedExpenses)
                )
                binding.availableValue.text = getString(
                    R.string.available_summary_placeholder,
                    currencyFormatter.format(state.availableForAllocation)
                )
                fixedExpenseAdapter.submitList(state.fixedExpenses)
                categoryAdapter.submitList(state.categories)
            }
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            viewModel.events.collectLatest { event ->
                when (event) {
                    BudgetEvent.IncomeRecorded -> showToast(R.string.toast_income_recorded)
                    BudgetEvent.FixedExpenseAdded -> showToast(R.string.toast_fixed_expense_added)
                    BudgetEvent.CategoryAdded -> showToast(R.string.toast_category_added)
                    BudgetEvent.InvalidCategoryPercentage -> showToast(R.string.toast_invalid_percentage)
                    BudgetEvent.SpendRecorded -> showToast(R.string.toast_spend_recorded)
                    BudgetEvent.InvalidSpend -> showToast(R.string.toast_invalid_spend)
                }
            }
        }
    }

    private fun showSpendDialog(category: BudgetCategory) {
        val dialogBinding = DialogSpendBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.spend_dialog_title))
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val amount = dialogBinding.spendAmountInput.text?.toString()?.toBigDecimalOrNull()
                if (amount != null) {
                    viewModel.recordCategorySpend(category.id, amount)
                } else {
                    showToast(R.string.toast_invalid_spend)
                }
            }
            .show()
    }

    private fun showUpdatePercentageDialog(category: BudgetCategory) {
        val dialogBinding = DialogUpdatePercentageBinding.inflate(layoutInflater)
        dialogBinding.percentageInput.setText(category.percentage.toString())
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.update_percentage_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val percentage = dialogBinding.percentageInput.text?.toString()?.toIntOrNull()
                if (percentage != null) {
                    viewModel.updateCategoryPercentage(category.id, percentage)
                } else {
                    showToast(R.string.toast_invalid_percentage)
                }
            }
            .show()
    }

    private fun showToast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? = try {
    BigDecimal(this)
} catch (e: NumberFormatException) {
    null
}
