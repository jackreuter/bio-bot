package jackreuter.biobot;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.widget.Button;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.io.PrintWriter;

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
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

public class MainActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    // Google location services
    private GoogleApiClient googleApiClient;
    private LocationManager locationManager;
    private LocationRequest locationRequest;
    private Location location;
    private long UPDATE_INTERVAL = 2 * 1000;  /* 10 secs */
    private long FASTEST_INTERVAL = 2000; /* 2 sec */

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
    TextView userIDTextView, manholeTextView, notesTextView;
    EditText manholeEditText, notesEditText;

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
    String feedBackString;
    String userID;
    Boolean transmissionEnded;
    Boolean serialConnectionOpen;

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

                        String feedback = Integer.toString(files.length - 1) + " files found on box:\n";

                        //split files into filenames and contents
                        for (int i = 0; i < files.length - 1; i++) {
                            String[] fileAndContents = files[i].split(START_FILE);
                            filenames[i] = fileAndContents[0].substring(1); //strip off the start and end characters
                            contents[i] = fileAndContents[1].substring(0, fileAndContents[1].length()-2);
                            feedback += filenames[i] + "\n";
                        }
                        feedBackString += feedback + "\n";
                    } else {
                        feedBackString += data + "\n\n";
                    }
                } else {
                    feedBackString += "No data received\n\n";
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
                            Toast.makeText(MainActivity.this, "Serial connection opened", Toast.LENGTH_LONG).show();
                            serialConnectionOpen = true;

                        } else {
                            Toast.makeText(MainActivity.this, "Port not open", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "Port is null", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Permission not granted", Toast.LENGTH_LONG).show();
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

        String manholeTextEntered = manholeEditText.getText().toString();
        if (manholeTextEntered != null) {
            outState.putString("manholeTextEntered", manholeTextEntered);
        } else {
            outState.putString("manholeTextEntered", "");
        }

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

        setContentView(R.layout.activity_main);

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

        //TRY TO CONNECT
        connectArduino();

        //CREATE UI
        logoutButton = (Button) findViewById(R.id.buttonLogout);
        userIDTextView = (TextView) findViewById(R.id.textViewUserID);
        manholeTextView = (TextView) findViewById(R.id.textViewManholeLocation);
        manholeEditText = (EditText) findViewById(R.id.editTextManholeLocation);
        notesTextView = (TextView) findViewById(R.id.textViewNotes);
        notesEditText = (EditText) findViewById(R.id.editTextNotes);
        readButton = (Button) findViewById(R.id.buttonRead);

        Intent intent = getIntent();
        userID = intent.getStringExtra("user_id");
        userIDTextView.setText("Hi " + userID + "!");

        //IF SCREEN ROTATED OR APP PAUSED FOR SOME REASON
        if (savedInstanceState != null) {
            manholeEditText.setText(savedInstanceState.getString("manholeTextEntered"), TextView.BufferType.EDITABLE);
            notesEditText.setText(savedInstanceState.getString("notesTextEntered"), TextView.BufferType.EDITABLE);
            filenames = savedInstanceState.getStringArray("filenames");
            contents = savedInstanceState.getStringArray("contents");
        }

        //INITIALIZE GLOBAL VARIABLES
        feedBackString = "";
        transmissionEnded = false;
        serialConnectionOpen = false;

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

        //DISCONNECT FROM LOCATION SERVICES
        if (googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }
    }

    /** send cue to read file from arduino */
    public void onClickRead(View view) {
        if (!serialConnectionOpen) {
            Toast.makeText(MainActivity.this, "Serial connection not open. Reconnect Teensy", Toast.LENGTH_LONG).show();
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

                String manholeLocation = manholeEditText.getText().toString();
                if (manholeLocation.equals("")) {
                    Toast.makeText(MainActivity.this, "Must enter manhole location", Toast.LENGTH_LONG).show();

                } else {

                    String locationString = "Location not detected";
                    if (location == null) {
                        Toast.makeText(MainActivity.this, "Location not detected, wait and try again", Toast.LENGTH_LONG).show();
                    } else {
                        locationString = location.getLatitude() + " " + location.getLongitude();

                        //save metadata string
                        String metadataString = "User ID: \t" + userID +
                                "\nRetrieval date: \t" + getDateCurrentTimeZone(System.currentTimeMillis() / 1000) +
                                "\nManhole location: \t" + manholeLocation +
                                "\nGPS coordinates: \t" + locationString +
                                "\nNotes: \t" + notesEditText.getText().toString() +
                                "\n";

                        feedBackString += metadataString + "\n";
                        serialPort.write(INQUIRY.getBytes());
                        manholeEditText.getText().clear();
                        notesEditText.getText().clear();

                        while (!transmissionEnded) { }
                        appendMetadataToFileContents(metadataString);
                        save();
                    }
                }
            }
        }
    }

    /** save files stored in global variables filenames[] and contents[] to SD card */
    public void save() {
        checkExternalMedia();
        writeToSDFile(filenames, contents);
        String tmpfeedBackString = feedBackString;
        feedBackString = "";

        Intent emailActivityIntent = new Intent(MainActivity.this, EmailActivity.class);
        emailActivityIntent.putExtra("filenames", filenames); //Optional parameters
        emailActivityIntent.putExtra("feedback", tmpfeedBackString); //Optional parameters
        MainActivity.this.startActivity(emailActivityIntent);
    }

    /** log out user and send back to login screen */
    public void onClickLogout(View view) {
        new Thread(new Runnable() {
            public void run() {
                // running shared preference editor on separate thread to avoid issues with UI thread (manholeEditText was uneditable)
                SharedPreferences pref = getApplicationContext().getSharedPreferences("MyPref", Context.MODE_PRIVATE); // 0 - for private mode
                SharedPreferences.Editor editor = pref.edit();
                editor.remove("user_id");
                editor.commit();
            }
        }).start();

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

    /** connects arduino if found, triggers broadcastReceiver to open up a serial connection */
    public void connectArduino() {
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            boolean keep = true;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                //int deviceVID = device.getVendorId();
                //Toast.makeText(MainActivity.this, "Vendor ID: " + Integer.toString(deviceVID), Toast.LENGTH_LONG).show();
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
            Toast.makeText(MainActivity.this, "No devices found", Toast.LENGTH_LONG).show();
        }
    }

    /** updates contents of files with desired metadata */
    public void appendMetadataToFileContents(String metadata) {
        for (int i = 0; i < contents.length; i++) {
            contents[i] = metadata + contents[i];
        }
    }

    /** Method to check whether external media available and writable. This is adapted from
     http://developer.android.com/guide/topics/data/data-storage.html#filesExternal */
    private void checkExternalMedia() {
        boolean mExternalStorageAvailable = false;
        boolean mExternalStorageWriteable = false;
        String state = Environment.getExternalStorageState();

        if (Environment.MEDIA_MOUNTED.equals(state)) {
            // Can read and write the media
            mExternalStorageAvailable = mExternalStorageWriteable = true;
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            // Can only read the media
            mExternalStorageAvailable = true;
            mExternalStorageWriteable = false;
        } else {
            // Can't read or write
            mExternalStorageAvailable = mExternalStorageWriteable = false;
        }
        //feedbackView.append("\n\nExternal Media: readable="+mExternalStorageAvailable+" writable="+mExternalStorageWriteable);
    }

    /** Method to write ascii text characters to file on SD card. Note that you must add a
     WRITE_EXTERNAL_STORAGE permission to the manifest file or this method will throw
     a FileNotFound Exception because you won't have write permission. */
    private void writeToSDFile(String[] filenames, String[] contents) {
        // Find the root of the external storage.
        // See http://developer.android.com/guide/topics/data/data-  storage.html#filesExternal

        File root = android.os.Environment.getExternalStorageDirectory();
        //feedbackView.append("\nExternal file system root: "+root);

        // See http://stackoverflow.com/questions/3551821/android-write-to-sd-card-folder

        File dir = new File(root.getAbsolutePath() + "/" + FOLDER_NAME);
        dir.mkdirs();

        try {
            String feedback = "";
            for (int i = 0; i < filenames.length; i++) {
                File file = new File(dir, filenames[i]);
                FileOutputStream f = new FileOutputStream(file);
                PrintWriter pw = new PrintWriter(f);
                pw.print(contents[i]);
                pw.flush();
                pw.close();
                f.close();
                feedback += "File written to " + file + "\n";
            }
            feedBackString += feedback + "\n";
        } catch (FileNotFoundException e) {
            String[] permissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE};
            requestPermissions(permissions, 1);
            e.printStackTrace();
            //Toast.makeText(MainActivity.this, "Check permissions in app settings", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "Unknown error", Toast.LENGTH_LONG).show();
        }
    }

    /** end connection if disconnected */
    public void endSerialConnection() {
        feedBackString = "";
        if (serialPort != null && serialConnectionOpen) {
            serialPort.close();
            Toast.makeText(MainActivity.this, "Serial connection closed", Toast.LENGTH_LONG).show();
        }
        serialConnectionOpen = false;
    }

    /** logout if back button pressed */
    @Override
    public void onBackPressed() {
        logoutButton.performClick();
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

