package com.sustain.step.ui.audio_player


import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.sustain.step.R
import com.sustain.step.databinding.ItemSongBinding

class AudioAdapter(
    private val selectSomeTrack: (Uri) -> Unit,
    private val toggleFavorite: (Uri) -> Unit
) :
    androidx.recyclerview.widget.ListAdapter<AudioData, AudioAdapter.ViewHolder>(
        DiffCallback
    ) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSongBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item: AudioData = currentList[position]
//        val lp = holder.itemView.layoutParams as? ViewGroup.MarginLayoutParams
//        if (lp != null) {
//            val context = holder.itemView.context
//            val dp10 = holder.dpToPx(context, 10f)
//            val dp30 = holder.dpToPx(context, 30f)
//            lp.topMargin = 0
//            lp.bottomMargin = if (position == itemCount - 1) dp30 else dp10
//
//            holder.itemView.layoutParams = lp
//        }
        holder.bind(item, selectSomeTrack, toggleFavorite)
    }



    class ViewHolder(
        private val binding: ItemSongBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun dpToPx(context: Context, dp: Float): Int {
            val density = context.resources.displayMetrics.density
            return (dp * density).toInt()
        }

        fun bind(
            item: AudioData,
            selectSomeTrack: (Uri) -> Unit,
            toggleFavorite: (Uri) -> Unit
        ) {
            binding.apply {
                name.text = item.title
                artist.text = item.artist
                if (item.isPlaying) {
                    iconIsPlay.setImageResource(R.drawable.icon_pause)
                } else {
                    iconIsPlay.setImageResource(R.drawable.icon_play_big)
                }
                buttonFavorite.setImageResource(
                    if (item.isFavorite) R.drawable.icon_heart_filled else R.drawable.icon_heart_outline
                )
                val white = ContextCompat.getColor(itemView.context, R.color.white_f8)
                iconIsPlay.setColorFilter(white)
                buttonFavorite.setColorFilter(white)
                iconIsPlay.alpha = if (item.isPlaying) 1f else 0.72f
                buttonFavorite.alpha = if (item.isFavorite) 1f else 0.78f
                itemView.setOnClickListener { selectSomeTrack(item.uri) }
                buttonFavorite.setOnClickListener { toggleFavorite(item.uri) }

            }
        }

    }


    companion object DiffCallback : DiffUtil.ItemCallback<AudioData>() {
        override fun areItemsTheSame(
            oldItem: AudioData,
            newItem: AudioData
        ): Boolean {
            return oldItem.uri == newItem.uri
        }

        override fun areContentsTheSame(
            oldItem: AudioData,
            newItem: AudioData
        ): Boolean {
            return oldItem == newItem
        }
    }
}