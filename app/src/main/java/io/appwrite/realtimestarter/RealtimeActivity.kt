package io.appwrite.realtimestarter

import android.os.Bundle
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView

class RealtimeActivity : AppCompatActivity() {

    private val viewModel by viewModels<RealtimeViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realtime)

        val button = findViewById<Button>(R.id.btnSubscribe)
        button.setOnClickListener {
            viewModel.subscribeToProducts(this)
        }

        val adapter = ProductAdapter()
        val recycler = findViewById<RecyclerView>(R.id.recyclerProducts)
        recycler.adapter = adapter

        viewModel.productStream.observe(this) {
            adapter.submitNext(it)
        }
    }
}

