package com.example.imageprocessing

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.imageprocessing.R
import com.example.imageprocessing.GridItem
import com.bumptech.glide.Glide

class GridAdapter(private val context: Context, private val items: MutableList<GridItem>) :
    RecyclerView.Adapter<GridAdapter.ImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.recycler_image_item, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int {
        return items.size
    }

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.image_view)
        val imageQualityScore: TextView = itemView.findViewById(R.id.image_quality_score)
        val numberOfFaces: TextView = itemView.findViewById(R.id.number_of_faces)
        val similarityScore: TextView = itemView.findViewById(R.id.similarity_score)

        fun bind(item: GridItem) {
            Glide.with(context).load(item.image).into(imageView)
            imageQualityScore.text = item.qualityScoreText
            numberOfFaces.text = item.numberOfFacesText
            similarityScore.text = item.similarityScoreText
        }
    }
}