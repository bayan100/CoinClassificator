package com.hhu.yannick.coinclassificator;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.hhu.yannick.coinclassificator.AsyncProcessor.OnTaskCompleted;
import com.hhu.yannick.coinclassificator.AsyncProcessor.ContourAGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.DrawEllipseAGP;

public class EllipseActivity extends AppCompatActivity implements OnTaskCompleted {

    private ImageView imageView;
    private ProgressBar progressBar;
    private DrawEllipseAGP agp;
    private ContourAGP cagp;
    private boolean debug;

    private Context context;
    private Intent classify_intent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ellipse);

        // get Views
        imageView = findViewById(R.id.imageView);
        progressBar = findViewById(R.id.pBar);

        context = getApplicationContext();

        // load Data
        loadImage();

        // set the click listeners
        Button continue_button = findViewById(R.id.continue_button);
        continue_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                classify_intent = new Intent(context, ClassifyActivity.class);
                classify_intent.putExtra("File", getIntent().getStringExtra("File"));
                startActivity(classify_intent);
            }
        });

        cagp = new ContourAGP(bitmap, this);
        Button button_debug = findViewById(R.id.button_debug);
        button_debug.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { debug = true; cagp.execute(); }
        });

        Button back_button = findViewById(R.id.back_button);
        back_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        // initialize and start the async task
        agp = new DrawEllipseAGP(bitmap, this);
        agp.execute();
    }

    private Bitmap bitmap;
    private void loadImage(){
        try {
            Intent intent = getIntent();
            String filepath = intent.getStringExtra("File");

            //filepath =  "/sdcard/Pictures/Testpictures/hand2.jpg";
            //bitmap = BitmapFactory.decodeFile(filepath);
            bitmap = BitmapFactory.decodeStream(this.openFileInput(filepath));
            bitmap = bitmap.copy( Bitmap.Config.ARGB_8888 , true);

            imageView.setImageBitmap(bitmap);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public void onTaskCompleted(){
        progressBar.setVisibility(View.INVISIBLE);

        // get the results from the async process
        if(debug && cagp.result.containsKey("bitmap")) {
            imageView.setImageBitmap((Bitmap)cagp.result.get("bitmap"));
        }
        else if(agp.result.containsKey("bitmap"))
            imageView.setImageBitmap((Bitmap)agp.result.get("bitmap"));
    }
}
