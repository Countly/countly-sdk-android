package ly.count.android.demo;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class DemoFragmentC extends Fragment {

    private int navCount = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_demo_c, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView logText = view.findViewById(R.id.text_log);

        // Navigate to a different activity (tests overlay migration activity + fragment combo)
        view.findViewById(R.id.btn_go_to_activity).setOnClickListener(v -> {
            navCount++;
            logText.append("\n[" + navCount + "] Navigating to Custom Events Activity...");
            startActivity(new Intent(requireContext(), ActivityExampleCustomEvents.class));
        });

        // Pop back stack (tests overlay during fragment back navigation)
        view.findViewById(R.id.btn_go_back).setOnClickListener(v -> {
            navCount++;
            logText.append("\n[" + navCount + "] Popping back stack...");
            requireActivity().getSupportFragmentManager().popBackStack();
        });
    }
}
