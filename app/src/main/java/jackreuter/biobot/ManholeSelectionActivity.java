package jackreuter.biobot;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.util.CollectionUtils;
import com.google.android.gms.common.util.MapUtils;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import org.w3c.dom.Document;

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
        final ArrayAdapter citySpinnerAdapter = new ArrayAdapter<String>(this, R.layout.spinner_item, new ArrayList<String>());
        final ArrayAdapter manholeSpinnerAdapter = new ArrayAdapter<String>(this, R.layout.spinner_item, new ArrayList<String>());
        citySpinnerAdapter.setDropDownViewResource(R.layout.spinner_item);
        citySpinner.setAdapter(citySpinnerAdapter);
        manholeSpinnerAdapter.setDropDownViewResource(R.layout.spinner_item);
        manholeSpinner.setAdapter(manholeSpinnerAdapter);

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

        // handle spinner selections
        citySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                // clear and populate manhole spinner
                cityID = parentView.getItemAtPosition(position).toString();
                final CollectionReference manholesRef = db.collection("cities")
                        .document(cityID)
                        .collection("manholes");
                manholesRef.addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@javax.annotation.Nullable QuerySnapshot snapshot, @javax.annotation.Nullable FirebaseFirestoreException e) {
                        if (e != null) {
                            Log.w("FIRESTORE", "Listen failed.", e);
                            return;
                        }

                        if (snapshot != null) {
                            updateSpinner(manholesRef, manholeSpinnerAdapter);
                        } else {
                            Log.d("FIRESTORE", "Current data: null");
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
                // listen for changes in selected manhole deployments to check if deployed
                manholeID = parentView.getItemAtPosition(position).toString();
                final CollectionReference deploymentsRef = db.collection("cities")
                        .document(cityID)
                        .collection("manholes")
                        .document(manholeID)
                        .collection("deployments");
                deploymentsRef.addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@javax.annotation.Nullable QuerySnapshot snapshot, @javax.annotation.Nullable FirebaseFirestoreException e) {
                        if (e != null) {
                            Log.w("FIRESTORE", "Listen failed.", e);
                            return;
                        }

                        if (snapshot != null) {
                            ArrayList<String> deploymentDates = new ArrayList<String>();
                            for (DocumentSnapshot document : snapshot.getDocuments()) {
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
                            Log.d("FIRESTORE", "Current data: null");
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

    // check database for collection, update spinner with the results
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
                    .collection("retrieval log")
                    .document("data")
                    .get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        // if most recent deployment has not been retrieved then start retrieval activity
                        if (document.getData() == null) {
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
