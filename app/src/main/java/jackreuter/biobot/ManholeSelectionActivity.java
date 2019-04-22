package jackreuter.biobot;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

public class ManholeSelectionActivity extends Activity {

    // Cloud Firestore database
    FirebaseFirestore db;

    // UI elements
    TextView userIDTextView;
    Spinner citySpinner;
    Spinner manholeSpinner;
    ArrayAdapter<String> citySpinnerAdapter;
    ArrayAdapter<String> manholeSpinnerAdapter;

    // Global variables
    String cityID;
    String manholeID;
    String userID;
    String deploymentDate;
    final int DEPLOYMENT_LOG_SIZE = 7;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manhole_selection);

        // create UI
        userIDTextView = (TextView) findViewById(R.id.textViewUserID);
        citySpinner = (Spinner) findViewById(R.id.city_spinner);
        manholeSpinner = (Spinner) findViewById(R.id.manhole_spinner);
        citySpinnerAdapter = new ArrayAdapter<String>(this, R.layout.spinner_item, new ArrayList<String>());
        manholeSpinnerAdapter = new ArrayAdapter<String>(this, R.layout.spinner_item, new ArrayList<String>());

        Intent intent = getIntent();
        userID = intent.getStringExtra("user_id");
        userIDTextView.setText("Hi " + userID + "!");

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
                // clear and populate manhole spinner
                manholeSpinnerAdapter.clear();
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
                                    ArrayList<String> deploymentDates = new ArrayList<String>();
                                    for (QueryDocumentSnapshot document : task.getResult()) {
                                        deploymentDates.add(document.getId());
                                    }
                                    if (deploymentDates.size() > 0) {
                                        Collections.sort(deploymentDates);
                                        Collections.reverse(deploymentDates);
                                        deploymentDate = deploymentDates.get(0);
                                    } else {
                                        deploymentDate = "";
                                    }
                                } else {
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
        if (deploymentDate.equals("")) {
            Intent deploymentActivityIntent = new Intent(ManholeSelectionActivity.this, DeploymentActivity.class);
            deploymentActivityIntent.putExtra("user_id", userID);
            deploymentActivityIntent.putExtra("city_id", cityID);
            deploymentActivityIntent.putExtra("manhole_id", manholeID);
            startActivity(deploymentActivityIntent);

        } else {
            db.collection("cities")
                    .document(cityID)
                    .collection("manholes")
                    .document(manholeID)
                    .collection("deployments")
                    .document(deploymentDate)
                    .get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        Map<String, Object> deploymentData = document.getData();

                        // if most recent deployment has not been retrieved then start retrieval activity
                        if (deploymentData.size() == DEPLOYMENT_LOG_SIZE) {
                            Intent retrievalActivityIntent = new Intent(ManholeSelectionActivity.this, RetrievalActivity.class);
                            retrievalActivityIntent.putExtra("user_id", userID);
                            retrievalActivityIntent.putExtra("city_id", cityID);
                            retrievalActivityIntent.putExtra("manhole_id", manholeID);
                            retrievalActivityIntent.putExtra("deployment_date", deploymentDate);
                            startActivity(retrievalActivityIntent);
                        } else {
                            Intent deploymentActivityIntent = new Intent(ManholeSelectionActivity.this, DeploymentActivity.class);
                            deploymentActivityIntent.putExtra("user_id", userID);
                            deploymentActivityIntent.putExtra("city_id", cityID);
                            deploymentActivityIntent.putExtra("manhole_id", manholeID);
                            startActivity(deploymentActivityIntent);
                        }
                    } else {
                    }
                }
            });
        }

    }

    public void onClickLogout(View view) {
        Intent logoutIntent = new Intent(ManholeSelectionActivity.this, LoginActivity.class);
        logoutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        logoutIntent.putExtra("logout", true);
        startActivity(logoutIntent);
    }
}
