package com.biobot.boxapp;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.support.v4.content.FileProvider;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.Toast;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.widget.Button;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.widget.TextView;
import android.Manifest;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

public class AdminActivity extends Activity {

    // file parsing
    public final String START_FILENAME = "!";
    public final String START_FILE = "$";
    public final String END_FILE = "^";
    public final String END_TRANSMISSION = "&";
    public final String INQUIRY = "~";
    public final String ID = "*";
    public final String EMAIL_RECIPIENT = "jreuter@wesleyan.edu";
    public final String EMAIL_SUBJECT = "BIOBOT";

    // must equal name field in provider_paths.xml
    public final String FOLDER_NAME = "data";

    // UI
    Button readButton, emailButton;
    ScrollView scrollViewFeedback;
    TextView textViewFeedback;

    // arduinoUSB
    public final String ACTION_USB_PERMISSION = "com.hariharan.arduinousb.USB_PERMISSION";
    UsbManager usbManager;
    IntentFilter filter;
    UsbDevice device;
    UsbDeviceConnection connection;
    com.felhr.usbserial.UsbSerialDevice serialPort;

    // global variables
    ArrayList<RetrievalFile> files;
    RetrievalFile currentFile;
    String excess;
    Boolean transmissionInProgress;
    Boolean serialConnectionOpen;

    /** callback used to communicate with arduino, takes arduino data and parses into
     filenames[] and contents[] */
    com.felhr.usbserial.UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() { //Defining a Callback which triggers whenever data is read.
        @Override
        public void onReceivedData(byte[] arg0) {
            String data;
            try {
                // deal with files running over size of received data
                data = new String(arg0, "UTF-8");
                int lastNewLine = data.lastIndexOf("\n");
                if (lastNewLine != -1) {
                    String lastLine = data.substring(lastNewLine + 1);
                    if (!lastLine.contains(END_TRANSMISSION)) {
                        data = data.substring(0, lastNewLine);
                        data = excess + data;
                        excess = lastLine;
                    }
                }
                processIncomingData(data);
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
                            serialPort.setBaudRate(115200);
                            serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                            serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                            serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                            serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                            serialPort.read(mCallback); //
                            largeToast("Serial connection opened", AdminActivity.this);
                            serialConnectionOpen = true;
                            readButton.setEnabled(true);
                        } else {
                            largeToast("Port not open", AdminActivity.this);
                        }
                    } else {
                        largeToast("Port is null", AdminActivity.this);
                    }
                } else {
                    largeToast("Permission not granted. Reconnect Teensy", AdminActivity.this);
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

    }

    /** create variables, receiver, try to connect in case already plugged in
     get variables from savedInstanceState if possible */
    @RequiresApi(api = 23)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        //TO COMMUNICATE WITH ARDUINO
        usbManager = (UsbManager) getSystemService(this.USB_SERVICE);

        //UI
        scrollViewFeedback = (ScrollView) findViewById(R.id.scrollViewFeedback);
        textViewFeedback = (TextView) findViewById(R.id.textViewFeedback);
        readButton = (Button) findViewById(R.id.buttonRead);
        emailButton = (Button) findViewById(R.id.buttonEmail);
        readButton.setEnabled(false);
        emailButton.setEnabled(false);

