package com.hhu.yannick.coinclassificator.AsyncProcessor.Processor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.hhu.yannick.coinclassificator.SQLite.CoinData;
import com.hhu.yannick.coinclassificator.SQLite.DatabaseManager;
import com.hhu.yannick.coinclassificator.SQLite.FeatureData;
import com.hhu.yannick.coinclassificator.SQLite.MatSerializer;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.DMatch;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.features2d.BFMatcher;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.Feature2D;
import org.opencv.features2d.Features2d;
import org.opencv.features2d.ORB;
import org.opencv.imgproc.Imgproc;
import org.opencv.xfeatures2d.SIFT;
import org.opencv.xfeatures2d.SURF;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class FeatureGP extends GraphicsProcessor {
    private DetectorType detectorType;
    public enum DetectorType{
        SIFT,
        SURF,
        ORB
    }

    private MatcherType matcherType;
    public enum MatcherType{
        BRUTEFORCE,
        FLANNBASED
    }

    private DatabaseManager databaseManager;
    private Context context;
    private String directory = null;

    public FeatureGP(DetectorType detectorType, MatcherType matcherType, DatabaseManager databaseManager, Context context){
        this.detectorType = detectorType;
        this.matcherType = matcherType;
        this.databaseManager = databaseManager;
        this.context = context;

        this.task = "Feature: " + detectorType.toString() + ", matcher: " + matcherType.toString();
    }

    public FeatureGP(String directory, DetectorType detectorType, DatabaseManager databaseManager, Context context){
        this.detectorType = detectorType;
        this.databaseManager = databaseManager;
        this.context = context;
        this.directory = directory;

        this.task = "Generate Features: " + detectorType.toString();
    }

    @Override
    protected Status executeProcess() {
        // generate Features from file
        if (directory != null) {
            generateCountry("/sdcard/Pictures/Testpictures/Flags/");
            List<String> cou = databaseManager.getCountrys();
            Log.d("LOAD", cou.toString());
            generateBunch(directory);
            Map<CoinData,FeatureData> cf = databaseManager.getFeaturesByType("SIFT");
            for(CoinData c : cf.keySet())
                Log.d("LOAD", c.country + ", " + c.value + ", " + cf.get(c).start + ", " + cf.get(c).length);
            loadFeatures();
            return Status.PASSED;
        }
        // classify by feature
        else {

            if (!data.containsKey("ellipses"))
                return Status.FAILED;
            if (!data.containsKey("mat") && data.containsKey("bitmap"))
                data.put("mat", toMat((Bitmap) data.get("bitmap")));
            else if (!data.containsKey("mat"))
                return Status.FAILED;

            // load the Coin-Features for the selected detector from the database
            Map<CoinData, FeatureData> features = loadFeatures();

            // generate the Features for the current Coin (slow!!!)
            RotatedRect ellipse = ((ArrayList<RotatedRect>) data.get("ellipses")).get(0);
            FeatureData featureData = generateFeatures((Mat)data.get("mat"), ellipse);

            // match against the coins
            CoinData result = matchAgainstCoins(featureData, features);
            data.put("coin", result);
            return Status.PASSED;
        }
    }

    private FeatureData generateFeatures(Mat data){
        return generateFeatures(data, new RotatedRect(new Point(data.size().width / 2, data.size().height / 2), data.size(), 0));
    }

    private FeatureData generateFeatures(Mat mat, RotatedRect ellipse){
        // generate a mask to lay on the image (the found ellipse)
        Mat mask = Mat.zeros(mat.size(), mat.type());
        Imgproc.ellipse(mask, ellipse, new Scalar(255,255,255), -1);
        MatOfKeyPoint keypoints = new MatOfKeyPoint();

        // create the right detector
        Mat descriptors = new Mat();
        Feature2D detector = null;
        switch (detectorType){
            case SIFT: //detector = SIFT.create(256, 3, 0.04, 10, 1.6);
                detector = SIFT.create();
                break;
            case SURF: detector = SURF.create();
                break;
            case ORB: detector = ORB.create();
                break;
        }

        // compute features
        detector.detectAndCompute(mat, mask, keypoints, descriptors);

        // put them onto the bitmap
        Features2d.drawKeypoints(mat, keypoints, mat);
        data.put("mat", mat);
        data.put("bitmap", toBitmap(mat));

        return new FeatureData(detectorType.toString(), keypoints, descriptors, mask);
    }

    private CoinData matchAgainstCoins(FeatureData input, Map<CoinData, FeatureData> set){

        // create the right matcher
        DescriptorMatcher matcher = null;
        switch (matcherType){
            case BRUTEFORCE: matcher = BFMatcher.create();
                break;
            case FLANNBASED: matcher = DescriptorMatcher.create(DescriptorMatcher.FLANNBASED);
                break;
        }

        // iterate the features and match the input against the coins
        TreeMap<Double, CoinData> result = new TreeMap<>();
        for (CoinData cd : set.keySet()){
            double score = match(input, set.get(cd), matcher);
            Log.d("MATCH", "Country: " + cd.country + ", value: " + cd.value + " -> score: " + score);
            result.put(score, cd);
        }

        // return the highest scoring Coin
        return result.get(result.lastKey());
    }

    private double match(FeatureData input, FeatureData feature, DescriptorMatcher matcher) {
        // do the matching and start evaluation
        MatOfDMatch match = new MatOfDMatch();
        matcher.match(input.descriptor, feature.descriptor, match);

        Log.d("MATCH", "ind: " + input.descriptor.size() + ", fd: " +feature.descriptor.size());
                Log.d("MATCH", "size: " + match.size().toString());

        double maxDist = 0;
        double minDist = Double.MAX_VALUE;

        // Quick calculation of max and min distances between keypoints
        List<DMatch> matches = match.toList();
        for (int i = 0; i < matches.size(); i++) {
            double dist = matches.get(i).distance;
            if (dist < minDist) minDist = dist;
            if (dist > maxDist) maxDist = dist;
        }

        // get only "good" matches (whose distance is less than 2 * minDist)
        int found = 0;
        for (int i = 0; i < matches.size(); i++) {
            if (matches.get(i).distance <= 2 * minDist)
                found++;
        }

        Log.d("MATCH", "found: " + found);

        // return the ratio between "good" and bad matches
        return (double)found / matches.size();
    }

    private void generateCountry(String directory){
        // lookup all files in the give directory
        File folder = new File(directory);

        // create the Feature-Type binary file if not already there
        String binFile = "FLAGS.bin";
        File bin = null;
        File storage = context.getFilesDir();
        for (File fileEntry : storage.listFiles())
            if(fileEntry.getName().equals(binFile)) {
                bin = fileEntry;
                break;
            }
        if(bin == null)
            bin = new File(storage, binFile);

        if(bin.delete()) {
            Log.d("SAVING", "done...");
            bin = new File(storage, binFile);
        }

        for (File fileEntry : folder.listFiles()) {
            String file = fileEntry.getName();
            Bitmap bitmap = BitmapFactory.decodeFile(fileEntry.getAbsolutePath());

            Mat mat = new Mat();
            Bitmap bmp32 = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            Utils.bitmapToMat(bmp32, mat);

            Log.d("SAVING", "type: " + mat.type());

            String country = file.substring(0, file.indexOf("."));

            byte[] binary = MatSerializer.matToBytes(mat);

            // append to binary file
            try {
                RandomAccessFile output = new RandomAccessFile(bin, "rw");
                int lastByte = (int)output.length();
                output.seek(lastByte);
                try {
                    output.write(binary);
                } finally {
                    output.close();

                    // save the positions in the database
                    Log.d("SAVING", "lastbyte: " + lastByte + ", to: " + (lastByte + binary.length));
                    databaseManager.putCountry(country, lastByte, binary.length);
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private void generateBunch(String directory){
        // gather the countrys and coins
        List<String> countrys = databaseManager.getCountrys();
        Map<String, CoinData[]> coins = databaseManager.getCoins();

        // lookup all files in the give directory
        File folder = new File(directory);

        // create the Feature-Type binary file if not already there
        String binFile = detectorType.toString() + ".bin";
        File bin = null;
        File storage = context.getFilesDir();
        for (File fileEntry : storage.listFiles())
            if(fileEntry.getName().equals(binFile)) {
                bin = fileEntry;
                break;
            }
        if(bin == null)
            bin = new File(storage, binFile);


        if(bin.delete()) {
            Log.d("SAVING", "done...");
            bin = new File(storage, binFile);
        }

        Log.d("SAVING", bin.getAbsolutePath());

        // generate requested Features for all given Coin-Files
        for (File fileEntry : folder.listFiles()) {
            String file = fileEntry.getName();
            Mat fileMat = loadGrayImage(fileEntry.getAbsolutePath());
            FeatureData feature = generateFeatures(fileMat);

            // read the Country and Coin-type from the filename
            String[] tmp = file.split("_");
            String country = tmp[0];
            int value = Integer.parseInt(tmp[1].substring(0, tmp[1].indexOf(".")));

            //Log.d("SIFT","size: keypoints: " + feature.keypoints.size() + ", desc: " + feature.descriptor.size() + ", mask: " + feature.mask.size());
            //Log.d("SIFT", "bytes: keypoints: " + MatSerializer.matToBytes(feature.keypoints).length + ", desc: " + MatSerializer.matToBytes(feature.descriptor).length + ", mask: " + MatSerializer.matToBytes(feature.mask).length);
            // if country not already there, add it
            /*if(!countrys.contains(country)){
                countrys.add(country);
                coins.put(country, new CoinData[3]);
                databaseManager.putCountry(country);
            }*/
            // if coin not there add as well
            if(coins.get(country) == null || coins.get(country)[value] == null){
                databaseManager.putCoin(new CoinData(value, country));
            }

            // serialize data
            byte[] binary = MatSerializer.matToBytes(feature.descriptor);

            // append to binary file
            try {
                RandomAccessFile output = new RandomAccessFile(bin, "rw");
                int lastByte = (int)output.length();
                output.seek(lastByte);
                try {
                    output.write(binary);
                } finally {
                    output.close();

                    // save the positions in the database
                    Log.d("SAVING", "lastbyte: " + lastByte + ", to: " + (lastByte + binary.length));
                    feature.start = lastByte;
                    feature.length = binary.length;
                    databaseManager.putFeature(feature, new CoinData(value, country));
                }
            }catch (Exception e){
                e.printStackTrace();
            }

            // save to database
            //databaseManager.putFeature(feature, new CoinData(value, country));
            Log.d("FEATURE", fileEntry.getName());
        }
    }

    private Mat loadGrayImage(String name){
        Bitmap bitmap = BitmapFactory.decodeFile(name);

        Mat data = new Mat();
        Bitmap bmp32 = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Utils.bitmapToMat(bmp32, data);
        Imgproc.cvtColor(data, data, Imgproc.COLOR_RGB2GRAY);
        return data;
    }

    private Map<CoinData, FeatureData> loadFeatures(){
        // load the Feature-Type binary file
        String binFile = detectorType.toString() + ".bin";
        File bin = null;
        File storage = context.getFilesDir();

        // find it
        for (File fileEntry : storage.listFiles())
            if(fileEntry.getName().equals(binFile)) {
                bin = fileEntry;
                break;
            }

        // load the information about the binarys from the database
        Map<CoinData, FeatureData> featureDataMap = databaseManager.getFeaturesByType(detectorType.toString());

        RandomAccessFile output = null;
        try {
            // open and read from binary file
            output = new RandomAccessFile(bin, "rw");
            for (CoinData c : featureDataMap.keySet()) {
                FeatureData featureData = featureDataMap.get(c);
                byte[] binary = new byte[featureData.length];

                    output.seek(featureData.start);
                    try {
                        Log.d("LOAD", "start: " + featureData.start + ", len: " + featureData.length);
                        output.read(binary, 0, binary.length);
                    } finally {
                        // put the descriptor to the features
                        Mat mat = MatSerializer.matFromBytes(binary);
                        featureData.descriptor = mat;
                    }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            try {
                // close the file
                if (output != null)
                    output.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return featureDataMap;
    }
}
