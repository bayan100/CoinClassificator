package com.hhu.yannick.coinclassificator;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
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

import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

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

        Button save_button = findViewById(R.id.button_save);
        save_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                savePicture();
                v.setEnabled(false);
            }
        });

        // initialize and start the async task
        agp = new DrawEllipseAGP(bitmap, this);
        agp.execute();
    }

    private Bitmap bitmap, original;
    private void loadImage(){
        try {
            Intent intent = getIntent();
            String filepath = intent.getStringExtra("File");

            //filepath =  "/sdcard/Pictures/Testpictures/dirtest.jpg";
            //bitmap = BitmapFactory.decodeFile(filepath);
            bitmap = BitmapFactory.decodeStream(this.openFileInput(filepath));
            original = bitmap.copy( Bitmap.Config.ARGB_8888 , true);
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
            //savePicture();
        }
        else if(agp.result.containsKey("bitmap"))
            imageView.setImageBitmap((Bitmap)agp.result.get("bitmap"));
    }

    private void savePicture(){
        Date dNow = new Date( );
        SimpleDateFormat ft = new SimpleDateFormat("yyyy.MM.dd_hh:mm:ss");
        String testpath = "/sdcard/Pictures/Testpictures/Test" + ft.format(dNow) + ".jpg";

        FileOutputStream out = null;
        try {
            //bitmap = ((BitmapDrawable)imageView.getDrawable()).getBitmap();
            out = new FileOutputStream(testpath);
            original.compress(Bitmap.CompressFormat.JPEG, 100, out);
            // PNG is a lossless format, the compression factor (100) is ignored
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                    Log.d("PICTURE", "saved");
                    //button_debug.setEnabled(false);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
