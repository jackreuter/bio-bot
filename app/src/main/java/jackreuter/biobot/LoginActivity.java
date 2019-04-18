package jackreuter.biobot;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class LoginActivity extends Activity {

    Button loginButton;
    EditText userIDEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Remove title bar
        //this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        //Remove notification bar
        //this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_login);

        //REQUEST PERMISSIONS
        String[] permissions = {Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE};
        requestPermissions(permissions, 1);

        //CHECK IF LOGGED IN
        SharedPreferences pref = getSharedPreferences("MyPref",
                Context.MODE_PRIVATE);
        if (pref.contains("user_id")) {
            Intent manholeSelectionActivityIntent = new Intent(LoginActivity.this, ManholeSelectionActivity.class);
            manholeSelectionActivityIntent.putExtra("user_id", pref.getString("user_id", ""));
            startActivity(manholeSelectionActivityIntent);
        }

        loginButton = findViewById(R.id.buttonLogin);
        userIDEditText = findViewById(R.id.editTextUserID);

    }

    /** Pull text from editTextLogin and log user in if non-empty */
    public void onClickLogin(View view) {
        final String textEntered = userIDEditText.getText().toString();
        if (textEntered.equals("")) {
            Toast.makeText(LoginActivity.this, "Must enter User ID", Toast.LENGTH_LONG).show();
        } else {
            new Thread(new Runnable() {
                public void run() {
                    // running shared preference editor on separate thread to avoid issues with UI thread (manholeEditText was uneditable)
                    SharedPreferences pref = getApplicationContext().getSharedPreferences("MyPref", Context.MODE_PRIVATE); // 0 - for private mode
                    SharedPreferences.Editor editor = pref.edit();
                    editor.putString("user_id", textEntered);
                    editor.commit();
                }
            }).start();

            Intent manholeSelectionActivityIntent = new Intent(LoginActivity.this, ManholeSelectionActivity.class);
            manholeSelectionActivityIntent.putExtra("user_id", textEntered);
            startActivity(manholeSelectionActivityIntent);
        }
    }
}
