package jackreuter.biobot;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.net.Uri;
import android.widget.Toast;
import android.widget.ScrollView;
import android.content.pm.PackageManager;

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
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.io.PrintWriter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.support.v4.content.ContextCompat;
import android.Manifest;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

public class MainActivity extends AppCompatActivity {

    public final String ACTION_USB_PERMISSION = "com.hariharan.arduinousb.USB_PERMISSION";
    public final String[] EMAIL_RECIPIENT = {"jreuter@wesleyan.edu"};
    public final String EMAIL_SUBJECT = "BIOBOT";
    public final String CUE_START = "$";
    public final String CUE_FILENAME = "<START>";
    public final String CUE_NEW_FILE = "<BREAK>";
    Button readButton, saveButton, emailButton;
    EditText identifierText;
    TextView filenameView;
    ScrollView feedbackContainer;
    TextView feedbackView;
    UsbManager usbManager;
    IntentFilter filter;
    UsbDevice device;
    UsbDeviceConnection connection;
    com.felhr.usbserial.UsbSerialDevice serialPort;
    String[] filenames;
    String[] contents;

    /** callback used to communicate with arduino, takes arduino data and parses into
     filenames[] and contents[] */
    com.felhr.usbserial.UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() { //Defining a Callback which triggers whenever data is read.
        @Override
        public void onReceivedData(byte[] arg0) {
            String data;
            try {
                data = new String(arg0, "UTF-8");
                if (data.length() > 0) {
                    if (data.substring(0, 1).equals(CUE_START)) {
                        data = data.substring(1);
                        String[] files = data.split(CUE_NEW_FILE);
                        filenames = new String[files.length];
                        contents = new String[files.length];
                        tvAppend(feedbackView, Integer.toString(files.length) + " files found:\n");
                        for (int i = 0; i < files.length; i++) {
                            String[] fileLong = files[i].split(CUE_FILENAME);
                            filenames[i] = fileLong[0];
                            contents[i] = fileLong[1];
                            tvAppend(feedbackView, fileLong[0] + "\n");
                        }
                        if (files.length > 0) {
                            buttonEnable(saveButton, true);
                        }
                    } else {
                        tvAppend(feedbackView, "Received: " + data + "\n");
                    }
                } else {
                    tvAppend(feedbackView, "\n");
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
                            Toast.makeText(MainActivity.this, "Serial connection opened",Toast.LENGTH_LONG).show();

                        } else {
                            Toast.makeText(MainActivity.this, "Port not open",Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "Port is null",Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Permission not granted",Toast.LENGTH_LONG).show();
                }
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                connect();
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                endConnection();
            }
        }

    };

    /**
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(broadcastReceiver);
    }

    /**
    @Override
    protected void onResume() {
        super.onResume();
        filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(broadcastReceiver, filter);
    }
     **/

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("isReadButtonEnabled",readButton.isEnabled());
        outState.putBoolean("isSaveButtonEnabled",saveButton.isEnabled());
        outState.putBoolean("isEmailButtonEnabled",emailButton.isEnabled());
        outState.putCharSequence("feedbackViewText", feedbackView.getText());
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        usbManager = (UsbManager) getSystemService(this.USB_SERVICE);
        readButton = (Button) findViewById(R.id.buttonRead);
        saveButton = (Button) findViewById(R.id.buttonSave);
        emailButton = (Button) findViewById(R.id.buttonEmail);
        //identifierText = (EditText) findViewById(R.id.identifierText);
        //filenameView = (TextView) findViewById(R.id.filenameView);
        feedbackContainer = (ScrollView) findViewById(R.id.feedbackContainer);
        feedbackView = new TextView(this);
        feedbackContainer.addView(feedbackView);
        setUiEnabled(false);

        filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(broadcastReceiver, filter);

        //int result = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION);
        //if (result == PackageManager.PERMISSION_GRANTED) {Log.d("","granted");}

        connect();

        if (savedInstanceState != null) {
            feedbackView.append(savedInstanceState.getCharSequence("feedbackViewText"));
            filenames = savedInstanceState.getStringArray("filenames");
            contents = savedInstanceState.getStringArray("contents");
            readButton.setEnabled(savedInstanceState.getBoolean("isReadButtonEnabled"));
            saveButton.setEnabled(savedInstanceState.getBoolean("isSaveButtonEnabled"));
            emailButton.setEnabled(savedInstanceState.getBoolean("isEmailButtonEnabled"));
        }
    }

