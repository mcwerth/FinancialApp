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
import com.example.myapplication.budget.ui.BudgetCategoryAdapter
import com.example.myapplication.budget.ui.FixedExpenseAdapter
import com.example.myapplication.databinding.DialogSpendBinding
import com.example.myapplication.databinding.FragmentDashboardBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.math.BigDecimal
import java.text.NumberFormat
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BudgetViewModel by activityViewModels {
        BudgetViewModelFactory(BudgetRepository.getInstance(requireContext().applicationContext))
    }

    private val currencyFormatter = NumberFormat.getCurrencyInstance()
    private val fixedExpenseAdapter = FixedExpenseAdapter()
    private val categoryAdapter by lazy {
        BudgetCategoryAdapter(
            onSpendClick = { category -> showSpendDialog(category) }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        setupIncomeInputs()
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
        binding.fixedExpensesList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = fixedExpenseAdapter
            isNestedScrollingEnabled = false
        }
        binding.dynamicCategoriesList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = categoryAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupIncomeInputs() {
        binding.addIncomeButton.setOnClickListener {
            val amount = binding.incomeAmountInput.text?.toString()?.toBigDecimalOrNull()
            if (amount != null && amount > BigDecimal.ZERO) {
                viewModel.addIncome(amount)
                binding.incomeAmountInput.setText("")
                binding.incomeAmountLayout.error = null
            } else {
                binding.incomeAmountLayout.error = getString(R.string.income_amount_hint)
            }
        }
        binding.incomeAmountInput.doAfterTextChanged {
            binding.incomeAmountLayout.error = null
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.currentBalanceValue.text = currencyFormatter.format(state.currentBalance)
                binding.totalAllocatedValue.text = getString(
                    R.string.total_allocated_display,
                    currencyFormatter.format(state.totalAllocated)
                )
                binding.totalRemainingValue.text = getString(
                    R.string.total_remaining_display,
                    currencyFormatter.format(state.totalRemaining)
                )
                binding.fixedExpensesEmptyText.isVisible = state.fixedExpenses.isEmpty()
                binding.dynamicEmptyText.isVisible = state.categories.isEmpty()
                fixedExpenseAdapter.submitList(state.fixedExpenses)
                categoryAdapter.submitList(state.categories)
            }
        }
    }

    private fun showSpendDialog(category: BudgetCategory) {
        val dialogBinding = DialogSpendBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.spend_dialog_title))
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val amount = dialogBinding.spendAmountInput.text?.toString()?.toBigDecimalOrNull()
                if (amount != null) {
                    viewModel.recordCategorySpend(category.id, amount)
                } else {
                    Toast.makeText(requireContext(), R.string.toast_invalid_spend, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? = try {
    BigDecimal(this)
} catch (e: NumberFormatException) {
    null
}
