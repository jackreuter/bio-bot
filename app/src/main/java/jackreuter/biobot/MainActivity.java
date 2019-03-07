package jackreuter.biobot;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.net.Uri;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import android.widget.ScrollView;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.widget.Button;
import android.support.v4.content.FileProvider;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.io.PrintWriter;
import java.util.TimeZone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.support.v4.content.ContextCompat;
import android.Manifest;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;

import org.w3c.dom.Text;

public class MainActivity extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    //location services
    private GoogleApiClient googleApiClient;
    private LocationManager locationManager;
    private LocationRequest locationRequest;
    private Location location;
    private long UPDATE_INTERVAL = 2 * 1000;  /* 10 secs */
    private long FASTEST_INTERVAL = 2000; /* 2 sec */

    //arduinoUSB
    public final String ACTION_USB_PERMISSION = "com.hariharan.arduinousb.USB_PERMISSION";

    //email data and file parsing
    public final String[] EMAIL_RECIPIENT = {"jreuter@wesleyan.edu"};
    public final String EMAIL_SUBJECT = "BIOBOT";
    public final String START_FILENAME = "!";
    public final String START_FILE = "\\$";
    public final String END_FILE = "\\^";
    public final String END_TRANSMISSION = "&";
    public final String INQUIRY = "~";
    public final String ID = "*";

    //must equal name field in provider_paths.xml
    public final String FOLDER_NAME = "data";

    public final String testData = "!190111_I.TXT$" +
            "[TONS OF TEXT]" +
            "^!190117_A.TXT$" +
            "[TONS OF TEXT]" +
            "^&";
    Button loginButton, readButton, saveButton, emailButton;
    TextView userIDTextView, manholeTextView;
    EditText userIDEditText, manholeEditText;
    ScrollView feedbackContainer;
    TextView feedbackView;
    UsbManager usbManager;
    IntentFilter filter;
    UsbDevice device;
    UsbDeviceConnection connection;
    com.felhr.usbserial.UsbSerialDevice serialPort;
    String[] filenames;
    String[] contents;

    Boolean loggedIn;
    String userID;
    String manholeLocation;
    String metadataString;

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

                        //split data into array of files,
                        String[] files = data.split(END_FILE);

                        //ditch the end character
                        filenames = new String[files.length - 1];
                        contents = new String[files.length - 1];

                        String feedback = Integer.toString(files.length - 1) + " files found:\n";

                        //split files into filenames and contents
                        for (int i = 0; i < files.length - 1; i++) {
                            String[] fileAndContents = files[i].split(START_FILE);
                            filenames[i] = fileAndContents[0].substring(1);
                            contents[i] = fileAndContents[1];
                            feedback += filenames[i] + "\n";
                        }
                        tvAppendToFront(feedbackView, feedback + "\n");
                        if (filenames.length > 0) {
                            buttonEnable(saveButton, true);
                            updateMetadata();
                        }
                    } else {
                        tvAppendToFront(feedbackView, "Received: " + data + "\n\n");
                    }
                } else {
                    //tvAppendToFront(feedbackView, "No data received\n");
                }
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

    /**
     @Override protected void onPause() {
     super.onPause();
     unregisterReceiver(broadcastReceiver);
     }

     /**
     @Override protected void onResume() {
     super.onResume();
     filter = new IntentFilter();
     filter.addAction(ACTION_USB_PERMISSION);
     filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
     filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
     registerReceiver(broadcastReceiver, filter);
     }
     **/

    /** save variables during recreation, e.g. rotation*/
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("userID", userID);
        outState.putBoolean("isUserLoggedIn", loggedIn);
        outState.putBoolean("isReadButtonEnabled", readButton.isEnabled());
        outState.putBoolean("isSaveButtonEnabled", saveButton.isEnabled());
        outState.putBoolean("isEmailButtonEnabled", emailButton.isEnabled());
        outState.putCharSequence("feedbackViewText", feedbackView.getText());

        String manholeTextEntered = manholeEditText.getText().toString();
        if (manholeTextEntered != null) {
            outState.putString("manholeTextEntered", manholeTextEntered);
        } else {
            outState.putString("manholeTextEntered", "");
        }

        String userIDTextEntered = userIDEditText.getText().toString();
        if (userIDTextEntered != null) {
            outState.putString("userIDTextEntered", manholeTextEntered);
        } else {
            outState.putString("userIDTextEntered", "");
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
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        //Remove notification bar
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        usbManager = (UsbManager) getSystemService(this.USB_SERVICE);

        String[] permissions = {Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE};
        requestPermissions(permissions, 1);

        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        checkLocation(); //check whether location service is enable or not in your  phone

        userIDTextView = (TextView) findViewById(R.id.textViewUserID);
        userIDEditText = (EditText) findViewById(R.id.editTextUserID);
        loginButton = (Button) findViewById(R.id.buttonLogin);
        manholeTextView = (TextView) findViewById(R.id.textViewManholeLocation);
        manholeEditText = (EditText) findViewById(R.id.editTextManholeLocation);
        readButton = (Button) findViewById(R.id.buttonRead);
        saveButton = (Button) findViewById(R.id.buttonSave);
        emailButton = (Button) findViewById(R.id.buttonEmail);
        feedbackContainer = (ScrollView) findViewById(R.id.feedbackContainer);
        feedbackView = new TextView(this);
        feedbackView.setTextSize(18);
        feedbackView.setTextColor(Color.WHITE);
        feedbackContainer.addView(feedbackView);

        setUiEnabled(false);
        setUiVisible(false);
        loggedIn = false;

        filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(broadcastReceiver, filter);

        //int result = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION);
        //if (result == PackageManager.PERMISSION_GRANTED) {Log.d("","granted");}

        connectArduino();

        if (savedInstanceState != null) {
            loggedIn = savedInstanceState.getBoolean("isUserLoggedIn");
            userID = savedInstanceState.getString("userID");
            manholeEditText.setText(savedInstanceState.getString("manholeTextEntered"), TextView.BufferType.EDITABLE);
            userIDEditText.setText(savedInstanceState.getString("userIDTextEntered"), TextView.BufferType.EDITABLE);
            feedbackView.append(savedInstanceState.getCharSequence("feedbackViewText"));
            filenames = savedInstanceState.getStringArray("filenames");
            contents = savedInstanceState.getStringArray("contents");
            readButton.setEnabled(savedInstanceState.getBoolean("isReadButtonEnabled"));
            saveButton.setEnabled(savedInstanceState.getBoolean("isSaveButtonEnabled"));
            emailButton.setEnabled(savedInstanceState.getBoolean("isEmailButtonEnabled"));
        }

        if (loggedIn) {
            setUiVisible(true);
            userIDEditText.setVisibility(View.INVISIBLE);
            userIDTextView.setText("User ID: " + userID);
            loginButton.setText("LOG OUT");
        }
    }

    /** unregister the broadcast Receiver */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);
    }

    /** show or don't show buttons */
    public void setUiVisible(boolean bool) {
        if (bool) {
            manholeTextView.setVisibility(View.VISIBLE);
            manholeEditText.setVisibility(View.VISIBLE);
            readButton.setVisibility(Button.VISIBLE);
            saveButton.setVisibility(Button.VISIBLE);
            emailButton.setVisibility(Button.VISIBLE);
            feedbackContainer.setVisibility(View.VISIBLE);
        } else {
            manholeTextView.setVisibility(View.INVISIBLE);
            manholeEditText.setVisibility(View.INVISIBLE);
            readButton.setVisibility(Button.INVISIBLE);
            saveButton.setVisibility(Button.INVISIBLE);
            emailButton.setVisibility(Button.INVISIBLE);
            feedbackContainer.setVisibility(View.INVISIBLE);
        }
    }

    /** enable or disable buttons */
    public void setUiEnabled(boolean bool) {
        readButton.setEnabled(bool);
        saveButton.setEnabled(bool);
        emailButton.setEnabled(bool);
    }

    /** to print feedback during callback thread */
    private void tvAppendToFront(TextView tv, CharSequence text) {
        final TextView ftv = tv;
        final CharSequence ftext = text;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ftv.setText(ftext + ftv.getText().toString());
                //ftv.append(ftext);
            }
        });
    }

    /** set UIenabled during callback thread */
    private void buttonEnable(Button button, Boolean choice) {
        final Button b = button;
        final Boolean bool = choice;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                b.setEnabled(bool);
            }
        });
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

    /** Pull text from editTextLogin and log user in if non-empty */
    public void onClickLogin(View view) {
        if (!loggedIn) {
            String textEntered = userIDEditText.getText().toString();
            if (textEntered.equals("")) {
                Toast.makeText(MainActivity.this, "Must enter User ID", Toast.LENGTH_LONG).show();
            } else {
                setUiVisible(true);
                userIDTextView.setText("User ID: " + textEntered);
                tvAppendToFront(feedbackView, "----LOGGED IN AS: " + textEntered + "----\n\n\n");
                userIDEditText.setVisibility(View.INVISIBLE);
                userID = textEntered;
                loginButton.setText("LOG OUT");
                loggedIn = true;
            }
        } else {
            setUiVisible(false);
            tvAppendToFront(feedbackView, "----LOGGED OUT----\n\n");
            userIDTextView.setText("User ID: ");
            userIDEditText.setVisibility(View.VISIBLE);
            loginButton.setText("LOG IN");
            userID = "";
            loggedIn = false;
        }
    }

    /** create fake data string to manipulate w/o need for arduino */
    /**
     public void onClickTestRead(View view) {
     String data = testData;
     if (data.length() > 0) {
     String cue = data.substring(0, 1);
     if (cue.equals(START_FILENAME)) {

     //split data into array of files,
     String[] files = data.split(END_FILE);

     //ditch the end character
     filenames = new String[files.length - 1];
     contents = new String[files.length - 1];
     tvAppendToFront(feedbackView, Integer.toString(files.length - 1) + " files found:\n");

     //split files into filenames and contents
     for (int i = 0; i < files.length - 1; i++) {
     String[] fileAndContents = files[i].split(START_FILE);
     filenames[i] = fileAndContents[0].substring(1);
     contents[i] = fileAndContents[1];
     tvAppendToFront(feedbackView, filenames[i] + "\n");
     }
     if (filenames.length > 0) {
     buttonEnable(saveButton, true);
     }
     } else {
     tvAppendToFront(feedbackView, "Received: " + data + "\n");
     }
     } else {
     tvAppendToFront(feedbackView, "\n");
     }
     }
     */

    /** send cue to read file from arduino */
    public void onClickRead(View view) {
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

            manholeLocation = manholeEditText.getText().toString();
            if (manholeLocation.equals("")) {
                Toast.makeText(MainActivity.this, "Must enter manhole location", Toast.LENGTH_LONG).show();

            } else {

                String locationString = "Location not detected";
                if (location == null) {
                    Toast.makeText(MainActivity.this, "Location not detected, wait and try again", Toast.LENGTH_LONG).show();
                } else {
                    locationString = location.getLatitude() + " " + location.getLongitude();

                    //save metadata string
                    metadataString = "User ID: \t" + userID +
                            "\nRetrieval date: \t" + getDateCurrentTimeZone(System.currentTimeMillis() / 1000) +
                            "\nManhole location: \t" + manholeLocation +
                            "\nGPS coordinates: \t" + locationString +
                            "\n";
                    tvAppendToFront(feedbackView, "METADATA\n" + metadataString + "\n");
                    serialPort.write(INQUIRY.getBytes());
                    manholeLocation = "";
                    manholeEditText.setText("");

                }
            }
        }
    }

    /** updates contents of files with desired metadata */
    public void updateMetadata() {
        for (int i = 0; i < contents.length; i++) {
            contents[i] = metadataString + contents[i];
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
            tvAppendToFront(feedbackView, feedback + "\n");
            emailButton.setEnabled(true);
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

    /** save files stored in global variables filenames[] and contents[] to SD card */
    public void onClickSave(View view) {
        checkExternalMedia();
        writeToSDFile(filenames, contents);
    }

    /** give option to email files as attachments to EMAIL_RECIPIENT or upload them to cloud storage */
    public void onClickEmail(View view) {
        //need to "send multiple" to get more than one attachment
        Intent emailIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        emailIntent.setType("text/plain");
        emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, EMAIL_RECIPIENT);
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, EMAIL_SUBJECT);
        emailIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        //has to be an ArrayList
        ArrayList<Uri> uris = new ArrayList<Uri>();

        //convert from paths to Android friendly Parcelable Uri's
        for (String filename : filenames)
        {
            File fileIn = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/"+FOLDER_NAME, filename);
            Uri u = FileProvider.getUriForFile(MainActivity.this,MainActivity.this.getApplicationContext().getPackageName() + ".provider", fileIn);
            uris.add(u);
        }

        emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);

        try {
            startActivity(Intent.createChooser(emailIntent, "Send mail..."));
            //finish();
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(MainActivity.this, "There is no email client installed.", Toast.LENGTH_LONG).show();
        }
    }

    /** end connection if disconnected */
    public void endSerialConnection() {
        readButton.setEnabled(false);
        if (serialPort != null) {
            serialPort.close();
            Toast.makeText(MainActivity.this, "Serial connection closed", Toast.LENGTH_LONG).show();
        }
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

    @Override
    protected void onStart() {
        super.onStart();
        if (googleApiClient != null) {
            googleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }
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

