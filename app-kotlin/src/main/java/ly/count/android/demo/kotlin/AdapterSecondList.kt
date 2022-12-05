package ly.count.android.demo.kotlin

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView

import ly.count.android.sdk.Countly


class AdapterSecondList(private val titleId: String, context: Context) :
    RecyclerView.Adapter<AdapterSecondList.SecondViewHolder>() {

    private val filteredWords: List<String>

    init {
        val titles = context.resources.getStringArray(R.array.titles).toList()

        filteredWords = titles
             .filter { it.contains(titleId, ignoreCase = true) }
            .shuffled()
            .sorted()
    }

    class SecondViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val button = view.findViewById<Button>(R.id.item_view)
    }

    override fun getItemCount(): Int = filteredWords.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SecondViewHolder {
        val layout = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.item_view, parent, false)

        return SecondViewHolder(layout)
    }

    override fun onBindViewHolder(holder: SecondViewHolder, position: Int) {
        val item = filteredWords[position]
        // Needed to call startActivity
        val context = holder.view.context
        // Set the text of the WordViewHolder
        holder.button.text = item

        holder.view.setOnClickListener{
            Countly.sharedInstance().events().recordEvent(item)
        }
    }
}