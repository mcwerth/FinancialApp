package com.example.myapplication.budget.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.budget.BudgetCategory
import com.example.myapplication.databinding.ItemBudgetCategoryBinding
import java.text.NumberFormat

class BudgetCategoryAdapter(
    private val onSpendClick: (BudgetCategory) -> Unit,
    private val onEditPercentageClick: (BudgetCategory) -> Unit,
    private val onDeleteClick: (BudgetCategory) -> Unit
) : ListAdapter<BudgetCategory, BudgetCategoryAdapter.BudgetCategoryViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetCategoryViewHolder {
        val binding = ItemBudgetCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BudgetCategoryViewHolder(binding, onSpendClick, onEditPercentageClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: BudgetCategoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class BudgetCategoryViewHolder(
        private val binding: ItemBudgetCategoryBinding,
        private val onSpendClick: (BudgetCategory) -> Unit,
        private val onEditPercentageClick: (BudgetCategory) -> Unit,
        private val onDeleteClick: (BudgetCategory) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        private val currencyFormatter = NumberFormat.getCurrencyInstance()

        fun bind(category: BudgetCategory) {
            binding.categoryName.text = category.name
            binding.categoryPercentage.text = "${category.percentage}%"
            binding.categoryAllocated.text = currencyFormatter.format(category.allocatedAmount)
            binding.categoryRemaining.text = currencyFormatter.format(category.remainingAmount)
            binding.spendButton.setOnClickListener { onSpendClick(category) }
            binding.editPercentageButton.setOnClickListener { onEditPercentageClick(category) }
            binding.deleteButton.setOnClickListener { onDeleteClick(category) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<BudgetCategory>() {
        override fun areItemsTheSame(oldItem: BudgetCategory, newItem: BudgetCategory): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: BudgetCategory, newItem: BudgetCategory): Boolean = oldItem == newItem
    }
}
