package ly.count.android.demo;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.util.HashMap;

import ly.count.android.sdk.Countly;

/**
 * Created by techuz on 28/6/16.
 */
public class EventFragment extends Fragment {

    private Button loginEvent,logoutEVent,wonEvent,lostEvent,sharedEvent;

    public EventFragment() {
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
        return inflater.inflate(R.layout.eventfragment, container, false);

    }



    public void init(){
        loginEvent = (Button) getView().findViewById(R.id.btn_login_event);
        logoutEVent = (Button) getView().findViewById(R.id.btn_logout_event);
        wonEvent = (Button) getView().findViewById(R.id.btn_won_event);
        lostEvent = (Button) getView().findViewById(R.id.btn_lost_event);
        sharedEvent = (Button) getView().findViewById(R.id.btn_shared_event);
    }

    public void clickListener(){

        //login event

        loginEvent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Countly.sharedInstance().recordEvent("Login");
            }
        });


        //logout event

        logoutEVent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Countly.sharedInstance().recordEvent("Logout");
            }
        });

        //won event segmentation with three different value

        wonEvent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                HashMap<String, String> segmentation = new HashMap<String, String>();
                segmentation.put("difficulty", "medium");
                segmentation.put("level", "12");
                segmentation.put("mode", "high");
                Countly.sharedInstance().recordEvent("Won",segmentation,1);
            }
        });

        //lost event segmentation with three different value

        lostEvent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                HashMap<String, String> segmentation = new HashMap<String, String>();
                segmentation.put("difficulty", "low");
                segmentation.put("level", "5");
                segmentation.put("mode", "easy");
                Countly.sharedInstance().recordEvent("Lost",segmentation,1);
            }
        });

        //share social media segmentation
        sharedEvent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                HashMap<String, String> segmentation = new HashMap<String, String>();
                segmentation.put("Facebook", "facebook");
                segmentation.put("Twitter", "twitter");
                Countly.sharedInstance().recordEvent("shared",segmentation,1);
            }
        });
    }

}
