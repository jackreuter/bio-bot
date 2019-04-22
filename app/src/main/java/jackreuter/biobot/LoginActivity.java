package jackreuter.biobot;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
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
        String[] permissions = {Manifest.permission.ACCESS_COARSE_LOCATION,
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
            Intent manholeSelectionActivityIntent = new Intent(LoginActivity.this, ManholeSelectionActivity.class);
            manholeSelectionActivityIntent.putExtra("user_id", pref.getString("user_id", ""));
            startActivity(manholeSelectionActivityIntent);
        }

    }

    /** Pull text from editTextLogin and log user in if non-empty */
    public void onClickLogin(View view) {
        final String textEntered = userIDEditText.getText().toString();
        if (textEntered.equals("")) {
            Toast.makeText(LoginActivity.this, "Must enter User ID", Toast.LENGTH_LONG).show();
        } else {
            editor.putString("user_id", textEntered);
            editor.apply();

            Intent manholeSelectionActivityIntent = new Intent(LoginActivity.this, ManholeSelectionActivity.class);
            manholeSelectionActivityIntent.putExtra("user_id", textEntered);
            startActivity(manholeSelectionActivityIntent);
        }
    }
}
