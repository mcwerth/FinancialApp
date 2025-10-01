package com.example.myapplication

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.myapplication.budget.BudgetEvent
import com.example.myapplication.budget.BudgetRepository
import com.example.myapplication.budget.BudgetViewModel
import com.example.myapplication.budget.BudgetViewModelFactory
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.ui.DashboardFragment
import com.example.myapplication.ui.ManageFragment
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val viewModel: BudgetViewModel by viewModels {
        BudgetViewModelFactory(BudgetRepository.getInstance(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPager()
        observeEvents()
    }

    private fun setupPager() {
        binding.viewPager.adapter = BudgetPagerAdapter()
        TabLayoutMediator(binding.tabs, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.dashboard_tab_title)
                else -> getString(R.string.manage_tab_title)
            }
        }.attach()
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            viewModel.events.collectLatest { event ->
                val messageRes = when (event) {
                    BudgetEvent.IncomeRecorded -> R.string.toast_income_recorded
                    BudgetEvent.FixedExpenseAdded -> R.string.toast_fixed_expense_added
                    BudgetEvent.FixedExpenseUpdated -> R.string.toast_fixed_expense_updated
                    BudgetEvent.FixedExpenseRemoved -> R.string.toast_fixed_expense_removed
                    BudgetEvent.CategoryAdded -> R.string.toast_category_added
                    BudgetEvent.CategoryUpdated -> R.string.toast_category_updated
                    BudgetEvent.CategoryRemoved -> R.string.toast_category_removed
                    BudgetEvent.InvalidCategoryPercentage -> R.string.toast_invalid_percentage
                    BudgetEvent.SpendRecorded -> R.string.toast_spend_recorded
                    BudgetEvent.InvalidSpend -> R.string.toast_invalid_spend
                }
                Toast.makeText(this@MainActivity, messageRes, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private inner class BudgetPagerAdapter : FragmentStateAdapter(this) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int) = when (position) {
            0 -> DashboardFragment()
            else -> ManageFragment()
        }
    }
}
