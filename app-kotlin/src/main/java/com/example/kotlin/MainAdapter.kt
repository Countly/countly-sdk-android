package ly.count.android.demo.kotlin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView

class MainAdapter :
    RecyclerView.Adapter<MainAdapter.MainViewHolder>() {

    private val list = listOf<String>("event", "sessions","view","remote config");

    class MainViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val button = view.findViewById<Button>(R.id.item_view)
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
        val item = list.get(position)
        holder.button.text = item.toString()


        holder.button.setOnClickListener {
            val action = MainListFragmentDirections.actionMainListFragmentToSecondListFragment(title = holder.button.text.toString())
            holder.view.findNavController().navigate(action)
        }
    }
}