package com.biobot.boxapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import nl.dionsegijn.konfetti.KonfettiView;
import nl.dionsegijn.konfetti.models.Shape;
import nl.dionsegijn.konfetti.models.Size;

public class FeedbackActivity extends Activity {

    TextView textViewYouRock;
    TextView textViewManhole;
    TextView textViewSuccess;
    KonfettiView konfettiView;
    Button buttonNext;
    String userID;
    String cityID;
    String manholeID;
    float xVal;
    float yVal;

    //must equal name field in provider_paths.xml
    public final String FOLDER_NAME = "data";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_feedback);

        textViewYouRock = findViewById(R.id.textViewYouRock);
        textViewManhole = findViewById(R.id.textViewManhole);
        textViewSuccess = findViewById(R.id.textViewSuccess);
        buttonNext = findViewById(R.id.buttonNext);

        Intent intent = getIntent();
        userID = intent.getStringExtra("user_id");
        cityID = intent.getStringExtra("city_id");
        manholeID = intent.getStringExtra("manhole_id");
        textViewYouRock.setText("You rock, " + userID + "!");
        textViewManhole.setText("Manhole at " + manholeID);

        konfettiView = findViewById(R.id.viewKonfetti);

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = size.x;

        konfettiView.build()
                .addColors(Color.YELLOW, Color.GREEN, Color.MAGENTA)
                .setDirection(0.0, 359.0)
                .setSpeed(1f, 5f)
                .setFadeOutEnabled(true)
                .setTimeToLive(2000L)
                .addShapes(Shape.RECT, Shape.CIRCLE)
                .addSizes(new Size(12, 5f))
                .setPosition(-50f, width + 50f, -50f, -50f)
                .streamFor(300, 5000L);

        konfettiView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN ||
                    event.getAction() == MotionEvent.ACTION_MOVE){
                    xVal = event.getX();
                    yVal = event.getY();
                }

                konfettiView.build()
                        .addColors(Color.YELLOW, Color.MAGENTA, Color.GREEN)
                        .setDirection(0.0, 359.0)
                        .setSpeed(1f, 5f)
                        .setFadeOutEnabled(true)
                        .setTimeToLive(400L)
                        .addShapes(Shape.RECT, Shape.CIRCLE)
                        .addSizes(new Size(12, 5f))
                        .setPosition(xVal - 10f, xVal + 10f, yVal + 10f, yVal - 10f)
                        .streamFor(100, 200L);
                return true;
            }
        });
    }

    /** return to the main activity to communicate with arduino */
    public void onClickNext(View view) {
        Intent nextIntent = new Intent(FeedbackActivity.this, ManholeSelectionActivity.class);
        nextIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        nextIntent.putExtra("user_id", userID);
        nextIntent.putExtra("city_id", cityID);
        startActivity(nextIntent);
    }

    /** back button equivalent to next */
    @Override
    public void onBackPressed() {
        buttonNext.performClick();
    }

}
