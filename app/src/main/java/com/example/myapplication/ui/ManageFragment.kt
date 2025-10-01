package com.example.myapplication.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.R
import com.example.myapplication.budget.BudgetCategory
import com.example.myapplication.budget.BudgetRepository
import com.example.myapplication.budget.BudgetViewModel
import com.example.myapplication.budget.BudgetViewModelFactory
import com.example.myapplication.budget.FixedExpense
import com.example.myapplication.budget.ui.BudgetCategoryAdapter
import com.example.myapplication.budget.ui.FixedExpenseAdapter
import com.example.myapplication.databinding.DialogUpdatePercentageBinding
import com.example.myapplication.databinding.FragmentManageBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ManageFragment : Fragment() {

    private var _binding: FragmentManageBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BudgetViewModel by activityViewModels {
        BudgetViewModelFactory(BudgetRepository.getInstance(requireContext().applicationContext))
    }

    private val currencyFormatter = NumberFormat.getCurrencyInstance()
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
    private val fixedExpenseAdapter by lazy {
        FixedExpenseAdapter(
            onEditDueDate = { expense -> showDueDatePicker(expense) },
            onRemove = { expense -> viewModel.removeFixedExpense(expense.id) }
        )
    }
    private val categoryAdapter by lazy {
        BudgetCategoryAdapter(
            onEditPercentageClick = { category -> showUpdatePercentageDialog(category) },
            onDeleteClick = { category -> viewModel.removeCategory(category.id) }
        )
    }

    private var pendingDueDate: LocalDate? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        setupCategoryInputs()
        setupFixedExpenseInputs()
        observeState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupRecyclerViews() {
        binding.categoryList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = categoryAdapter
            isNestedScrollingEnabled = false
        }
        binding.fixedExpenseList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = fixedExpenseAdapter
            isNestedScrollingEnabled = false
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
            if (percentage == null || percentage <= 0) {
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
        binding.categoryNameInput.doAfterTextChanged {
            binding.categoryNameLayout.error = null
        }
        binding.categoryPercentageInput.doAfterTextChanged {
            binding.categoryPercentageLayout.error = null
        }
    }

    private fun setupFixedExpenseInputs() {
        binding.fixedExpenseDueDateLayout.setEndIconOnClickListener {
            showDueDatePicker(null)
        }
        binding.fixedExpenseDueDateInput.setOnClickListener {
            showDueDatePicker(null)
        }
        binding.addFixedExpenseButton.setOnClickListener {
            val name = binding.fixedExpenseNameInput.text?.toString().orEmpty()
            val amount = binding.fixedExpenseAmountInput.text?.toString()?.toBigDecimalOrNull()
            val dueDate = pendingDueDate
            var hasError = false
            if (name.isBlank()) {
                binding.fixedExpenseNameLayout.error = getString(R.string.fixed_expense_name_hint)
                hasError = true
            }
            if (amount == null || amount <= BigDecimal.ZERO) {
                binding.fixedExpenseAmountLayout.error = getString(R.string.fixed_expense_amount_hint)
                hasError = true
            }
            if (dueDate == null) {
                binding.fixedExpenseDueDateLayout.error = getString(R.string.fixed_expense_due_date_hint)
                hasError = true
            }
            if (!hasError && dueDate != null) {
                viewModel.addFixedExpense(name, amount!!, dueDate)
                clearFixedExpenseInputs()
            }
        }
        binding.fixedExpenseNameInput.doAfterTextChanged {
            binding.fixedExpenseNameLayout.error = null
        }
        binding.fixedExpenseAmountInput.doAfterTextChanged {
            binding.fixedExpenseAmountLayout.error = null
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.categoryEmptyText.isVisible = state.categories.isEmpty()
                binding.fixedEmptyText.isVisible = state.fixedExpenses.isEmpty()
                categoryAdapter.submitList(state.categories)
                fixedExpenseAdapter.submitList(state.fixedExpenses)
                val totalFixed = state.fixedExpenses.fold(BigDecimal.ZERO) { acc, expense ->
                    acc.add(expense.amount)
                }
                binding.fixedSummaryValue.text = currencyFormatter.format(totalFixed)
            }
        }
    }

    private fun showDueDatePicker(expense: FixedExpense?) {
        val initialDate = expense?.nextDueDate ?: pendingDueDate ?: LocalDate.now()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.pick_due_date_title)
            .setSelection(initialDate.toEpochMilli())
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            val date = Instant.ofEpochMilli(selection).atZone(ZoneId.systemDefault()).toLocalDate()
            if (expense == null) {
                pendingDueDate = date
                binding.fixedExpenseDueDateInput.setText(date.format(dateFormatter))
                binding.fixedExpenseDueDateLayout.error = null
            } else {
                viewModel.updateFixedExpenseDueDate(expense.id, date)
            }
        }
        picker.show(childFragmentManager, DUE_DATE_PICKER_TAG)
    }

    private fun showUpdatePercentageDialog(category: BudgetCategory) {
        val dialogBinding = DialogUpdatePercentageBinding.inflate(layoutInflater)
        dialogBinding.percentageInput.setText(category.percentage.toString())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.update_percentage_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val percentage = dialogBinding.percentageInput.text?.toString()?.toIntOrNull()
                if (percentage != null) {
                    viewModel.updateCategoryPercentage(category.id, percentage)
                } else {
                    Toast.makeText(requireContext(), R.string.toast_invalid_percentage, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun clearFixedExpenseInputs() {
        binding.fixedExpenseNameInput.setText("")
        binding.fixedExpenseAmountInput.setText("")
        binding.fixedExpenseDueDateInput.setText("")
        binding.fixedExpenseNameLayout.error = null
        binding.fixedExpenseAmountLayout.error = null
        binding.fixedExpenseDueDateLayout.error = null
        pendingDueDate = null
    }

    private fun LocalDate.toEpochMilli(): Long {
        return atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    companion object {
        private const val DUE_DATE_PICKER_TAG = "dueDatePicker"
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? = try {
    BigDecimal(this)
} catch (e: NumberFormatException) {
    null
}
