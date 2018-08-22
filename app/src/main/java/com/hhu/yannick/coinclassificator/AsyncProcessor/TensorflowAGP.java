package com.hhu.yannick.coinclassificator.AsyncProcessor;

import android.app.Activity;
import android.graphics.Bitmap;

import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.GraphicsProcessor;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.ResizeGP;
import com.hhu.yannick.coinclassificator.SQLite.DatabaseManager;
import com.hhu.yannick.coinclassificator.Tensorflow.TensorflowProcessor;

import java.util.ArrayList;

public class TensorflowAGP extends AsyncGraphicsProcessor {
    public TensorflowAGP(Bitmap bitmap, DatabaseManager databaseManager, Activity activity, OnTaskCompleted listener)
    {
        super(listener);

        ArrayList<GraphicsProcessor> processors = new ArrayList<>();
        TensorflowProcessor gp = new TensorflowProcessor(TensorflowProcessor.Task.CLASSIFY, activity, databaseManager);
        gp.setImage(bitmap.copy(bitmap.getConfig(), true));
        processors.add(gp);

        task = processors;
    }
}