    /** unregister the broadcast Receiver */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);
    }

    /** enable or disable buttons */
    public void setUiEnabled(boolean bool) {
        readButton.setEnabled(bool);
        saveButton.setEnabled(bool);
        emailButton.setEnabled(bool);
    }

    /** to print feedback during callback thread */
    private void tvAppend(TextView tv, CharSequence text) {
        final TextView ftv = tv;
        final CharSequence ftext = text;
        runOnUiThread(new Runnable() {
            @Override public void run() {
                ftv.append(ftext);
            }
        });
    }

    /** set UIenabled during callback thread */
    private void buttonEnable(Button button, Boolean choice) {
        final Button b = button;
        final Boolean bool = choice;
        runOnUiThread(new Runnable() {
            @Override public void run() {
                b.setEnabled(bool);
            }
        });
    }

    /** connects arduino if found, triggers broadcastReceiver to open up a serial connection */
    public void connect() {
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            boolean keep = true;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                int deviceVID = device.getVendorId();
                Toast.makeText(MainActivity.this, "Device ID: " + Integer.toString(deviceVID),Toast.LENGTH_LONG).show();
                //if (deviceVID == 6790)//Arduino Vendor ID, not sure where to find
                try
                {
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
        }
        else {
            Toast.makeText(MainActivity.this, "No devices found",Toast.LENGTH_LONG).show();
        }
    }

    /** create fake data string to manipulate w/o need for arduino */
    public void onClickTestRead(View view) {
        String data = "file1<START>fake data blah blah<BREAK>file2<START>fakedata<BREAK>file3<START>bs blah blah";
        String[] files = data.split(CUE_NEW_FILE);
        filenames = new String[files.length];
        contents = new String[files.length];
        feedbackView.append(Integer.toString(files.length)+" files found:\n");
        for (int i=0; i<files.length; i++) {
            String[] fileLong = files[i].split(CUE_FILENAME);
            filenames[i] = fileLong[0];
            contents[i] = fileLong[1];
            feedbackView.append(fileLong[0]+"\n");
        }
        if (filenames.length > 0) {
            saveButton.setEnabled(true);
        }
    }

    /** send cue '$' to read file from arduino */
    public void onClickRead(View view) {
        serialPort.write(CUE_START.getBytes());
    }

    /** Method to check whether external media available and writable. This is adapted from
     http://developer.android.com/guide/topics/data/data-storage.html#filesExternal */
    private void checkExternalMedia(){
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
    private void writeToSDFile(String[] filenames, String[] contents){

        // Find the root of the external storage.
        // See http://developer.android.com/guide/topics/data/data-  storage.html#filesExternal

        File root = android.os.Environment.getExternalStorageDirectory();
        //feedbackView.append("\nExternal file system root: "+root);

        // See http://stackoverflow.com/questions/3551821/android-write-to-sd-card-folder

        File dir = new File (root.getAbsolutePath() + "/data");
        dir.mkdirs();

        try {
            for (int i=0; i<filenames.length; i++) {
                File file = new File(dir, filenames[i]);
                FileOutputStream f = new FileOutputStream(file);
                PrintWriter pw = new PrintWriter(f);
                pw.print(contents[i]);
                pw.flush();
                pw.close();
                f.close();
                feedbackView.append("File written to " + file + "\n");
                emailButton.setEnabled(true);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            feedbackView.append("Check permissions in app settings\n");
        } catch (IOException e) {
            e.printStackTrace();
            feedbackView.append("Unknown error\n");
        }
    }

    /** save files stored in global variables filenames[] and contents[] to SD card */
    public void onClickSave(View view) {
        checkExternalMedia();
        writeToSDFile(filenames,contents);
    }

    /** give option to email files as attachments to EMAIL_RECIPIENT or upload them to cloud storage */
    public void onClickEmail(View view) {
        //need to "send multiple" to get more than one attachment
        Intent emailIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        emailIntent.setType("text/plain");
        emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, EMAIL_RECIPIENT);
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, EMAIL_SUBJECT);
        emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        //has to be an ArrayList
        ArrayList<Uri> uris = new ArrayList<Uri>();

        //convert from paths to Android friendly Parcelable Uri's
        for (String filename : filenames)
        {
            File fileIn = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/data", filename);
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
    public void endConnection() {
        readButton.setEnabled(false);
        if (serialPort != null) {
            serialPort.close();
            Toast.makeText(MainActivity.this, "Serial connection closed", Toast.LENGTH_LONG).show();
        }
    }

}

