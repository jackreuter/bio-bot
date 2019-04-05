package jackreuter.biobot;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.content.FileProvider;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;

public class EmailActivity extends Activity {

    TextView feedbackView;
    Button emailButton;
    Button nextButton;
    String[] filenames;
    String feedbackString;
    String userID;

    public final String[] EMAIL_RECIPIENT = {"jreuter@wesleyan.edu"};
    public final String EMAIL_SUBJECT = "BIOBOT";

    //must equal name field in provider_paths.xml
    public final String FOLDER_NAME = "data";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Remove title bar
        //this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        //Remove notification bar
        //this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_email);

        feedbackView = findViewById(R.id.textViewFeedback);
        emailButton = findViewById(R.id.buttonEmail);
        nextButton = findViewById(R.id.buttonNext);

        Intent intent = getIntent();
        filenames = intent.getStringArrayExtra("filenames");
        feedbackString = intent.getStringExtra("feedback");
        userID = intent.getStringExtra("user_id");
        feedbackView.setText(feedbackString);
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
            Uri u = FileProvider.getUriForFile(EmailActivity.this, EmailActivity.this.getApplicationContext().getPackageName() + ".provider", fileIn);
            uris.add(u);
        }

        emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);

        try {
            startActivity(Intent.createChooser(emailIntent, "Send mail..."));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(EmailActivity.this, "There is no email client installed.", Toast.LENGTH_LONG).show();
        }
    }

    /** return to the main activity to communicate with arduino */
    public void onClickNext(View view) {
        finish();
    }

    /** back button equivalent to next */
    @Override
    public void onBackPressed() {
        nextButton.performClick();
    }
}
