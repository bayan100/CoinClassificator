package com.hhu.yannick.coinclassificator.AsyncProcessor;

import android.app.Activity;
import android.graphics.Bitmap;
import android.util.Log;

import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.BlurGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.ContourGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.EllipseGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.GraphicsProcessor;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.ResizeGP;
import com.hhu.yannick.coinclassificator.SQLite.DatabaseManager;
import com.hhu.yannick.coinclassificator.Tensorflow.TensorflowProcessor;

import java.nio.IntBuffer;
import java.util.ArrayList;

public class EllipseAndTensorflowAGP extends AsyncGraphicsProcessor {
    public EllipseAndTensorflowAGP(Bitmap bitmap, DatabaseManager databaseManager, Activity activity, OnTaskCompleted listener)
    {
        super(listener);

        ArrayList<GraphicsProcessor> processors = new ArrayList<>();
        ResizeGP gp = new ResizeGP(ResizeGP.Type.LINEAR);
        gp.setImage(bitmap.copy(bitmap.getConfig(), true));
        processors.add(gp);
        processors.add(new BlurGP(BlurGP.Type.MEDIAN));
        processors.add(new ContourGP(ContourGP.Type.CANNYEDGE));
        processors.add(new ContourGP(ContourGP.Type.FIND));
        processors.add(new ContourGP(ContourGP.Type.SPLIT));
        processors.add(new ContourGP(ContourGP.Type.FILTER));
        processors.add(new EllipseGP(EllipseGP.Type.FIND));

        TensorflowProcessor tf = new TensorflowProcessor(TensorflowProcessor.Task.CLASSIFY, activity, databaseManager);

        gp = new ResizeGP(ResizeGP.Type.RESIZE_ELLIPSE);
        gp.setImage(bitmap.copy(bitmap.getConfig(), true));
        processors.add(gp);
        processors.add(new ResizeGP(ResizeGP.Type.CROP));
        gp = new ResizeGP(ResizeGP.Type.LINEAR);
        gp.setParameter("width", (Integer)tf.getParameter("tensorImageWidth"));
        gp.setParameter("height", (Integer)tf.getParameter("tensorImageHeight"));
        Log.d("TENSOR", tf.getParameter("tensorImageWidth") + ", " + gp.getParameter("width"));
        processors.add(gp);

        //tf.set("bitmap", bitmap.copy(bitmap.getConfig(), true));
        processors.add(tf);

        task = processors;
    }
}
