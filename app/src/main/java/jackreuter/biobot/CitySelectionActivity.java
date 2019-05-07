package jackreuter.biobot;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;

public class CitySelectionActivity extends Activity {

    // Cloud Firestore database
    FirebaseFirestore db;

    // UI elements
    TextView userIDTextView;
    Spinner citySpinner;

    // Global variables
    String cityID;
    String userID;
    String installDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_city_selection);

        // create UI
        userIDTextView = (TextView) findViewById(R.id.textViewUserID);
        citySpinner = (Spinner) findViewById(R.id.city_spinner);
        final ArrayAdapter citySpinnerAdapter = new ArrayAdapter<String>(this, R.layout.spinner_item, new ArrayList<String>());
        citySpinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        citySpinner.setAdapter(citySpinnerAdapter);

        Intent intent = getIntent();
        userID = intent.getStringExtra("user_id");
        userIDTextView.setText("Hi " + userID + "!");

        // CLOUD FIRESTORE DATABASE SYNC
        db = FirebaseFirestore.getInstance();

        // listen for changes in database to repopulate spinner
        final CollectionReference citiesRef = db.collection("cities");
        citiesRef.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@javax.annotation.Nullable QuerySnapshot snapshot, @javax.annotation.Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    Log.w("FIRESTORE", "Listen failed.", e);
                    return;
                }

                if (snapshot != null) {
                    updateSpinner(citiesRef, citySpinnerAdapter);
                } else {
                    Log.d("FIRESTORE", "Current data: null");
                }
            }
        });

        // listen for city selection
        citySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                cityID = parentView.getItemAtPosition(position).toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // your code here
            }
        });
    }

    /** check database for collection, update spinner with the results */
    public void updateSpinner(CollectionReference collection, final ArrayAdapter spinnerAdapter) {
        spinnerAdapter.clear();
        spinnerAdapter.notifyDataSetChanged();
        collection.get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                spinnerAdapter.add(document.getId());
                                spinnerAdapter.notifyDataSetChanged();
                            }
                        } else {
                            //Log.w("data ayy", "Error getting documents.", task.getException());
                        }
                    }
                });
    }

    /** move on to manhole selection screen */
    public void onClickContinue(View view) {
        if (cityID == null) {
            largeToast("Must select a city", CitySelectionActivity.this);
        } else {
            /**
            Intent manholeSelectionActivityIntent = new Intent(CitySelectionActivity.this, ManholeSelectionActivity.class);
            manholeSelectionActivityIntent.putExtra("user_id", userID);
            manholeSelectionActivityIntent.putExtra("city_id", cityID);
            startActivity(manholeSelectionActivityIntent);
             */

            // just to test feedback screen
            Intent feedbackActivityIntent = new Intent(CitySelectionActivity.this, FeedbackActivity.class);
            startActivity(feedbackActivityIntent);
        }
    }

    /** log out current user and return to login screen */
    public void onClickLogout(View view) {
        Intent logoutIntent = new Intent(CitySelectionActivity.this, LoginActivity.class);
        logoutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        logoutIntent.putExtra("logout", true);
        startActivity(logoutIntent);
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
