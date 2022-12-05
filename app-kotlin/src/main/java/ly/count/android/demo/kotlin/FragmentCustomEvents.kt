package ly.count.android.demo.kotlin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import ly.count.android.demo.kotlin.databinding.FragmentSecondListBinding

class FragmentCustomEvents : Fragment() {

    companion object {
        val TITLE = "title"
    }

    private var _binding: FragmentSecondListBinding? = null
    private val binding get() = _binding!!
    private lateinit var titleId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            titleId = it.getString(TITLE).toString()
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSecondListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recyclerView = binding.recyclerView
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = AdapterSecondList(titleId, requireContext())
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
