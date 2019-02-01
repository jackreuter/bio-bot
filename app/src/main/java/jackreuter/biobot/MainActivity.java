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

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.widget.Button;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringBufferInputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.io.PrintWriter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

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
    TextView feedbackView;
    UsbManager usbManager;
    UsbDevice device;
    UsbDeviceConnection connection;
    UsbSerialDevice serialPort;
    String[] filenames;
    String[] contents;

    //saving to file
    private static final String TAG = "MEDIA";

    //callback to talk with arduino
    UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() { //Defining a Callback which triggers whenever data is read.
        @Override
        public void onReceivedData(byte[] arg0) {
            String data = null;
            try {
                data = new String(arg0, "UTF-8");
                String[] files = data.split(CUE_NEW_FILE);
                filenames = new String[files.length];
                contents = new String[files.length];
                for (int i=0; i<files.length; i++) {
                    String[] fileLong = files[i].split(CUE_FILENAME);
                    filenames[i] = fileLong[0];
                    contents[i] = fileLong[1];
                    tvAppend(filenameView, fileLong[0]);
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    };

    //receiver to talk with arduino
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
                            setUiEnabled(true); //Enable Buttons in UI
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
        };
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //talking to arduino
        usbManager = (UsbManager) getSystemService(this.USB_SERVICE);
        readButton = (Button) findViewById(R.id.buttonRead);
        saveButton = (Button) findViewById(R.id.buttonSave);
        emailButton = (Button) findViewById(R.id.buttonEmail);
        identifierText = (EditText) findViewById(R.id.identifierText);
        filenameView = (TextView) findViewById(R.id.filenameView);
        feedbackView = (TextView) findViewById(R.id.feedbackView);
        setUiEnabled(false);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(broadcastReceiver, filter);
    }

    public void setUiEnabled(boolean bool) {
        readButton.setEnabled(bool);
        saveButton.setEnabled(bool);
        emailButton.setEnabled(bool);
    }

    //cue arduino to feed files through serial, with escape characters, ending with terminal character
    //save files
    public void connect() {
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            boolean keep = true;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                int deviceVID = device.getVendorId();
                Toast.makeText(MainActivity.this, Integer.toString(deviceVID),Toast.LENGTH_LONG).show();
                if (deviceVID == 6790)//Arduino Vendor ID, not sure where to find this
                {
                    PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
                    usbManager.requestPermission(device, pi);
                    keep = false;
                } else {
                    connection = null;
                    device = null;
                }

                if (!keep)
                    break;
            }
        }
        else {
            //tvAppend(feedbackView, "No devices connected");
            Toast.makeText(MainActivity.this, "No devices found",Toast.LENGTH_LONG).show();
        }

    }

    //send cue '$' to read file from arduino
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
        filenameView.append("\n\nExternal Media: readable="
                +mExternalStorageAvailable+" writable="+mExternalStorageWriteable);
    }

    /** Method to write ascii text characters to file on SD card. Note that you must add a
     WRITE_EXTERNAL_STORAGE permission to the manifest file or this method will throw
     a FileNotFound Exception because you won't have write permission. */

    private void writeToSDFile(String filename, String content){

        // Find the root of the external storage.
        // See http://developer.android.com/guide/topics/data/data-  storage.html#filesExternal

        File root = android.os.Environment.getExternalStorageDirectory();
        filenameView.append("\nExternal file system root: "+root);

        // See http://stackoverflow.com/questions/3551821/android-write-to-sd-card-folder

        File dir = new File (root.getAbsolutePath() + "/download");
        dir.mkdirs();
        File file = new File(dir, filename);

        try {
            FileOutputStream f = new FileOutputStream(file);
            PrintWriter pw = new PrintWriter(f);
            pw.print(content);
            pw.flush();
            pw.close();
            f.close();
            filenameView.append("\n\nFile written to "+file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            filenameView.append("******* File not found. Did you" +
                    " add a WRITE_EXTERNAL_STORAGE permission to the   manifest?");
        } catch (IOException e) {
            e.printStackTrace();
            filenameView.append("error");
        }
    }

    //saving to file
    public void onClickSave(View view) {
        checkExternalMedia();
        for (int i=0; i<filenames.length; i++) {
            writeToSDFile(filenames[i],contents[i]);
        }
    }

    //email files to email address
    public void onClickEmail(View view) {
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setData(Uri.parse("mailto:"));
        emailIntent.setType("text/plain");
        emailIntent.putExtra(Intent.EXTRA_EMAIL, EMAIL_RECIPIENT);
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, EMAIL_SUBJECT);
        emailIntent.putExtra(Intent.EXTRA_TEXT, "hello world");

        try {
            startActivity(Intent.createChooser(emailIntent, "Send mail..."));
            finish();
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(MainActivity.this, "There is no email client installed.", Toast.LENGTH_SHORT).show();
        }
    }

    //end connection if disconnected
    public void endConnection() {
        setUiEnabled(false);
        serialPort.close();
        Toast.makeText(MainActivity.this, "Serial connection closed!", Toast.LENGTH_LONG);
    }

    private void tvAppend(TextView tv, CharSequence text) {
        final TextView ftv = tv;
        final CharSequence ftext = text;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ftv.append(ftext);
            }
        });
    }

}
