package ly.count.android.demo;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import ly.count.android.sdk.Countly;

/**
 * Created by techuz on 28/6/16.
 */
public class ExceptionFragment extends Fragment {

    private Button btn_exception1,btn_exception2,btn_exception3,btn_exception4,btn_exception5;

    public ExceptionFragment() {
        // Required empty public constructor
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        init();
        clickListener();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.exceptionfragment, container, false);
    }


    public void init(){
        btn_exception1 = (Button) getView().findViewById(R.id.runtime);
        btn_exception2 = (Button) getView().findViewById(R.id.nullpointer);
        btn_exception3 = (Button) getView().findViewById(R.id.division0);
        btn_exception4 = (Button) getView().findViewById(R.id.stackoverflow);
        btn_exception5 = (Button) getView().findViewById(R.id.handled);
    }

    public void clickListener(){

        //login event

        btn_exception1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Countly.sharedInstance().addCrashLog("Button 1 pressed");
                Countly.sharedInstance().crashTest(4);
            }
        });


        //logout event

        btn_exception2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Countly.sharedInstance().addCrashLog("Button 2 pressed");
                Countly.sharedInstance().crashTest(5);
            }
        });

        //won event segmentation with three different value

        btn_exception3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Countly.sharedInstance().addCrashLog("Button 3 pressed");
                Countly.sharedInstance().crashTest(2);
            }
        });

        //lost event segmentation with three different value

        btn_exception4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Countly.sharedInstance().addCrashLog("Button 4 pressed");
                Countly.sharedInstance().crashTest(1);
            }
        });

        //share social media segmentation
        btn_exception5.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                    Countly.sharedInstance().addCrashLog("Button 5 pressed");
                    try {
                        Countly.sharedInstance().crashTest(5);
                    }
                    catch(Exception e){
                        Countly.sharedInstance().logException(e);
                    }
                }
            });

    }
}
