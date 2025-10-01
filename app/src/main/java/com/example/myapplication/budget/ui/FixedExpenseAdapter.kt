package com.example.myapplication.budget.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.budget.FixedExpense
import com.example.myapplication.databinding.ItemFixedExpenseBinding
import java.text.NumberFormat

class FixedExpenseAdapter : ListAdapter<FixedExpense, FixedExpenseAdapter.FixedExpenseViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FixedExpenseViewHolder {
        val binding = ItemFixedExpenseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FixedExpenseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FixedExpenseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FixedExpenseViewHolder(private val binding: ItemFixedExpenseBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(expense: FixedExpense) {
            binding.expenseName.text = expense.name
            binding.expenseAmount.text = NumberFormat.getCurrencyInstance().format(expense.amount)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<FixedExpense>() {
        override fun areItemsTheSame(oldItem: FixedExpense, newItem: FixedExpense): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: FixedExpense, newItem: FixedExpense): Boolean = oldItem == newItem
    }
}
