package io.proximi.navigationdemo.ui.searchitem

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.proximi.navigationdemo.R
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_search_item_detail_image_item.view.*
import kotlin.math.max

private const val TYPE_ITEM = 0
private const val TYPE_EMPTY = 1

class SearchItemDetailImageAdapter(private val imageUrlList: List<String>): RecyclerView.Adapter<SearchItemDetailImageAdapter.ViewHolder>() {

    override fun getItemViewType(position: Int): Int {
        return if (imageUrlList.isNotEmpty()) TYPE_ITEM else TYPE_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(
                if (viewType == TYPE_ITEM) R.layout.activity_search_item_detail_image_item else R.layout.activity_search_item_detail_image_item_empty,
                parent,
                false
            )
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return max(imageUrlList.size, 1)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (imageUrlList.isNotEmpty()) {
            holder.loadImage(imageUrlList[position])
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun loadImage(url: String) {
            Picasso.get()
                .load(url)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_broken_image)
                .into(itemView.titleView)
        }
    }
}
