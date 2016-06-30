package ly.count.android.demo;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Spinner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import ly.count.android.sdk.Countly;

/**
 * Created by techuz on 28/6/16.
 */
public class ProfileFragment extends Fragment {

    private Spinner gender;
    private EditText name,username,email,organization,year,country,city,address,phone_number;
    private ImageView avatar_img;
    private Button submit;
    private String sendGender = "";
    List<String> list = new ArrayList<String>();
    private String imagePath = "";
    private ScrollView sv_profile;

    public ProfileFragment() {
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
        return inflater.inflate(R.layout.profilefragment, container, false);
    }

    public void init(){
        sv_profile = (ScrollView) getView().findViewById(R.id.sv_profile);
        name = (EditText)getView().findViewById(R.id.et_name);
        username = (EditText)getView().findViewById(R.id.et_username);
        email = (EditText)getView().findViewById(R.id.et_email);
        organization = (EditText)getView().findViewById(R.id.et_organization);
        year = (EditText)getView().findViewById(R.id.et_year);
        country = (EditText)getView().findViewById(R.id.et_country);
        city = (EditText)getView().findViewById(R.id.et_city);
        address = (EditText)getView().findViewById(R.id.et_address);
        phone_number = (EditText)getView().findViewById(R.id.et_number);
        avatar_img = (ImageView)getView().findViewById(R.id.iv_avatar);
        gender = (Spinner) getView().findViewById(R.id.sp_gender);
        submit = (Button) getView().findViewById(R.id.btn_submit);
        list.clear();
        list.add("Gender");
        list.add("Male");
        list.add("Female");

        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>
                (getActivity(), android.R.layout.simple_spinner_item,list);

        dataAdapter.setDropDownViewResource
                (android.R.layout.simple_spinner_dropdown_item);

        gender.setAdapter(dataAdapter);
        sv_profile.post(new Runnable() {
            @Override
            public void run() {
                sv_profile.fullScroll(ScrollView.FOCUS_UP);
            }
        });

    }

    public void clickListener(){

        //select image form gallery or capture from camera

        avatar_img.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectImage();
            }
        });


        // set user data and send to server

        submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                HashMap<String, String> data = new HashMap<String, String>();
                data.put("name", name.getText().toString());
                data.put("username", username.getText().toString());
                data.put("email", email.getText().toString());
                data.put("organization",organization.getText().toString());
                data.put("phone", phone_number.getText().toString());
                data.put("gender", sendGender);
                //provide url to picture
                //data.put("picture", "http://example.com/pictures/profile_pic.png");
                //or locally from device
                //data.put("picturePath", "/mnt/sdcard/portrait.jpg");
                if(!imagePath.equals("")) {
                    data.put("picturePath", imagePath);
                }
                data.put("byear", year.getText().toString());

                //providing any custom key values to store with user
                HashMap<String, String> custom = new HashMap<String, String>();
                custom.put("country", country.getText().toString());
                custom.put("city", city.getText().toString());
                custom.put("address", address.getText().toString());

                //set multiple custom properties
                Countly.userData.setUserData(data, custom);
                Countly.userData.save();

            }
        });

        //select gender code

        gender.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if(i==1){
                    sendGender = "M";
                }else if(i==2){
                    sendGender = "F";
                }else{
                    sendGender = "M";
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

    }

    public boolean checkFieldvalidation(){

        if(!name.getText().toString().equals("") && !username.getText().toString().equals("") &&
                !email.getText().toString().equals("") && !organization.getText().toString().equals("") &&
                !phone_number.getText().toString().equals("") && !sendGender.equals("") &&
                !year.getText().toString().equals("") &&
                !country.getText().toString().equals("") && !city.getText().toString().equals("")
                && !address.getText().toString().equals("")){
            return  true;
        }else {
         //   Toast.makeText(getActivity(),"Please fill the field",Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    //image selection popup

    private void selectImage() {

        final CharSequence[] options = { "Take Photo", "Choose from Gallery","Cancel" };

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Add Photo!");
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                if (options[item].equals("Take Photo"))
                {
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    File f = new File(Environment.getExternalStorageDirectory(), "temp.jpg");
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));
                    startActivityForResult(intent, 1);
                }
                else if (options[item].equals("Choose from Gallery"))
                {
                    Intent intent = new   Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    startActivityForResult(intent, 2);

                }
                else if (options[item].equals("Cancel")) {
                    dialog.dismiss();
                }
            }
        });
        builder.show();
    }

    //set image after taking from gallery or capture by camera

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        //super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == getActivity().RESULT_OK) {
            if (requestCode == 1) {
                File f = new File(Environment.getExternalStorageDirectory().toString());
                for (File temp : f.listFiles()) {
                    if (temp.getName().equals("temp.jpg")) {
                        f = temp;
                        break;
                    }
                }
                try {
                    Bitmap bitmap;
                    BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();

                    bitmap = BitmapFactory.decodeFile(f.getAbsolutePath(),
                            bitmapOptions);

                    avatar_img.setImageBitmap(bitmap);

                    String path = Environment
                            .getExternalStorageDirectory()
                            + File.separator;
                            //+ "Phoenix" + File.separator + "default";
                    f.delete();
                    OutputStream outFile = null;
                    File file = new File(path, String.valueOf(System.currentTimeMillis()) + ".jpg");
                    try {
                        outFile = new FileOutputStream(file);
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outFile);
                        outFile.flush();
                        outFile.close();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    imagePath = file.getAbsolutePath();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (requestCode == 2) {

                Uri selectedImage = data.getData();
                String[] filePath = { MediaStore.Images.Media.DATA };
                Cursor c = getActivity().getContentResolver().query(selectedImage,filePath, null, null, null);
                c.moveToFirst();
                int columnIndex = c.getColumnIndex(filePath[0]);
                String picturePath = c.getString(columnIndex);
                imagePath = picturePath;
                c.close();
                Bitmap thumbnail = (BitmapFactory.decodeFile(picturePath));
                Log.w("path of image gallery", picturePath+"");
                avatar_img.setImageBitmap(thumbnail);
            }
        }

    }
}
