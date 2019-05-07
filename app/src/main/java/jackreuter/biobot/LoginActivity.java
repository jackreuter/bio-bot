package jackreuter.biobot;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class LoginActivity extends Activity {

    Button loginButton;
    EditText userIDEditText;
    SharedPreferences pref;
    SharedPreferences.Editor editor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        //UI ELEMENTS
        loginButton = findViewById(R.id.buttonLogin);
        userIDEditText = findViewById(R.id.editTextUserID);

        //REQUEST PERMISSIONS
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE};
        requestPermissions(permissions, 1);

        //preference editor to save login info
        pref = getApplicationContext().getSharedPreferences("MyPref", Context.MODE_PRIVATE); // 0 - for private mode
        editor = pref.edit();

        //CHECK IF STARTED FROM LOGOUT INTENT
        Intent intent = getIntent();
        boolean logout = intent.getBooleanExtra("logout", false);
        if (logout) {
            editor.remove("user_id");
            editor.apply();

        //CHECK IF LOGGED IN
        } else if (pref.contains("user_id")) {
            Intent citySelectionActivityIntent = new Intent(LoginActivity.this, CitySelectionActivity.class);
            citySelectionActivityIntent.putExtra("user_id", pref.getString("user_id", ""));
            startActivity(citySelectionActivityIntent);
        }

    }

    /** Pull text from editTextLogin and log user in if non-empty */
    public void onClickLogin(View view) {
        final String textEntered = userIDEditText.getText().toString();
        if (textEntered.equals("")) {
            largeToast("Must enter User ID", LoginActivity.this);
        } else {
            editor.putString("user_id", textEntered);
            editor.apply();

            Intent citySelectionActivityIntent = new Intent(LoginActivity.this, CitySelectionActivity.class);
            citySelectionActivityIntent.putExtra("user_id", textEntered);
            startActivity(citySelectionActivityIntent);
        }
    }

    /** increase size of toast text */
    public void largeToast(String message, Context context) {
        Toast toast = Toast.makeText(context, message, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, 0);
        ViewGroup group = (ViewGroup) toast.getView();
        TextView messageTextView = (TextView) group.getChildAt(0);
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.font_size_large));
        toast.show();
    }
}
