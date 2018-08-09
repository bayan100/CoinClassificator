package com.hhu.yannick.coinclassificator.AsyncProcessor;

import android.content.Context;

import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.FeatureGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.GraphicsProcessor;
import com.hhu.yannick.coinclassificator.SQLite.DatabaseManager;

import org.opencv.xfeatures2d.SIFT;

import java.util.ArrayList;

public class GenerateFeaturesAGP extends AsyncGraphicsProcessor {
    public GenerateFeaturesAGP(DatabaseManager databaseManager, Context context, OnTaskCompleted listener){
        super(listener);

        ArrayList<GraphicsProcessor> processors = new ArrayList<>();
        FeatureGP fgp = new FeatureGP("/sdcard/Pictures/Testpictures/trainset/",
                FeatureGP.DetectorType.SIFT, databaseManager, context);
        processors.add(fgp);

        task = processors;
    }
}
