package com.hhu.yannick.coinclassificator.AsyncProcessor.Processor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.hhu.yannick.coinclassificator.AsyncProcessor.EvaluationAGP;
import com.hhu.yannick.coinclassificator.SQLite.CoinData;
import com.hhu.yannick.coinclassificator.SQLite.DatabaseManager;
import com.hhu.yannick.coinclassificator.SQLite.FeatureData;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.hhu.yannick.coinclassificator.AsyncProcessor.EllipseAndFeatureAGP.getEnum;

public class EvaluationGP extends GraphicsProcessor {

    private List<Bitmap> testData;
    private List<String> testDataExpected;
    private Type type;
    private DatabaseManager dbm;
    private Context context;

    private String expectedResponse;
    private PrintWriter cnnStream;

    public enum Type{
        FEATURES,
        CNN
    }

    public EvaluationGP(Type type, DatabaseManager dbm, Context context){
        this.type = type;
        task = type.toString() + "_Evaluation";
        this.dbm = dbm;
        this.context = context;
    }

    public EvaluationGP(Type type, String expectedResponse, PrintWriter cnnStream){
        this.type = type;
        task = type.toString() + "_Evaluation";

        this.expectedResponse = expectedResponse;
        this.cnnStream = cnnStream;
    }

    @Override
    protected Status executeProcess() {
        if(type == Type.FEATURES) {
            // generate the ellipse for the testdata
            int size = 244;
            RotatedRect testEllipse = new RotatedRect(new Point(size / 2, size / 2), new Size(size, size), 0);

            loadTestData();

            // do a test for each possible combination
            String[] detector = new String[]{"SIFT", "SURF", "ORB"};
            String[] matcher = new String[]{"LOWE_RATIO_TEST", "SMALLEST_DISTANCE", "DISTANCE_THRESHOLD"};
            for (String d : detector) {
                for (String m : matcher) {
                    Log.d("EVAL", d + m);
                    // open he result file and write the results to it
                    try {
                        PrintWriter file = new PrintWriter("/sdcard/Pictures/Testpictures/" + d + "_" + m + ".txt");
                        doFeatureTests(getEnum(FeatureGP.DetectorType.class, d), getEnum(FeatureGP.MatchMethode.class, m), file);
                        file.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            return Status.PASSED;
        }
        else {
            evaluateCNN();
            return Status.PASSED;
        }
    }

    private void loadTestData(){
        testData = new ArrayList<>();
        testDataExpected = new ArrayList<>();
        String filepath =  "/sdcard/Pictures/Testpictures/testset";

        File[] listOfFiles = (new File(filepath)).listFiles();
        for (File f: listOfFiles) {
            Log.d("EVAL", f.getAbsolutePath());
            testData.add(BitmapFactory.decodeFile(f.getAbsolutePath()));
            String[] s = f.getName().split("_");
            testDataExpected.add(s[0] + "_" + s[1]);
        }
    }

    private void doFeatureTests(FeatureGP.DetectorType detectorType, FeatureGP.MatchMethode matchMethode, PrintWriter  writer){
        // create the FeatureGP instance we will abuse for testing
        FeatureGP tester = new FeatureGP(detectorType, matchMethode, dbm, context);
        Map<CoinData, FeatureData> features = tester.loadFeatures();

        for (int i = 0; i < testData.size(); i++) {
            Mat mat = toMat(testData.get(i));
            Mat dest = new Mat();
            Imgproc.cvtColor(mat, dest, Imgproc.COLOR_RGB2GRAY);
            FeatureData featureData = tester.generateFeatures(dest);

            // match against the coins
            TreeMap<Double, CoinData> result = tester.mAC(featureData, features);
            saveToFile(result, testDataExpected.get(i), writer);

            Log.d("EVAL", i + "/" + testData.size() + " --- " + detectorType.toString() + "_" + matchMethode.toString());
        }
    }

    private static int counter = 0;
    private void evaluateCNN(){
        TreeMap<Double, CoinData> result = (TreeMap<Double, CoinData>)data.get("results");
        saveToFile(result, expectedResponse, cnnStream);
        Log.d("EVAL", "count: " + counter);
        counter++;
    }

    private void saveToFile(TreeMap<Double, CoinData> result, String expected, PrintWriter  writer){
        StringBuilder sb = new StringBuilder();
        sb.append(expected);
        sb.append(";");
        for (Double key : result.keySet()){
            sb.append(key);
            sb.append(":");
            String country = result.get(key).country;
            country = Character.toUpperCase(country.charAt(0)) + country.substring(1, country.length());
            sb.append(country);
            sb.append("_");
            sb.append(result.get(key).value);
            sb.append("|");
        }
        sb.deleteCharAt(sb.length() - 1);
        try {
            writer.println(sb.toString());
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
