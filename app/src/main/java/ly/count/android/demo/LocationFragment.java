package ly.count.android.demo;

import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import ly.count.android.sdk.Countly;

/**
 * Created by techuz on 28/6/16.
 */
public class LocationFragment extends Fragment {

    private Button sendlocation;
    GPSTracker mGPS;

    public LocationFragment() {
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
        return inflater.inflate(R.layout.locationfragment, container, false);
    }

    public void init(){
        sendlocation = (Button) getView().findViewById(R.id.btn_sendlocation);
        mGPS = new GPSTracker(getActivity());
    }

    public void clickListener(){

        sendlocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
             //   Countly.sharedInstance().setLocation(56.1304,106.3468);
                if(mGPS.canGetLocation ){
                    mGPS.getLocation();

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            //Countly.sharedInstance().setLocation(44.5888300, 33.5224000);
                            Countly.sharedInstance().setLocation(mGPS.getLatitude(),mGPS.getLongitude());
                            Log.i("current location",mGPS.getLatitude() +" "+ mGPS.getLongitude());
                        }
                    }, 1000);


                }else{

                    System.out.println("Unable");
                }
            }
        });

    }

}
