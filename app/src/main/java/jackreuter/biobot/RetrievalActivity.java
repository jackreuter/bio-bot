package jackreuter.biobot;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.widget.Button;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.Manifest;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

public class RetrievalActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    // Google location services
    private GoogleApiClient googleApiClient;
    private LocationManager locationManager;
    private LocationRequest locationRequest;
    private Location location;
    private long UPDATE_INTERVAL = 2 * 1000;  /* 10 secs */
    private long FASTEST_INTERVAL = 2000; /* 2 sec */

    // cloud firestore database
    FirebaseFirestore db;

    // file parsing
    public final String START_FILENAME = "!";
    public final String START_FILE = "\\$";
    public final String END_FILE = "\\^";
    public final String END_TRANSMISSION = "&";
    public final String INQUIRY = "~";
    public final String ID = "*";

    // must equal name field in provider_paths.xml
    public final String FOLDER_NAME = "data";

    // UI
    Button logoutButton, readButton;
    TextView cityManholeTextView, userIDTextView, installLogTextView, notesTextView;
    EditText notesEditText;
    Button buttonScanQrCode;
    RadioGroup radioGroupGreenLedStatus, radioGroupSamplePlacedOnIce;
    ImageView imageViewScanQrCodeCheck, imageViewReadDataCheck;
    ImageView imageViewGreedLedStatusAlert, imageViewScanQrCodeAlert, imageViewSamplePlacedOnIceAlert, imageViewReadDataAlert;

    // arduinoUSB
    public final String ACTION_USB_PERMISSION = "com.hariharan.arduinousb.USB_PERMISSION";
    UsbManager usbManager;
    IntentFilter filter;
    UsbDevice device;
    UsbDeviceConnection connection;
    com.felhr.usbserial.UsbSerialDevice serialPort;

    // global variables
    String[] filenames;
    String[] contents;
    String userID;
    String cityID;
    String manholeID;
    String installDate;
    String greenLedStatus;
    String samplePlacedOnIce;
    String qrCode;
    Boolean transmissionEnded;
    Boolean serialConnectionOpen;

    // QR intent request
    static final int QR_INTENT_REQUEST_CODE = 0;

    /** callback used to communicate with arduino, takes arduino data and parses into
     filenames[] and contents[] */
    com.felhr.usbserial.UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() { //Defining a Callback which triggers whenever data is read.
        @Override
        public void onReceivedData(byte[] arg0) {
            String data;
            try {
                data = new String(arg0, "UTF-8");
                if (data.length() > 0) {
                    String cue = data.substring(0, 1);
                    if (cue.equals(START_FILENAME)) {
                        /**
                         * example data:    !190111_I.TXT$TONS OF TEXT FAKE DATA BLAH BLAH BLAH^
                         *                  !190117_A.TXT$MORE FAKE DATA BLAH BLAH BLAHDI BLOO^
                         *                  &
                         */
                        //split data into array of files,
                        String[] files = data.split("\n");

                        //ditch the end character
                        filenames = new String[files.length - 1];
                        contents = new String[files.length - 1];

                        //split files into filenames and contents
                        for (int i = 0; i < files.length - 1; i++) {
                            String[] fileAndContents = files[i].split(START_FILE);
                            filenames[i] = fileAndContents[0].substring(1); //strip off the start and end characters
                            contents[i] = fileAndContents[1].substring(0, fileAndContents[1].length()-2);
                        }
                    } else {
                    }
                } else {
                }
                transmissionEnded = true;
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    };

    /** triggered when connect method is called, opens up serial connection with Arduino,
     sets communication parameters, uses mCallback to communicate */
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() { //Broadcast Receiver to automatically start and stop the Serial connection.
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
                boolean granted =
                        intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                if (granted) {
                    connection = usbManager.openDevice(device);
                    serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
                    if (serialPort != null) {
                        if (serialPort.open()) { //Set Serial Connection Parameters.
                            readButton.setEnabled(true); //Enable Buttons in UI
                            serialPort.setBaudRate(9600);
                            serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                            serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                            serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                            serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                            serialPort.read(mCallback); //
                            largeToast("Serial connection opened", RetrievalActivity.this);
                            serialConnectionOpen = true;

                        } else {
                            largeToast("Port not open", RetrievalActivity.this);
                        }
                    } else {
                        largeToast("Port is null", RetrievalActivity.this);
                    }
                } else {
                    largeToast("Permission not granted. Reconnect Teensy", RetrievalActivity.this);
                }
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                connectArduino();
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                endSerialConnection();
            }
        }

    };

    /** save variables during recreation, e.g. rotation*/
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        String notesTextEntered = notesEditText.getText().toString();
        if (notesTextEntered != null) {
            outState.putString("notesTextEntered", notesTextEntered);
        } else {
            outState.putString("notesTextEntered", "");
        }

        if (filenames != null) {
            outState.putStringArray("filenames", filenames);
        } else {
            outState.putStringArray("filenames", new String[0]);
        }
        if (contents != null) {
            outState.putStringArray("contents", contents);
        } else {
            outState.putStringArray("contents", new String[0]);
        }

    }

    /** create variables, receiver, try to connect in case already plugged in
     get variables from savedInstanceState if possible */
    @RequiresApi(api = 23)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Remove title bar
        //this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        //Remove notification bar
        //this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_retrieval);

        //TO COMMUNICATE WITH ARDUINO
        usbManager = (UsbManager) getSystemService(this.USB_SERVICE);

        //FOR LOCATION SERVICES
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        checkLocation(); //check whether location service is enable or not in your  phone

        // CLOUD FIRESTORE DATABASE SYNC
        db = FirebaseFirestore.getInstance();

        //CREATE UI
        logoutButton = (Button) findViewById(R.id.buttonLogout);
        cityManholeTextView = (TextView) findViewById(R.id.textViewCityManhole);
        userIDTextView = (TextView) findViewById(R.id.textViewUserID);
        installLogTextView = (TextView) findViewById(R.id.textViewInstallLog);
        notesTextView = (TextView) findViewById(R.id.textViewNotes);
        notesEditText = (EditText) findViewById(R.id.editTextNotes);
        readButton = (Button) findViewById(R.id.buttonRead);
        buttonScanQrCode = (Button) findViewById(R.id.buttonScanQrCode);
        imageViewScanQrCodeCheck = (ImageView) findViewById(R.id.imageViewScanQrCodeCheck);
        imageViewReadDataCheck = (ImageView) findViewById(R.id.imageViewReadDataCheck);
        imageViewGreedLedStatusAlert = (ImageView) findViewById(R.id.imageViewGreenLedStatusAlert);
        imageViewScanQrCodeAlert = (ImageView) findViewById(R.id.imageViewScanQrCodeAlert);
        imageViewSamplePlacedOnIceAlert = (ImageView) findViewById(R.id.imageViewSamplePlacedOnIceAlert);
        imageViewReadDataAlert = (ImageView) findViewById(R.id.imageViewReadDataAlert);
        radioGroupGreenLedStatus = (RadioGroup) findViewById(R.id.radioGroupGreenLedStatus);
        radioGroupSamplePlacedOnIce = (RadioGroup) findViewById(R.id.radioGroupSamplePlacedOnIce);

        // UI progress checks set to invisible
        imageViewScanQrCodeCheck.setVisibility(View.INVISIBLE);
        imageViewReadDataCheck.setVisibility(View.INVISIBLE);

        // get information from previous screens and set user ID view
        Intent intent = getIntent();
        userID = intent.getStringExtra("user_id");
        cityID = intent.getStringExtra("city_id");
        manholeID = intent.getStringExtra("manhole_id");
        installDate = intent.getStringExtra("install_date");
        cityManholeTextView.setText(manholeID + ", " + cityID);
        userIDTextView.setText("Hi " + userID + "!");

        // get install log from database and set text view
        installLogTextView.setText("");
        db.collection("cities")
                .document(cityID)
                .collection("manholes")
                .document(manholeID)
                .collection("deployments")
                .document(installDate)
                .collection("install log")
                .document("data")
                .get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            Map<String, Object> installData = document.getData();
                            if (installData == null) {
                                installLogTextView.append("No install log found");
                            } else {
                                Log.d("ayy", installData.keySet()+"");
                                installLogTextView.append("Device installed on: " + installDate + "\n");
                                installLogTextView.append("Installed by: " + installData.get("deploymentUser") + "\n");
                                installLogTextView.append("Notes: " + installData.get("deploymentNotes"));
                            }
                        }
                    }
                });

        // set listener for green LED status radio buttons
        radioGroupGreenLedStatus.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener()
        {
            public void onCheckedChanged(RadioGroup group, int checkedId)
            {
                imageViewGreedLedStatusAlert.setVisibility(View.INVISIBLE);
                RadioButton checkedRadioButton = (RadioButton) group.findViewById(checkedId);
                greenLedStatus = checkedRadioButton.getText().toString();
            }
        });

        // set listener for sample placed on ice radio buttons
        radioGroupSamplePlacedOnIce.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener()
        {
            public void onCheckedChanged(RadioGroup group, int checkedId)
            {
                imageViewSamplePlacedOnIceAlert.setVisibility(View.INVISIBLE);
                RadioButton checkedRadioButton = (RadioButton) group.findViewById(checkedId);
                samplePlacedOnIce = checkedRadioButton.getText().toString();
            }
        });

        //IF SCREEN ROTATED OR APP PAUSED FOR SOME REASON
        if (savedInstanceState != null) {
            notesEditText.setText(savedInstanceState.getString("notesTextEntered"), TextView.BufferType.EDITABLE);
            filenames = savedInstanceState.getStringArray("filenames");
            contents = savedInstanceState.getStringArray("contents");
        }

        //INITIALIZE GLOBAL VARIABLES
        transmissionEnded = false;
        transmissionEnded = false;
        serialConnectionOpen = false;

    }

    // save QR code image
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == QR_INTENT_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                String text = data.getDataString();
                qrCode = text;
                imageViewScanQrCodeCheck.setVisibility(View.VISIBLE);
                imageViewScanQrCodeAlert.setVisibility(View.INVISIBLE);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        //REGISTER ARDUINO BROADCAST RECEIVER
        filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(broadcastReceiver, filter);

        //CONNECT TO LOCATION SERVICES
        if (googleApiClient != null) {
            googleApiClient.connect();
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
        //UNREGISTER BROADCAST RECEIVER
        unregisterReceiver(broadcastReceiver);

        //DISCONNECT FROM ARDUINO
        endSerialConnection();

        //DISCONNECT FROM LOCATION SERVICES
        if (googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }
    }

    /** create a dialog to select one of three most recent installs */
    public void onClickWrongInstallDate(View view) {
        // custom dialog
        final Dialog dialog = new Dialog(RetrievalActivity.this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.radiobutton_dialog);
        List<String> stringList = new ArrayList<>();  // here is list
        for(int i=0;i<5;i++) {
            stringList.add("RadioButton " + (i + 1));
        }

        final RadioGroup rg = (RadioGroup) dialog.findViewById(R.id.radio_group);
        Button buttonContinue = (Button) dialog.findViewById(R.id.buttonContinue);
        Button buttonCancel = (Button) dialog.findViewById(R.id.buttonCancel);

        //get past three install dates to create radio buttons
        db.collection("cities")
                .document(cityID)
                .collection("manholes")
                .document(manholeID)
                .collection("deployments")
                .get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if (task.isSuccessful()) {
                    QuerySnapshot collection = task.getResult();
                    List<DocumentSnapshot> deployments = collection.getDocuments();
                    int totalDeployments = deployments.size();
                    ArrayList<String> previousInstallDates = new ArrayList<>();

                    if (totalDeployments > 0) { previousInstallDates.add(deployments.get(totalDeployments - 1).getId()); }
                    if (totalDeployments > 1) { previousInstallDates.add(deployments.get(totalDeployments-2).getId()); }
                    if (totalDeployments > 2) { previousInstallDates.add(deployments.get(totalDeployments-3).getId()); }

                    for (String previousInstallDate : previousInstallDates) {
                        RadioButton rb = new RadioButton(RetrievalActivity.this); // dynamically creating RadioButton and adding to RadioGroup.
                        rb.setText(previousInstallDate);
                        rb.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.font_size_large));

                        RadioGroup.LayoutParams params = new RadioGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        params.setMargins(
                                (int) getResources().getDimension(R.dimen.inner_margin),
                                0,
                                0,
                                (int) getResources().getDimension(R.dimen.inner_margin));
                        rb.setLayoutParams(params);
                        rg.addView(rb);
                    }
                } else {
                }
            }
        });

        buttonContinue.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                // get selected radio button from radioGroup
                int selectedId = rg.getCheckedRadioButtonId();

                if (selectedId != -1) {
                    // find the radiobutton by returned id
                    RadioButton selectedRadioButton = (RadioButton) dialog.findViewById(selectedId);
                    String selectedInstallDate = selectedRadioButton.getText().toString();

                    // restart activity with new install date
                    Intent restartActivityIntent = new Intent(RetrievalActivity.this, RetrievalActivity.class);
                    restartActivityIntent.putExtra("user_id", userID);
                    restartActivityIntent.putExtra("city_id", cityID);
                    restartActivityIntent.putExtra("manhole_id", manholeID);
                    restartActivityIntent.putExtra("install_date", selectedInstallDate);
                    finish();
                    startActivity(restartActivityIntent);
                } else {
                    largeToast("No date selected", RetrievalActivity.this);
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

    /** open camera to scan qr code */
    public void onClickScanQrCode(View view) {
        Intent qrCodeScannerActivityIntent = new Intent(RetrievalActivity.this, QrCodeScannerActivity.class);
        RetrievalActivity.this.startActivityForResult(qrCodeScannerActivityIntent, QR_INTENT_REQUEST_CODE);
    }

    /** send cue to read file from arduino */
    public void onClickRead(View view) {
        if (!serialConnectionOpen) {
            largeToast("Serial connection not open. Reconnect Teensy", RetrievalActivity.this);
        } else {
            transmissionEnded = false;
            if (checkLocation()) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    String[] permissions = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};
                    requestPermissions(permissions, 1);
                    return;
                }

                location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
                if (location == null) {
                    startLocationUpdates();
                }

                if (location == null) {
                    largeToast("Location not detected, wait and try again", RetrievalActivity.this);
                } else {
                    serialPort.write(INQUIRY.getBytes());
                    while (!transmissionEnded) { }
                    imageViewReadDataCheck.setVisibility(View.VISIBLE);
                    imageViewReadDataAlert.setVisibility(View.INVISIBLE);
                }
            }
        }
    }

    /** upload data to database and return to manhole selection screen */
    public void onClickDone(View view) {
        if (greenLedStatus != null &&
                qrCode != null &&
                samplePlacedOnIce != null &&
                transmissionEnded) {
            saveToDatabase();
        } else {
            largeToast("Please complete all fields", RetrievalActivity.this);
        }
    }

    public void saveToDatabase() {
        String locationString;
        if (location == null) {
            locationString = "unable to find location";
        } else {
            locationString = location.getLatitude() + " " + location.getLongitude();
        }

        RetrievalLog log = new RetrievalLog(
                userID,
                locationString,
                greenLedStatus,
                samplePlacedOnIce,
                notesEditText.getText().toString(),
                qrCode
        );

        db = FirebaseFirestore.getInstance();
        db.collection("cities")
                .document(cityID)
                .collection("manholes")
                .document(manholeID)
                .collection("deployments")
                .document(installDate)
                .collection("retrieval log")
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

        Intent feedbackActivityIntent = new Intent(RetrievalActivity.this, FeedbackActivity.class);
        feedbackActivityIntent.putExtra("user_id", userID);
        feedbackActivityIntent.putExtra("manhole_id", manholeID);
        feedbackActivityIntent.putExtra("city_id", cityID);
        RetrievalActivity.this.startActivity(feedbackActivityIntent);

    }

    /** log out user and send back to login screen */
    public void onClickLogout(View view) {
        Intent logoutIntent = new Intent(RetrievalActivity.this, LoginActivity.class);
        logoutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        logoutIntent.putExtra("logout", true);
        startActivity(logoutIntent);
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

    /** connects arduino if found, triggers broadcastReceiver to open up a serial connection */
    public void connectArduino() {
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            boolean keep = true;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                //int deviceVID = device.getVendorId();
                //Toast.makeText(RetrievalActivity.this, "Vendor ID: " + Integer.toString(deviceVID), Toast.LENGTH_LONG).show();
                //if (deviceVID == 6790)//Arduino Vendor ID, not sure where to find
                try {
                    PendingIntent pi = PendingIntent.getBroadcast(this, 0,
                            new Intent(ACTION_USB_PERMISSION), 0);
                    usbManager.requestPermission(device, pi);
                    keep = false;

                } catch (Exception e) {
                    connection = null;
                    device = null;
                }

                if (!keep)
                    break;
            }
        } else {
            largeToast("No devices found", RetrievalActivity.this);
        }
    }

    /** end connection if disconnected */
    public void endSerialConnection() {
        if (serialPort != null && serialConnectionOpen) {
            serialPort.close();
            largeToast("Serial connection closed", RetrievalActivity.this);
        }
        serialConnectionOpen = false;
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

