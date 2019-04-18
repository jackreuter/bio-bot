package jackreuter.biobot;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class ManholeSelectionActivity extends Activity {

    // Cloud Firestore database
    FirebaseFirestore db;

    // Dropdown selection menus
    Spinner citySpinner;
    Spinner manholeSpinner;
    ArrayAdapter<String> citySpinnerAdapter;
    ArrayAdapter<String> manholeSpinnerAdapter;

    // Global variables
    String cityID;
    String manholeID;
    String userID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manhole_selection);

        // create UI
        citySpinner = (Spinner) findViewById(R.id.city_spinner);
        manholeSpinner = (Spinner) findViewById(R.id.manhole_spinner);
        citySpinnerAdapter = new ArrayAdapter<String>(this, R.layout.spinner_item, new ArrayList<String>());
        manholeSpinnerAdapter = new ArrayAdapter<String>(this, R.layout.spinner_item, new ArrayList<String>());

        Intent intent = getIntent();
        userID = intent.getStringExtra("user_id");

        // CLOUD FIRESTORE DATABASE SYNC
        db = FirebaseFirestore.getInstance();

        // populate city spinner
        citySpinnerAdapter.setDropDownViewResource(R.layout.spinner_item);
        citySpinner.setAdapter(citySpinnerAdapter);

        manholeSpinnerAdapter.setDropDownViewResource(R.layout.spinner_item);
        manholeSpinner.setAdapter(manholeSpinnerAdapter);

        db.collection("cities")
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                citySpinnerAdapter.add(document.getId());
                                citySpinnerAdapter.notifyDataSetChanged();
                            }
                        } else {
                            //Log.w("data ayy", "Error getting documents.", task.getException());
                        }
                    }
                });

        // handle spinner selections
        citySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                // populate manhole spinner
                cityID = parentView.getItemAtPosition(position).toString();
                db.collection("cities")
                        .document(cityID)
                        .collection("manholes")
                        .get()
                        .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                            @Override
                            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                if (task.isSuccessful()) {
                                    for (QueryDocumentSnapshot document : task.getResult()) {
                                        manholeSpinnerAdapter.add(document.getId());
                                        manholeSpinnerAdapter.notifyDataSetChanged();
                                    }
                                } else {
                                    //Log.w("data ayy", "Error getting documents.", task.getException());
                                }
                            }
                        });
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // your code here
            }

        });

        // handle spinner selections
        manholeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                // find most recent deployment, see if unfinished
                manholeID = parentView.getItemAtPosition(position).toString();
                db.collection("cities")
                        .document(cityID)
                        .collection("manholes")
                        .document(manholeID)
                        .collection("deployments")
                        .get()
                        .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                            @Override
                            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                if (task.isSuccessful()) {
                                    for (QueryDocumentSnapshot document : task.getResult()) {
                                        Log.d("ayy", "deployment "+document.getId());
                                    }
                                } else {
                                    //Log.w("data ayy", "Error getting documents.", task.getException());
                                }
                            }
                        });

            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // your code here
            }

        });



    }

    public void onClickContinue(View view) {
        Intent deploymentActivityIntent = new Intent(ManholeSelectionActivity.this, DeploymentActivity.class);
        deploymentActivityIntent.putExtra("user_id", userID);
        deploymentActivityIntent.putExtra("city_id", cityID);
        deploymentActivityIntent.putExtra("manhole_id", manholeID);
        startActivity(deploymentActivityIntent);
    }
}
