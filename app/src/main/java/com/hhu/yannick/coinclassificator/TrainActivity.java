package com.hhu.yannick.coinclassificator;

import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.hhu.yannick.coinclassificator.AsyncProcessor.GenerateFeaturesAGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.OnTaskCompleted;
import com.hhu.yannick.coinclassificator.SQLite.DatabaseManager;

public class TrainActivity extends AppCompatActivity implements OnTaskCompleted {

    private DatabaseManager databaseManager;
    private GenerateFeaturesAGP generateFeaturesAGP;

    private ProgressBar progressBar;
    private ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_train);

        progressBar = findViewById(R.id.pBar);
        imageView = findViewById(R.id.imageView);

        databaseManager = new DatabaseManager(getApplicationContext());
        databaseManager.open();

        generateFeaturesAGP = new GenerateFeaturesAGP(databaseManager, getApplicationContext(), this);
        generateFeaturesAGP.execute();
    }

    @Override
    public void onTaskCompleted() {
        progressBar.setVisibility(View.INVISIBLE);
        databaseManager.close();

        //Log.d("TRAIN", ((Bitmap)generateFeaturesAGP.result.get("bitmap")).getWidth() + "");
        imageView.setImageBitmap((Bitmap)generateFeaturesAGP.result.get("bitmap"));
    }
}
