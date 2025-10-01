package com.example.myapplication.budget.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.budget.FixedExpense
import com.example.myapplication.databinding.ItemFixedExpenseBinding
import java.text.NumberFormat
import java.time.format.DateTimeFormatter

class FixedExpenseAdapter(
    private val onEditDueDate: ((FixedExpense) -> Unit)? = null,
    private val onRemove: ((FixedExpense) -> Unit)? = null
) : ListAdapter<FixedExpense, FixedExpenseAdapter.FixedExpenseViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FixedExpenseViewHolder {
        val binding = ItemFixedExpenseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FixedExpenseViewHolder(binding, onEditDueDate, onRemove)
    }

    override fun onBindViewHolder(holder: FixedExpenseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FixedExpenseViewHolder(
        private val binding: ItemFixedExpenseBinding,
        private val onEditDueDate: ((FixedExpense) -> Unit)?,
        private val onRemove: ((FixedExpense) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(expense: FixedExpense) {
            binding.expenseName.text = expense.name
            binding.expenseAmount.text = NumberFormat.getCurrencyInstance().format(expense.amount)
            binding.expenseDueDate.text = binding.root.context.getString(
                R.string.fixed_expense_due_date_format,
                expense.nextDueDate.format(DATE_FORMATTER)
            )
            binding.manageActions.isVisible = onEditDueDate != null || onRemove != null
            binding.editDueDateButton.apply {
                isVisible = onEditDueDate != null
                setOnClickListener { onEditDueDate?.invoke(expense) }
            }
            binding.removeExpenseButton.apply {
                isVisible = onRemove != null
                setOnClickListener { onRemove?.invoke(expense) }
            }
        }

        companion object {
            private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<FixedExpense>() {
        override fun areItemsTheSame(oldItem: FixedExpense, newItem: FixedExpense): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: FixedExpense, newItem: FixedExpense): Boolean = oldItem == newItem
    }
}
