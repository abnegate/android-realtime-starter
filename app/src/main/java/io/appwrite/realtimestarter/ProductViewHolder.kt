package io.appwrite.realtimestarter

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private var nameView: TextView = itemView.findViewById(R.id.txtName)
    private var priceView: TextView = itemView.findViewById(R.id.txtPrice)
    private var imageView: ImageView = itemView.findViewById(R.id.imgProduct)

    fun setName(name: String) {
        nameView.text = name
    }

    fun setPrice(price: String) {
        priceView.text = "\$$price"
    }

    fun setProductImage(url: String) {
        Glide.with(itemView)
            .load(url)
            .centerCrop()
            .into(imageView)
    }
}