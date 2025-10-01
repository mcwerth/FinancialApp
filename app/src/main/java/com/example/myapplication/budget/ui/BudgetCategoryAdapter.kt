package com.example.myapplication.budget.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.budget.BudgetCategory
import com.example.myapplication.databinding.ItemBudgetCategoryBinding
import java.text.NumberFormat

class BudgetCategoryAdapter(
    private val onSpendClick: ((BudgetCategory) -> Unit)? = null,
    private val onEditPercentageClick: ((BudgetCategory) -> Unit)? = null,
    private val onDeleteClick: ((BudgetCategory) -> Unit)? = null
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
        private val onSpendClick: ((BudgetCategory) -> Unit)?,
        private val onEditPercentageClick: ((BudgetCategory) -> Unit)?,
        private val onDeleteClick: ((BudgetCategory) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {
        private val currencyFormatter = NumberFormat.getCurrencyInstance()

        fun bind(category: BudgetCategory) {
            binding.categoryName.text = category.name
            binding.categoryPercentage.text = "${category.percentage}%"
            binding.categoryAllocated.text = binding.root.context.getString(
                R.string.category_allocated_format,
                currencyFormatter.format(category.allocatedAmount)
            )
            binding.categoryRemaining.text = binding.root.context.getString(
                R.string.category_remaining_format,
                currencyFormatter.format(category.remainingAmount)
            )
            binding.spendButton.apply {
                isVisible = onSpendClick != null
                setOnClickListener { onSpendClick?.invoke(category) }
            }
            binding.editPercentageButton.apply {
                isVisible = onEditPercentageClick != null
                setOnClickListener { onEditPercentageClick?.invoke(category) }
            }
            binding.deleteButton.apply {
                isVisible = onDeleteClick != null
                setOnClickListener { onDeleteClick?.invoke(category) }
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<BudgetCategory>() {
        override fun areItemsTheSame(oldItem: BudgetCategory, newItem: BudgetCategory): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: BudgetCategory, newItem: BudgetCategory): Boolean = oldItem == newItem
    }
}
