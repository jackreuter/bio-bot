package jackreuter.biobot;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.os.Bundle;
import android.support.v4.widget.TextViewCompat;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class ManholeSelectionActivity extends Activity {

    // Cloud Firestore database and storage
    FirebaseFirestore db;
    FirebaseStorage storage;

    // UI elements
    TextView userIDTextView;
    RadioGroup manholeRadioGroup;
    ImageView mapImageView;

    // Global variables
    String cityID;
    String manholeID;
    String userID;
    String lastInstallDate;
    boolean hasBeenRetrieved;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manhole_selection);

        // create UI
        userIDTextView = (TextView) findViewById(R.id.textViewUserID);
        manholeRadioGroup = (RadioGroup) findViewById(R.id.radioGroupManholes);
        mapImageView = (ImageView) findViewById(R.id.imageViewMap);

        Intent intent = getIntent();
        userID = intent.getStringExtra("user_id");
        cityID = intent.getStringExtra("city_id");
        userIDTextView.setText("Hi " + userID + "!");

        // CLOUD FIRESTORE DATABASE SYNC
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        // listen for changes in database to repopulate radio buttons
        final CollectionReference manholesRef = db.collection("cities")
                .document(cityID)
                .collection("manholes");
        manholesRef.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@javax.annotation.Nullable QuerySnapshot snapshot, @javax.annotation.Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    Log.w("FIRESTORE", "Listen failed.", e);
                    return;
                }

                if (snapshot != null) {
                    // clear views
                    manholeRadioGroup.removeAllViews();

                    // repopulate
                    Boolean first = true;
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        RadioButton manholeRadioButton = new RadioButton(ManholeSelectionActivity.this);
                        manholeRadioButton.setTextColor(getResources().getColor(R.color.text_color));
                        manholeRadioButton.setText(document.getId());
                        manholeRadioButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.font_size_large));

                        RadioGroup.LayoutParams params = new RadioGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        if (first) {
                            params.setMargins(
                                    (int) getResources().getDimension(R.dimen.inner_margin),
                                    (int) getResources().getDimension(R.dimen.inner_margin),
                                    0,
                                    (int) getResources().getDimension(R.dimen.inner_margin));
                            first = false;
                        } else {
                            params.setMargins(
                                    (int) getResources().getDimension(R.dimen.inner_margin),
                                    0,
                                    0,
                                    (int) getResources().getDimension(R.dimen.inner_margin));
                        }
                        manholeRadioButton.setLayoutParams(params);
                        manholeRadioGroup.addView(manholeRadioButton);
                    }

                } else {
                    Log.d("FIRESTORE", "Current data: null");
                }
            }
        });

        // handle radio button selection to get map image and update manholeID
        manholeRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // checkedId is the RadioButton selected
                RadioButton selectedRadioButton = (RadioButton) findViewById(checkedId);
                manholeID = selectedRadioButton.getText().toString();
                db.collection("cities")
                        .document(cityID)
                        .collection("manholes")
                        .document(manholeID)
                        .get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                // document found
                                // load map image
                                String mapURL = document.getString("mapURL");
                                if (mapURL == null) {
                                    mapImageView.setImageResource(0);
                                } else {
                                    mapImageView.setAdjustViewBounds(true);
                                    GlideApp.with(ManholeSelectionActivity.this)
                                            .load(mapURL)
                                            .into(mapImageView);
                                }

                            } else {
                                // no document found
                            }
                        } else {
                            // error
                        }
                    }
                });

                final CollectionReference installsRef = db.collection("cities")
                        .document(cityID)
                        .collection("manholes")
                        .document(manholeID)
                        .collection("deployments");
                installsRef.addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@javax.annotation.Nullable QuerySnapshot snapshot, @javax.annotation.Nullable FirebaseFirestoreException e) {
                        if (e != null) {
                            Log.w("FIRESTORE", "Listen failed.", e);
                            return;
                        }

                        if (snapshot != null) {
                            ArrayList<String> installDates = new ArrayList<String>();
                            List<DocumentSnapshot> documents = snapshot.getDocuments();
                            if (documents.size() > 0) {
                                DocumentSnapshot lastInstall = documents.get(documents.size() - 1);
                                lastInstallDate = lastInstall.getId();
                                checkLastDeploymentForRetrieval();
                            } else {
                                lastInstallDate = "";
                            }
                        } else {
                            Log.d("FIRESTORE", "Current data: null");
                        }
                    }
                });


            }
        });

    }

    // check last deployment for retrieval log
    public void checkLastDeploymentForRetrieval() {
        db.collection("cities")
                .document(cityID)
                .collection("manholes")
                .document(manholeID)
                .collection("deployments")
                .document(lastInstallDate)
                .collection("retrieval log")
                .get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if (task.isSuccessful()) {
                    QuerySnapshot collection = task.getResult();
                    if (collection.getDocuments().size() > 0) {
                        hasBeenRetrieved = true;
                    } else {
                        hasBeenRetrieved = false;
                    }
                } else {
                }
            }
        });
    }

    // start new deployment
    public void onClickInstall(View view) {
        if (manholeID == null) {
            largeToast("Must select a manhole location", ManholeSelectionActivity.this);
        } else {
            startInstall();
        }
    }

    // start retrieval for past deployment
    public void onClickRetrieve(View view) {
        if (manholeID == null) {
            largeToast("Must select a manhole location", ManholeSelectionActivity.this);
        } else if (lastInstallDate.equals("")) {
            lastInstallDate = getDateCurrentTimeZone(System.currentTimeMillis() / 1000);
            final AlertDialog alertDialog = new AlertDialog.Builder(ManholeSelectionActivity.this).create();
            alertDialog.setTitle("No install log found");
            alertDialog.setMessage("Are you sure this is the correct manhole location?");
            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "YES",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            startRetrieval();
                        }
                    });
            alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "NO",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            alertDialog.show();
            enlargeTextAlertDialog(alertDialog);
        } else if (hasBeenRetrieved) {
            final AlertDialog alertDialog = new AlertDialog.Builder(ManholeSelectionActivity.this).create();
            alertDialog.setTitle("This manhole already has a retrieval log on file");
            alertDialog.setMessage("Are you sure you want to start a new retrieval?");
            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "YES",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            startRetrieval();
                        }
                    });
            alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "NO",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            alertDialog.show();
            enlargeTextAlertDialog(alertDialog);
        } else {
            startRetrieval();
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

    /** logout user and return to login screen */
    public void onClickLogout(View view) {
        Intent logoutIntent = new Intent(ManholeSelectionActivity.this, LoginActivity.class);
        logoutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        logoutIntent.putExtra("logout", true);
        startActivity(logoutIntent);
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

    /** start install activity */
    public void startInstall() {
        Intent installActivityIntent = new Intent(ManholeSelectionActivity.this, InstallActivity.class);
        installActivityIntent.putExtra("user_id", userID);
        installActivityIntent.putExtra("city_id", cityID);
        installActivityIntent.putExtra("manhole_id", manholeID);
        startActivity(installActivityIntent);
    }

    /** start retrieval activity */
    public void startRetrieval() {
        Intent retrievalActivityIntent = new Intent(ManholeSelectionActivity.this, RetrievalActivity.class);
        retrievalActivityIntent.putExtra("user_id", userID);
        retrievalActivityIntent.putExtra("city_id", cityID);
        retrievalActivityIntent.putExtra("manhole_id", manholeID);
        retrievalActivityIntent.putExtra("install_date", lastInstallDate);
        startActivity(retrievalActivityIntent);
    }

    public void enlargeTextAlertDialog(AlertDialog ad) {
        TextView textViewMessage = (TextView) ad.findViewById(android.R.id.message);
        Button buttonYes = ad.getButton(Dialog.BUTTON_POSITIVE);
        Button buttonNo = ad.getButton(Dialog.BUTTON_NEGATIVE);
        textViewMessage.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.font_size_large));
        buttonYes.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.font_size_large));
        buttonNo.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.font_size_large));
    }

}
