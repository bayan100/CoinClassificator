package com.hhu.yannick.coinclassificator;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.hhu.yannick.coinclassificator.AsyncProcessor.DrawEllipseAGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.OnTaskCompleted;

public class EllipseActivity extends AppCompatActivity implements OnTaskCompleted {

    ImageView imageView;
    ProgressBar progressBar;
    private DrawEllipseAGP agp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ellipse);

        // get Views
        imageView = findViewById(R.id.imageView);
        progressBar = findViewById(R.id.pBar);

        // load Data
        loadImage();

        // initialize and start the async task
        agp = new DrawEllipseAGP(bitmap, this);
        agp.execute();
    }

    private Bitmap bitmap;
    private void loadImage(){
        try {
            Intent intent = getIntent();
            String filepath = intent.getStringExtra("File");

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
        if(agp.result.containsKey("bitmap"))
            imageView.setImageBitmap((Bitmap)agp.result.get("bitmap"));
    }
}
