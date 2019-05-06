package jackreuter.biobot;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.content.FileProvider;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;

public class FeedbackActivity extends Activity {

    TextView cityManholeTextView;
    TextView userIDTextView;
    TextView feedbackView;
    Button nextButton;
    String[] filenames;
    String feedbackString;
    String userID;
    String cityID;
    String manholeID;

    public final String[] EMAIL_RECIPIENT = {"jreuter@wesleyan.edu"};
    public final String EMAIL_SUBJECT = "BIOBOT";

    //must equal name field in provider_paths.xml
    public final String FOLDER_NAME = "data";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_feedback);

        cityManholeTextView = findViewById(R.id.textViewCityManhole);
        userIDTextView = findViewById(R.id.textViewUserID);
        feedbackView = findViewById(R.id.textViewFeedback);
        nextButton = findViewById(R.id.buttonNext);

        Intent intent = getIntent();
        filenames = intent.getStringArrayExtra("filenames");
        feedbackString = intent.getStringExtra("feedback");
        userID = intent.getStringExtra("user_id");
        cityID = intent.getStringExtra("city_id");
        manholeID = intent.getStringExtra("manhole_id");
        cityManholeTextView.setText(manholeID + ", " + cityID);
        userIDTextView.setText("Hi " + userID + "!");


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
            Uri u = FileProvider.getUriForFile(FeedbackActivity.this, FeedbackActivity.this.getApplicationContext().getPackageName() + ".provider", fileIn);
            uris.add(u);
        }

        emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);

        try {
            startActivity(Intent.createChooser(emailIntent, "Send mail..."));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(FeedbackActivity.this, "There is no email client installed.", Toast.LENGTH_LONG).show();
        }
    }

    /** return to the main activity to communicate with arduino */
    public void onClickNext(View view) {
        Intent nextIntent = new Intent(FeedbackActivity.this, CitySelectionActivity.class);
        nextIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        nextIntent.putExtra("user_id", userID);
        startActivity(nextIntent);
    }

    /** back button equivalent to next */
    @Override
    public void onBackPressed() {
        nextButton.performClick();
    }

    public void onClickLogout(View view) {
        Intent logoutIntent = new Intent(FeedbackActivity.this, LoginActivity.class);
        logoutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        logoutIntent.putExtra("logout", true);
        startActivity(logoutIntent);
    }
}
