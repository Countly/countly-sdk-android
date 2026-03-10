package ly.count.android.demo;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

import java.util.HashMap;
import java.util.Map;

import ly.count.android.sdk.Countly;

public class ActivityExampleUserDetails extends AppCompatActivity {

    private TextInputEditText inputName, inputUsername, inputEmail, inputOrganization;
    private TextInputEditText inputPhone, inputGender, inputBirthYear, inputPictureUrl;
    private TextInputEditText inputOpKey, inputOpValue;
    private LinearLayout customPropsContainer;
    private Spinner spinnerOperation;

    private static final String[] OPERATIONS = {
        "increment", "incrementBy", "multiply", "saveMax", "saveMin",
        "setOnce", "push", "pushUnique", "pull"
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example_user_details);

        inputName = findViewById(R.id.inputName);
        inputUsername = findViewById(R.id.inputUsername);
        inputEmail = findViewById(R.id.inputEmail);
        inputOrganization = findViewById(R.id.inputOrganization);
        inputPhone = findViewById(R.id.inputPhone);
        inputGender = findViewById(R.id.inputGender);
        inputBirthYear = findViewById(R.id.inputBirthYear);
        inputPictureUrl = findViewById(R.id.inputPictureUrl);
        inputOpKey = findViewById(R.id.inputOpKey);
        inputOpValue = findViewById(R.id.inputOpValue);
        customPropsContainer = findViewById(R.id.customPropsContainer);
        spinnerOperation = findViewById(R.id.spinnerOperation);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, OPERATIONS);
        spinnerOperation.setAdapter(adapter);

        findViewById(R.id.btnAddCustomProp).setOnClickListener(v -> addCustomPropRow());
        findViewById(R.id.btnSetStandardProps).setOnClickListener(v -> setStandardProperties());
        findViewById(R.id.btnSetCustomProps).setOnClickListener(v -> setCustomProperties());
        findViewById(R.id.btnExecuteOp).setOnClickListener(v -> executeOperation());
        findViewById(R.id.btnSaveProfile).setOnClickListener(v -> {
            Countly.sharedInstance().userProfile().save();
            Toast.makeText(this, "Profile saved to server", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btnClearProfile).setOnClickListener(v -> {
            Countly.sharedInstance().userProfile().clear();
            Toast.makeText(this, "Profile modifications cleared", Toast.LENGTH_SHORT).show();
        });

        // Add one default row
        addCustomPropRow();
    }

    private void addCustomPropRow() {
        // Reuse the same row_segmentation layout (key-value pair with remove button)
        View row = LayoutInflater.from(this).inflate(R.layout.row_segmentation, customPropsContainer, false);
        row.findViewById(R.id.btnRemoveSegment).setOnClickListener(v -> customPropsContainer.removeView(row));
        customPropsContainer.addView(row);
    }

    private String getText(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }

    private void setStandardProperties() {
        HashMap<String, Object> data = new HashMap<>();

        String name = getText(inputName);
        String username = getText(inputUsername);
        String email = getText(inputEmail);
        String org = getText(inputOrganization);
        String phone = getText(inputPhone);
        String gender = getText(inputGender);
        String byear = getText(inputBirthYear);
        String picture = getText(inputPictureUrl);

        if (!name.isEmpty()) data.put("name", name);
        if (!username.isEmpty()) data.put("username", username);
        if (!email.isEmpty()) data.put("email", email);
        if (!org.isEmpty()) data.put("organization", org);
        if (!phone.isEmpty()) data.put("phone", phone);
        if (!gender.isEmpty()) data.put("gender", gender);
        if (!byear.isEmpty()) data.put("byear", byear);
        if (!picture.isEmpty()) data.put("picture", picture);

        if (data.isEmpty()) {
            Toast.makeText(this, "Please fill at least one field", Toast.LENGTH_SHORT).show();
            return;
        }

        Countly.sharedInstance().userProfile().setProperties(data);
        Toast.makeText(this, "Standard properties set (" + data.size() + " fields)", Toast.LENGTH_SHORT).show();
    }

    private void setCustomProperties() {
        HashMap<String, Object> custom = new HashMap<>();
        for (int i = 0; i < customPropsContainer.getChildCount(); i++) {
            View row = customPropsContainer.getChildAt(i);
            EditText keyField = row.findViewById(R.id.inputSegKey);
            EditText valueField = row.findViewById(R.id.inputSegValue);
            String key = keyField.getText().toString().trim();
            String value = valueField.getText().toString().trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                try {
                    if (value.contains(".")) {
                        custom.put(key, Double.parseDouble(value));
                    } else {
                        custom.put(key, Integer.parseInt(value));
                    }
                } catch (NumberFormatException e) {
                    custom.put(key, value);
                }
            }
        }

        if (custom.isEmpty()) {
            Toast.makeText(this, "Please add at least one property", Toast.LENGTH_SHORT).show();
            return;
        }

        Countly.sharedInstance().userProfile().setProperties(custom);
        Toast.makeText(this, "Custom properties set (" + custom.size() + " fields)", Toast.LENGTH_SHORT).show();
    }

    private void executeOperation() {
        String key = getText(inputOpKey);
        String value = getText(inputOpValue);

        if (key.isEmpty()) {
            inputOpKey.setError("Key is required");
            return;
        }

        String operation = OPERATIONS[spinnerOperation.getSelectedItemPosition()];

        switch (operation) {
            case "increment":
                Countly.sharedInstance().userProfile().increment(key);
                break;
            case "incrementBy":
                try {
                    Countly.sharedInstance().userProfile().incrementBy(key, Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Value must be a number", Toast.LENGTH_SHORT).show();
                    return;
                }
                break;
            case "multiply":
                try {
                    Countly.sharedInstance().userProfile().multiply(key, Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Value must be a number", Toast.LENGTH_SHORT).show();
                    return;
                }
                break;
            case "saveMax":
                try {
                    Countly.sharedInstance().userProfile().saveMax(key, Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Value must be a number", Toast.LENGTH_SHORT).show();
                    return;
                }
                break;
            case "saveMin":
                try {
                    Countly.sharedInstance().userProfile().saveMin(key, Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Value must be a number", Toast.LENGTH_SHORT).show();
                    return;
                }
                break;
            case "setOnce":
                Countly.sharedInstance().userProfile().setOnce(key, value);
                break;
            case "push":
                Countly.sharedInstance().userProfile().push(key, value);
                break;
            case "pushUnique":
                Countly.sharedInstance().userProfile().pushUnique(key, value);
                break;
            case "pull":
                Countly.sharedInstance().userProfile().pull(key, value);
                break;
        }

        Toast.makeText(this, operation + " applied to '" + key + "'", Toast.LENGTH_SHORT).show();
    }
}
