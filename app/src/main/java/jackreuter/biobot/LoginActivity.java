package jackreuter.biobot;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class LoginActivity extends Activity {

    Button loginButton;
    EditText userIDEditText;
    SharedPreferences pref;
    SharedPreferences.Editor editor;

    // for admin screen access
    private int count = 0;
    private long startMillis=0;
    private final String ADMIN_PASSWORD = "biobot";

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

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        int eventaction = event.getAction();
        if (eventaction == MotionEvent.ACTION_UP) {

            //get system current milliseconds
            long time= System.currentTimeMillis();


            //if it is the first time, or if it has been more than 3 seconds since the first tap ( so it is like a new try), we reset everything
            if (startMillis==0 || (time-startMillis> 3000) ) {
                startMillis=time;
                count=1;
            }
            //it is not the first, and it has been  less than 3 seconds since the first
            else{ //  time-startMillis< 3000
                count++;
            }

            if (count==5) {
                final Dialog dialog = new Dialog(LoginActivity.this);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setContentView(R.layout.admin_dialog);

                final EditText editTextPassword = (EditText) dialog.findViewById(R.id.editTextPassword);
                Button buttonContinue = (Button) dialog.findViewById(R.id.buttonContinue);
                Button buttonCancel = (Button) dialog.findViewById(R.id.buttonCancel);

                buttonContinue.setOnClickListener(new Button.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // get selected radio button from radioGroup
                        if (editTextPassword.getText().toString().equals(ADMIN_PASSWORD)) {
                            Intent adminActivityIntent= new Intent(LoginActivity.this, AdminActivity.class);
                            startActivity(adminActivityIntent);
                        } else {
                            largeToast("Wrong password", LoginActivity.this);
                        }
                    }
                });

                buttonCancel.setOnClickListener(new Button.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dialog.dismiss();
                    }
                });

                dialog.show();
            }
            return true;
        }
        return false;
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
