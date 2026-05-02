package com.sustain.step.ui.history

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.sustain.step.R
import com.sustain.step.data.database.entity.HistoryEntity
import com.sustain.step.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryAdapter :
    androidx.recyclerview.widget.ListAdapter<HistoryEntity, HistoryAdapter.ViewHolder>(
        DiffCallback
    ) {
    private val animatedItemKeys = mutableSetOf<String>()
    private var cascadeEnabledForCurrentData = false


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item: HistoryEntity = currentList[position]
//        val lp = holder.itemView.layoutParams as? ViewGroup.MarginLayoutParams
//        if (lp != null) {
//            val context = holder.itemView.context
//            val dp10 = holder.dpToPx(context, 10f)
//            val dp20 = holder.dpToPx(context, 20f)
//
//            lp.topMargin = if (position == 0) dp20 else 0
//            lp.bottomMargin = dp10
//
//            holder.itemView.layoutParams = lp
//        }
        holder.bind(item)
//        maybeAnimateCascade(holder, position, item)
    }

    fun enableCascadeForCurrentData() {
        cascadeEnabledForCurrentData = true
        animatedItemKeys.clear()
    }

    private fun maybeAnimateCascade(holder: ViewHolder, position: Int, item: HistoryEntity) {
        if (!cascadeEnabledForCurrentData) {
            holder.itemView.alpha = 1f
            holder.itemView.translationY = 0f
            return
        }
        val key = item.id.toString()
        if (!animatedItemKeys.add(key)) {
            holder.itemView.alpha = 1f
            holder.itemView.translationY = 0f
            return
        }
        val startShift = holder.dpToPx(holder.itemView.context, 10f).toFloat()
        holder.itemView.alpha = 0f
        holder.itemView.translationY = startShift
        holder.itemView.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay((position * 45L).coerceAtMost(320L))
            .setDuration(220L)
            .start()
    }


    class ViewHolder(
        private val binding: ItemHistoryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private fun convertDateFromString(dateString: String): String? {
            val sdf = SimpleDateFormat("yyyy-M-d", Locale.ENGLISH)
            val newDate = sdf.parse(dateString) ?: return "null"
            return SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(newDate)
        }

        fun dpToPx(context: Context, dp: Float): Int {
            val density = context.resources.displayMetrics.density
            return (dp * density).toInt()
        }


        fun bind(item: HistoryEntity) {
            binding.apply {
                val safeGoal = item.goal.coerceAtLeast(1)
                val progressPercent = ((item.steps.toDouble() / safeGoal) * 100).toInt()
                val distanceKm = item.steps * KM_PER_STEP
                val calories = (item.steps * KCAL_PER_STEP).toInt()

                date.text = convertDateFromString(item.date)
                progress.text = itemView.context.getString(
                    R.string.history_progress_percent,
                    progressPercent.coerceAtMost(999)
                )
                steps.text = itemView.context.getString(
                    R.string.history_steps_value,
                    formatWithCommas(item.steps)
                )
                goal.text = itemView.context.getString(
                    R.string.history_goal_value,
                    formatWithCommas(safeGoal)
                )
                distance.text = itemView.context.getString(
                    R.string.history_distance_value,
                    String.format(Locale.getDefault(), "%.1f", distanceKm)
                )
                this.calories.text = itemView.context.getString(
                    R.string.history_calories_value,
                    calories
                )
                task.isVisible = item.task.isNotBlank()
                task.text = itemView.context.getString(R.string.history_eco_task_value, item.task)

            }
        }

        private fun formatWithCommas(value: Int): String = String.format("%,d", value)

        private companion object {
            const val KM_PER_STEP = 0.00075
            const val KCAL_PER_STEP = 0.04
        }

    }


    companion object DiffCallback : DiffUtil.ItemCallback<HistoryEntity>() {
        override fun areItemsTheSame(
            oldItem: HistoryEntity,
            newItem: HistoryEntity
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: HistoryEntity,
            newItem: HistoryEntity
        ): Boolean {
            return oldItem == newItem
        }
    }
}