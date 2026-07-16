package ly.count.android.demo;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class DemoFragmentA extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_demo_a, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btn_action_a1).setOnClickListener(v ->
            Toast.makeText(requireContext(), "Button 1 tapped - touch works!", Toast.LENGTH_SHORT).show()
        );

        view.findViewById(R.id.btn_action_a2).setOnClickListener(v ->
            Toast.makeText(requireContext(), "Button 2 tapped - touch works!", Toast.LENGTH_SHORT).show()
        );

        // Populate list to test scrolling with overlay
        String[] items = new String[30];
        for (int i = 0; i < 30; i++) {
            items[i] = "List item " + (i + 1) + " - test scrolling with overlay";
        }
        ListView listView = view.findViewById(R.id.list_a);
        listView.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, items));
    }
}
