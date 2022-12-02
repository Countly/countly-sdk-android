package com.example.kotlin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView

class AdapterMainList :
    RecyclerView.Adapter<AdapterMainList.MainViewHolder>() {

    private val list = listOf("event", "sessions","view","remote config")

    class MainViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val button: Button = view.findViewById(R.id.item_view)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        val layout = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.item_view, parent, false)

        return MainViewHolder(layout)
    }

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        val item = list[position]
        holder.button.text = item


        holder.button.setOnClickListener {
            val action = FragmentMainListDirections.actionFragmentMainListToFragmentCustomEvents(title = holder.button.text.toString())
            holder.view.findNavController().navigate(action)
        }
    }
}