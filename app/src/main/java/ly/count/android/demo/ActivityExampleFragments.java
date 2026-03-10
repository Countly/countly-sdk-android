package ly.count.android.demo;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

public class ActivityExampleFragments extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_fragments);

        // Show Fragment A by default
        if (savedInstanceState == null) {
            showFragment(new DemoFragmentA(), false);
        }

        findViewById(R.id.btn_fragment_a).setOnClickListener(v -> showFragment(new DemoFragmentA(), true));
        findViewById(R.id.btn_fragment_b).setOnClickListener(v -> showFragment(new DemoFragmentB(), true));
        findViewById(R.id.btn_fragment_c).setOnClickListener(v -> showFragment(new DemoFragmentC(), true));
    }

    private void showFragment(Fragment fragment, boolean addToBackStack) {
        FragmentTransaction tx = getSupportFragmentManager()
            .beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            .replace(R.id.fragment_container, fragment);

        if (addToBackStack) {
            tx.addToBackStack(null);
        }

        tx.commit();
    }
}
