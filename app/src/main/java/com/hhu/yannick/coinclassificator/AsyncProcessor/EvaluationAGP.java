package com.hhu.yannick.coinclassificator.AsyncProcessor;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.BlurGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.ContourGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.EllipseGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.EvaluationGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.GraphicsProcessor;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.ResizeGP;
import com.hhu.yannick.coinclassificator.SQLite.CoinData;
import com.hhu.yannick.coinclassificator.SQLite.DatabaseManager;
import com.hhu.yannick.coinclassificator.Tensorflow.TensorflowProcessor;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class EvaluationAGP extends AsyncGraphicsProcessor {

    private List<String> testData;
    private List<String> testDataExpected;

    public EvaluationAGP(DatabaseManager dbm, Context context, OnTaskCompleted listener) {
        super(listener);

        ArrayList<GraphicsProcessor> processors = new ArrayList<>();
        EvaluationGP gp = new EvaluationGP(EvaluationGP.Type.FEATURES, dbm, context);
        processors.add(gp);
        task = processors;

    }

    public EvaluationAGP(DatabaseManager dbm, Activity activity, PrintWriter cnnStream, OnTaskCompleted listener) {
        super(listener);

        loadTestData();
        ArrayList<GraphicsProcessor> processors = new ArrayList<>();
        for (int i = 120; i < 160 && i < testData.size(); i++) {

            EvaluationGP egp = new EvaluationGP(EvaluationGP.Type.LOADBITMAP, testData.get(i));
            ResizeGP gp = new ResizeGP(ResizeGP.Type.LINEAR);
            //gp.setImage(bitmap.copy(bitmap.getConfig(), true));
            processors.add(egp);
            processors.add(gp);
            processors.add(new BlurGP(BlurGP.Type.MEDIAN));
            processors.add(new ContourGP(ContourGP.Type.CANNYEDGE));
            processors.add(new ContourGP(ContourGP.Type.FIND));
            processors.add(new ContourGP(ContourGP.Type.SPLIT));
            processors.add(new ContourGP(ContourGP.Type.FILTER));
            processors.add(new EllipseGP(EllipseGP.Type.FIND));

            TensorflowProcessor tf = new TensorflowProcessor(TensorflowProcessor.Task.CLASSIFY, activity, dbm);
            tf.changeModel(true);

            egp = new EvaluationGP(EvaluationGP.Type.LOADBITMAP, testData.get(i));
            processors.add(egp);
            gp = new ResizeGP(ResizeGP.Type.RESIZE_ELLIPSE);
            //gp.setImage(bitmap.copy(bitmap.getConfig(), true));
            processors.add(gp);
            processors.add(new ResizeGP(ResizeGP.Type.CROP));
            gp = new ResizeGP(ResizeGP.Type.LINEAR);
            gp.setParameter("width", (Integer) tf.getParameter("tensorImageWidth"));
            gp.setParameter("height", (Integer) tf.getParameter("tensorImageHeight"));
            processors.add(gp);

            processors.add(tf);
            processors.add(new EvaluationGP(EvaluationGP.Type.CNN, testDataExpected.get(i), cnnStream));
        }
        task = processors;
    }

    private void loadTestData(){
        testData = new ArrayList<>();
        testDataExpected = new ArrayList<>();
        String filepath =  "/sdcard/Pictures/Testpictures/testset_cnn";

        File[] listOfFiles = (new File(filepath)).listFiles();
        for (File f: listOfFiles) {
            if (f.isFile()) {
                Log.d("EVAL", f.getAbsolutePath());
                testData.add(f.getAbsolutePath());
                //testData.add(BitmapFactory.decodeFile(f.getAbsolutePath()));
                String[] s = f.getName().split("_");
                testDataExpected.add(s[0] + "_" + s[1]);
            }
        }
    }
}
