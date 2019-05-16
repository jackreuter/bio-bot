package com.biobot.boxapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class InstallActivity extends Activity  implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    TextView textViewCityManhole;
    TextView textViewUserID;
    Spinner spinnerBoxID;
    CheckBox checkBoxNewBatteryInstalled;
    CheckBox checkBoxNewPanelInstalled;
    CheckBox checkBoxInletAssemblyPluggedIn;
    EditText editTextResetTime;
    EditText editTextLightTurnedGreen;
    Spinner spinnerLightStatus;
    ArrayAdapter<String> spinnerAdapterLightStatus;
    EditText editTextNotes;

    // cloud firestore database
    FirebaseFirestore db;

    // Google location services
    private GoogleApiClient googleApiClient;
    private LocationManager locationManager;
    private LocationRequest locationRequest;
    private Location location;
    private long UPDATE_INTERVAL = 2 * 1000;  /* 10 secs */
    private long FASTEST_INTERVAL = 2000; /* 2 sec */

    // global variables
    String userID;
    String cityID;
    String manholeID;
    String boxID;
    String lightStatusString;

    // camera request
    static final int REQUEST_IMAGE_CAPTURE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_install);

        //FOR LOCATION SERVICES
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        checkLocation(); //check whether location service is enable or not in your  phone

        // ui elements
        textViewCityManhole = (TextView) findViewById(R.id.textViewCityManhole);
        textViewUserID = (TextView) findViewById(R.id.textViewUserID);
        checkBoxNewBatteryInstalled = (CheckBox) findViewById(R.id.checkBoxNewBatteryInstalled);
        checkBoxNewPanelInstalled = (CheckBox) findViewById(R.id.checkBoxNewPanelInstalled);
        checkBoxInletAssemblyPluggedIn= (CheckBox) findViewById(R.id.checkBoxInletAssemblyPluggedIn);
        editTextResetTime = (EditText) findViewById(R.id.editTextResetTime);
        editTextLightTurnedGreen = (EditText) findViewById(R.id.editTextLightTurnedGreen);
        editTextNotes = (EditText) findViewById(R.id.editTextNotes);

        ArrayList<String> lightStatusOptions = new ArrayList();
        lightStatusOptions.add("Blinking");
        lightStatusOptions.add("Solid");
        spinnerLightStatus = (Spinner) findViewById(R.id.spinnerLightStatus);
        spinnerAdapterLightStatus = new ArrayAdapter<String>(this, R.layout.spinner_item, lightStatusOptions);
        spinnerAdapterLightStatus.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerLightStatus.setAdapter(spinnerAdapterLightStatus);

        spinnerBoxID = (Spinner) findViewById(R.id.spinnerBoxID);
        final ArrayAdapter spinnerBoxIDAdapter = new ArrayAdapter<String>(this, R.layout.spinner_item, new ArrayList<String>());
        spinnerBoxIDAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerBoxID.setAdapter(spinnerBoxIDAdapter);

        editTextResetTime.setInputType(InputType.TYPE_NULL);
        editTextLightTurnedGreen.setInputType(InputType.TYPE_NULL);

        // information from manhole selection and login
        Intent intent = getIntent();
        userID = intent.getStringExtra("user_id");
        cityID = intent.getStringExtra("city_id");
        manholeID = intent.getStringExtra("manhole_id");
        textViewCityManhole.setText(manholeID + ", " + cityID);
        textViewUserID.setText("Hi " + userID + "!");

        // create spinner for boxID selection
        // listen for changes in database to repopulate spinner
        db = FirebaseFirestore.getInstance();

        final CollectionReference boxIDsRef = db.collection("cities")
                .document(cityID)
                .collection("boxes");

        boxIDsRef.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@javax.annotation.Nullable QuerySnapshot snapshot, @javax.annotation.Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    Log.w("FIRESTORE", "Listen failed.", e);
                    return;
                }

                if (snapshot != null) {
                    updateSpinner(boxIDsRef, spinnerBoxIDAdapter);
                } else {
                    Log.d("FIRESTORE", "Current data: null");
                }
            }
        });

        // listen for boxID selection
        spinnerBoxID.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                boxID = parentView.getItemAtPosition(position).toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // your code here
            }
        });

        // create clock selectors for reset time and light turned green
        editTextResetTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Calendar cldr = Calendar.getInstance();
                int hour = cldr.get(Calendar.HOUR_OF_DAY);
                int minutes = cldr.get(Calendar.MINUTE);
                // time picker dialog
                TimePickerDialog picker = new TimePickerDialog(InstallActivity.this,
                        R.style.SpinnerTimePickerStyle,
                        new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(TimePicker tp, int sHour, int sMinute) {
                                if (sMinute < 10) {
                                    editTextResetTime.setText(sHour + ":0" + sMinute);
                                } else {
                                    editTextResetTime.setText(sHour + ":" + sMinute);
                                }
                            }
                        }, hour, minutes, true);
                picker.show();
            }
        });

        editTextLightTurnedGreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Calendar cldr = Calendar.getInstance();
                int hour = cldr.get(Calendar.HOUR_OF_DAY);
                int minutes = cldr.get(Calendar.MINUTE);
                // time picker dialog
                TimePickerDialog picker = new TimePickerDialog(InstallActivity.this,
                        R.style.SpinnerTimePickerStyle,
                        new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(TimePicker tp, int sHour, int sMinute) {
                                if (sMinute < 10) {
                                    editTextLightTurnedGreen.setText(sHour + ":0" + sMinute);
                                } else {
                                    editTextLightTurnedGreen.setText(sHour + ":" + sMinute);
                                }
                            }
                        }, hour, minutes, true);
                picker.show();
            }
        });

        // handle spinner selections
        spinnerLightStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                lightStatusString = parentView.getItemAtPosition(position).toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) { }

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
                        }
                    }
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        //CONNECT TO LOCATION SERVICES
        if (googleApiClient != null) {
            googleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        //DISCONNECT FROM LOCATION SERVICES
        if (googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }
    }

    public void onClickLogInfo(View view) {
        if (boxID != null &&
                checkBoxNewBatteryInstalled.isChecked() &&
                checkBoxNewPanelInstalled.isChecked() &&
                checkBoxInletAssemblyPluggedIn.isChecked() &&
                (editTextResetTime.getText().length() > 0) &&
                (editTextLightTurnedGreen.getText().length() > 0) &&
                (lightStatusString != null)
        ) {
            String locationString;
            if (location == null) {
                locationString = "unable to find location";
            } else {
                locationString = location.getLatitude() + " " + location.getLongitude();
            }

            InstallLog log = new InstallLog(
                    userID,
                    locationString,
                    boxID,
                    checkBoxNewBatteryInstalled.isChecked(),
                    checkBoxNewPanelInstalled.isChecked(),
                    checkBoxInletAssemblyPluggedIn.isChecked(),
                    editTextResetTime.getText().toString(),
                    editTextLightTurnedGreen.getText().toString(),
                    lightStatusString,
                    editTextNotes.getText().toString()
            );

            String timeStamp = getDateCurrentTimeZone(System.currentTimeMillis() / 1000);
            Map<String, String> install = new HashMap<>();

            // create install
            db.collection("cities")
                    .document(cityID)
                    .collection("manholes")
                    .document(manholeID)
                    .collection("deployments")
                    .document(timeStamp)
                    .set(install);

            // log install information
            db.collection("cities")
                    .document(cityID)
                    .collection("manholes")
                    .document(manholeID)
                    .collection("deployments")
                    .document(timeStamp)
                    .collection("install log")
                    .document("data")
                    .set(log)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            Log.d("FIRESTORE", "DocumentSnapshot successfully written!");
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.w("FIRESTORE", "Error writing document", e);
                        }
                    });


            largeToast("Install log sent to database", InstallActivity.this);
            finish();
        } else {
            largeToast("Please complete all fields", InstallActivity.this);
        }
    }

    /** get timestamp and format*/
    public String getDateCurrentTimeZone(long timestamp) {
        try {
            Calendar calendar = Calendar.getInstance();
            //TimeZone tz = TimeZone.getDefault();
            calendar.setTimeInMillis(timestamp * 1000);
            //calendar.add(Calendar.MILLISECOND, tz.getOffset(calendar.getTimeInMillis()));
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date currentTimeZone = (Date) calendar.getTime();
            return sdf.format(currentTimeZone);
        } catch (Exception e) {
        }
        return "";
    }

    /** logout current user and return to login screen */
    public void onClickLogout(View view) {
        Intent logoutIntent = new Intent(InstallActivity.this, LoginActivity.class);
        logoutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        logoutIntent.putExtra("logout", true);
        startActivity(logoutIntent);
    }

    /** back button same function as built in android back button */
    public void onClickBack(View view) {
        this.onBackPressed();
    }


    /** -----------------------------------LOCATION SERVICES-------------------------------- */

    /** required to overwrite */
    @Override
    public void onConnected(Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        startLocationUpdates();

        location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);

        if(location == null){
            startLocationUpdates();
        }
    }

    protected void startLocationUpdates() {
        // Create the location request
        locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(UPDATE_INTERVAL)
                .setFastestInterval(FASTEST_INTERVAL);
        // Request location updates
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient,
                locationRequest, this);
        Log.d("reque", "--->>>>");
    }

    /** required to overwrite */
    @Override
    public void onConnectionSuspended(int i) {
        Log.i("", "Connection Suspended");
        googleApiClient.connect();
    }

    /** required to overwrite */
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i("", "Connection failed. Error: " + connectionResult.getErrorCode());
    }

    /** required to overwrite */
    @Override
    public void onLocationChanged(Location location) { }

    private boolean checkLocation() {
        if(!isLocationEnabled()) {
            showAlert();
        }
        return isLocationEnabled();
    }

    /** create dialog to enable location usage */
    private void showAlert() {
        final AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Enable Location")
                .setMessage("Location Services set to 'Off'.\nEnable Location in order to " +
                        "write GPS coordinates to file")
                .setPositiveButton("Location Settings", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface paramDialogInterface, int paramInt) {

                        Intent myIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(myIntent);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface paramDialogInterface, int paramInt) {

                    }
                });
        dialog.show();
    }

    /** check if location services are enabled */
    private boolean isLocationEnabled() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
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
