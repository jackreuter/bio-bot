package jackreuter.biobot;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import org.w3c.dom.Text;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class DeploymentActivity extends Activity  implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    TextView userIDTextView;
    EditText editTextBoxID;
    EditText editTextResetTime;
    EditText editTextLightTurnedGreen;
    Spinner lightBlinkingSpinner;
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
    String lightBlinkingString;

    // shared preference to store login

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deployment);

        //FOR LOCATION SERVICES
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        checkLocation(); //check whether location service is enable or not in your  phone

        // ui elements
        userIDTextView = (TextView) findViewById(R.id.textViewUserID);
        editTextBoxID = (EditText) findViewById(R.id.editTextBoxID);
        editTextResetTime = (EditText) findViewById(R.id.editTextResetTime);
        editTextLightTurnedGreen = (EditText) findViewById(R.id.editTextLightTurnedGreen);
        lightBlinkingSpinner = (Spinner) findViewById(R.id.lightBlinkingSpinner);
        editTextNotes = (EditText) findViewById(R.id.editTextNotes);

        editTextResetTime.setInputType(InputType.TYPE_NULL);
        editTextLightTurnedGreen.setInputType(InputType.TYPE_NULL);

        // information from manhole selection and login
        Intent intent = getIntent();
        userID = intent.getStringExtra("user_id");
        cityID = intent.getStringExtra("city_id");
        manholeID = intent.getStringExtra("manhole_id");
        userIDTextView.setText("Hi " + userID + "!");

        // create clock selectors for reset time and light turned green
        editTextResetTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Calendar cldr = Calendar.getInstance();
                int hour = cldr.get(Calendar.HOUR_OF_DAY);
                int minutes = cldr.get(Calendar.MINUTE);
                // time picker dialog
                TimePickerDialog picker = new TimePickerDialog(DeploymentActivity.this,
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
                TimePickerDialog picker = new TimePickerDialog(DeploymentActivity.this,
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
        lightBlinkingSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                lightBlinkingString = parentView.getItemAtPosition(position).toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) { }

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
        String locationString;
        if (location == null) {
            locationString = "unable to find location";
        } else {
            locationString = location.getLatitude() + " " + location.getLongitude();
        }

        DeploymentLog log = new DeploymentLog(
                userID,
                locationString,
                editTextBoxID.getText().toString(),
                editTextResetTime.getText().toString(),
                editTextLightTurnedGreen.getText().toString(),
                lightBlinkingString,
                editTextNotes.getText().toString()
        );

        db = FirebaseFirestore.getInstance();
        db.collection("cities")
                .document(cityID)
                .collection("manholes")
                .document(manholeID)
                .collection("deployments")
                .document(getDateCurrentTimeZone(System.currentTimeMillis() / 1000))
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


        Toast.makeText(this, "Deployment information sent to database", Toast.LENGTH_SHORT).show();
        finish();
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

    public void onClickLogout(View view) {
        Intent logoutIntent = new Intent(DeploymentActivity.this, LoginActivity.class);
        logoutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        logoutIntent.putExtra("logout", true);
        startActivity(logoutIntent);
    }

    /** -----------------------------------LOCATION SERVICES-------------------------------- */

    /** required to overwrite */
    @Override
    public void onConnected(Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
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
            // TODO: Consider calling
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
}