        //INITIALIZE GLOBAL VARIABLES
        transmissionInProgress = false;
        serialConnectionOpen = false;
        files = new ArrayList<>();
        excess = "";
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
    }

    @Override
    protected void onStop() {
        super.onStop();
        //UNREGISTER BROADCAST RECEIVER
        unregisterReceiver(broadcastReceiver);

        //DISCONNECT FROM ARDUINO
        endSerialConnection();
    }

    /** connects arduino if found, triggers broadcastReceiver to open up a serial connection */
    public void connectArduino() {
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            boolean keep = true;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                //int deviceVID = device.getVendorId();
                //Toast.makeText(AdminActivity.this, "Vendor ID: " + Integer.toString(deviceVID), Toast.LENGTH_LONG).show();
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
            largeToast("No devices found", AdminActivity.this);
        }
    }

    /** end connection if disconnected */
    public void endSerialConnection() {
        if (transmissionInProgress) {
            textViewAppendAndScroll(textViewFeedback, scrollViewFeedback, "\nError in transmission, please reconnect and try again\n");
        }
        if (serialPort != null && serialConnectionOpen) {
            serialPort.close();
            largeToast("Serial connection closed", AdminActivity.this);
        }
        serialConnectionOpen = false;
        readButton.setEnabled(false);
    }

    /** takes incoming serial data and processes into files */
    public void processIncomingData(String data) {
        if (data.length() > 0) {
            String cue = data.substring(0, 1);

            if (cue.equals(START_FILENAME)) {
                if (data.contains(START_FILE)) {
                    //chomp filename
                    int startFileIndex = data.indexOf(START_FILE);
                    currentFile = new RetrievalFile(data.substring(1, startFileIndex));
                    textViewAppendAndScroll(textViewFeedback, scrollViewFeedback, "File found: "+currentFile+"\n");

                    //process the rest of the data
                    processIncomingData(data.substring(startFileIndex));
                } else {
                    textViewAppendAndScroll(textViewFeedback, scrollViewFeedback, "Error in file format\n");
                }

            } else if (cue.equals(START_FILE)) {
                if (data.contains(END_FILE)) {
                    //chomp contents
                    int endFileIndex = data.indexOf(END_FILE);
                    processFieldsAndContents(data.substring(1, endFileIndex));
                    files.add(currentFile);
                    textViewAppendAndScroll(textViewFeedback, scrollViewFeedback, "End of file\n\n");

                    //process the rest of the data
                    if (data.length() > endFileIndex + 2) {
                        processIncomingData(data.substring(endFileIndex + 2)); //skip endfile symbol and newline character after
                    }
                } else {
                    processFieldsAndContents(data.substring(1));
                }

            } else if (cue.equals(END_TRANSMISSION)) {
                textViewAppendAndScroll(textViewFeedback, scrollViewFeedback, "Transmission complete\n\n");
                currentFile = null;
                transmissionInProgress = false;
                save();

            } else {
                if (currentFile != null) {
                    if (data.contains(END_FILE)) {
                        //chomp contents
                        int endFileIndex = data.indexOf(END_FILE);
                        processContents(data.substring(1, endFileIndex));
                        files.add(currentFile);
                        textViewAppendAndScroll(textViewFeedback, scrollViewFeedback, "End of file\n\n");

                        //process the rest of the data
                        if (data.length() > endFileIndex + 2) {
                            processIncomingData(data.substring(endFileIndex + 2)); //skip endfile symbol and newline character after
                        }
                    } else {
                        processContents(data);
                    }
                } else {
                }
            }

        } else {
        }
    }

    /** process both fields and contents of incoming data file*/
    public void processFieldsAndContents(String data) {
        if (data.length() > 0) {
            String[] fieldsAndContents = data.split("\n\n");
            if (fieldsAndContents.length > 1) {
                String fields = fieldsAndContents[0];
                String contents = fieldsAndContents[1];

                if (contents.contains("\n")) {
                    int namesSplit = contents.indexOf("\n");
                    String columnNames = contents.substring(0, namesSplit);
                    contents = contents.substring(namesSplit + 1);

                    processFields(fields);
                    processColumnNames(columnNames);
                    processContents(contents);
                }
            }
        }
    }

    /** parse fields of data file into hashmap, return the rest of the data file */
    public void processFields(String data) {
        String[] keyAndValueStrings = data.split("\n");
        for (int i = 0; i < keyAndValueStrings.length; i++) {
            String[] keyAndValuePair = keyAndValueStrings[i].split(":");
            if (keyAndValuePair.length > 1) {
                currentFile.addField(keyAndValuePair[0], chompFrontWhiteSpace(keyAndValuePair[1]));
            }
        }
    }

    /** parse the column names out of the data file */
    public void processColumnNames(String data) {
        String[] columnNames = data.split("\t");
        currentFile.setColumnNames(columnNames);
    }

    /** parse contents of data file into 2d array */
    public void processContents(String data) {
        String[] rows = data.split("\n");
        for (int i = 0; i < rows.length; i++) {
            String[] row = rows[i].split("\t");
            currentFile.addRow(row);
        }
        textViewAppendAndScroll(textViewFeedback, scrollViewFeedback, "Processed " + currentFile.rows.size() + " lines of data\n");
    }

    /** removes any whitespace "\t" "\n" or " " from front of string */
    public String chompFrontWhiteSpace(String s) {
        if (s.length() >  0) {
            while (s.substring(0,1).contains(" ") || s.substring(0,1).contains("\t") || s.substring(0,1).contains("\n")) {
                s = s.substring(1);
            }
        }
        return s;
    }

    /**
     * Method to check whether external media available and writable. This is adapted from
     * http://developer.android.com/guide/topics/data/data-storage.html#filesExternal
     */
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

    /**
     * Method to write ascii text characters to file on SD card. Note that you must add a
     * WRITE_EXTERNAL_STORAGE permission to the manifest file or this method will throw
     * a FileNotFound Exception because you won't have write permission.
     */
    private void writeToSDFile() {
        // Find the root of the external storage.
        // See http://developer.android.com/guide/topics/data/data-  storage.html#filesExternal

        File root = android.os.Environment.getExternalStorageDirectory();
        //feedbackView.append("\nExternal file system root: "+root);

        // See http://stackoverflow.com/questions/3551821/android-write-to-sd-card-folder

        File dir = new File(root.getAbsolutePath() + "/" + FOLDER_NAME);
        dir.mkdirs();

        try {
            String feedback = "";
            for (int i = 0; i < files.size(); i++) {
                File file = new File(dir, files.get(i).filename);
                FileOutputStream f = new FileOutputStream(file);
                PrintWriter pw = new PrintWriter(f);

                writeRetrievalFile(pw, files.get(i));

                pw.flush();
                pw.close();
                f.close();
                feedback += "File written to:\n" + file + "\n";
            }
            textViewAppendAndScroll(textViewFeedback, scrollViewFeedback, feedback + "\n");
            setEnabledOnUI(emailButton, true);
        } catch (FileNotFoundException e) {
            String[] permissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE};
            requestPermissions(permissions, 1);
            e.printStackTrace();
            Toast.makeText(AdminActivity.this, "Check permissions in app settings", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(AdminActivity.this, "Unknown error", Toast.LENGTH_LONG).show();
        }
    }

    /** recreate data file format */
    public void writeRetrievalFile(PrintWriter printWriter, RetrievalFile file) {
        if (file.fields != null) {
            for (String key : file.fields.keySet()) {
                printWriter.write(key + ":\t" + file.fields.get(key) + "\n");
            }
            printWriter.write("\n");
        }

        if (file.columnNames != null) {
            for (String column : file.columnNames) {
                printWriter.write(column + "\t");
            }
            printWriter.write("\n");
        }

        if (file.rows != null) {
            for (String[] row : file.rows) {
                for (String entry : row) {
                    printWriter.write(entry + "\t");
                }
                printWriter.write("\n");
            }
        }
    }

    /**
     * save files stored in global variables filenames[] and contents[] to SD card
     */
    public void save() {
        checkExternalMedia();
        writeToSDFile();
    }

    /** send cue to read file from arduino */
    public void onClickRead(View view) {
        if (!serialConnectionOpen) {
            largeToast("Serial connection not open. Reconnect Teensy", AdminActivity.this);
        } else {
            transmissionInProgress = true;
            serialPort.write(INQUIRY.getBytes());
        }
    }

    /**
     * give option to email files as attachments to EMAIL_RECIPIENT or upload them to cloud storage
     */
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
        for (RetrievalFile file : files) {
            File fileIn = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + FOLDER_NAME, file.filename);
            Uri u = FileProvider.getUriForFile(AdminActivity.this, AdminActivity.this.getApplicationContext().getPackageName() + ".provider", fileIn);
            uris.add(u);
        }

        emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);

        try {
            startActivity(Intent.createChooser(emailIntent, "Send mail..."));
            //finish();
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(AdminActivity.this, "There is no email client installed.", Toast.LENGTH_LONG).show();
        }
    }

    /** logout user and return to login screen */
    public void onClickLogout(View view) {
        Intent logoutIntent = new Intent(AdminActivity.this, LoginActivity.class);
        logoutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(logoutIntent);
    }

    /** back button equivalent to logout */
    @Override
    public void onBackPressed() {
        Intent logoutIntent = new Intent(AdminActivity.this, LoginActivity.class);
        logoutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(logoutIntent);
    }

    /** to print feedback during callback thread */
    private void textViewAppendAndScroll(final TextView tv, final ScrollView sv, final CharSequence text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //ftv.setText(ftext + ftv.getText().toString());
                tv.append(text);
            }
        });

        sv.post(new Runnable() {
            @Override
            public void run() {
                sv.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    /** set button enabled or disabled */
    public void setEnabledOnUI(final Button button, final Boolean enabled) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                button.setEnabled(enabled);
            }
        });
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
