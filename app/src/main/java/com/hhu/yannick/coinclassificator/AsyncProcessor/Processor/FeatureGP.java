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
import org.opencv.features2d.FeatureDetector;
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

    private MatchMethode matchMethode;
    public enum MatchMethode{
        LOWE_RATIO_TEST,
        SMALLEST_DISTANCE,
        DISTANCE_THRESHOLD
    }

    private DatabaseManager databaseManager;
    private Context context;
    private String directory = null;

    public FeatureGP(DetectorType detectorType, MatchMethode matchMethode, DatabaseManager databaseManager, Context context){
        this.detectorType = detectorType;
        this.matchMethode = matchMethode;
        this.databaseManager = databaseManager;
        this.context = context;

        parameter.put("nFeaturesMax", -1);
        parameter.put("nFeaturesMin", -1);

        this.task = "Feature: " + detectorType.toString() + ", matcher: " + matchMethode.toString();
    }

    public FeatureGP(String directory, DetectorType detectorType, DatabaseManager databaseManager, Context context){
        this.detectorType = detectorType;
        this.databaseManager = databaseManager;
        this.context = context;
        this.directory = directory;

        parameter.put("nFeaturesMax", 256);
        parameter.put("nFeaturesMin", 128);

        this.task = "Generate Features: " + detectorType.toString();
    }

    @Override
    protected Status executeProcess() {
        // generate Features from file
        if (directory != null) {
            //generateCountry("/sdcard/Pictures/Testpictures/flags/");
            //generateBunch(directory);
            //loadFeatures();
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
            loadFlag(result.country);
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
            case SIFT:
                // create according to maximum number of features
                if(getInt("nFeaturesMax") > 0)
                    detector = SIFT.create(getInt("nFeaturesMax"), 3, 0.04, 10, 1.6);
                else
                    detector = SIFT.create();

                // compute features
                detector.detectAndCompute(mat, mask, keypoints, descriptors);
                // draw features on bitmap
                Features2d.drawKeypoints(mat, keypoints, mat);
                break;

            case SURF:
                if(getInt("nFeaturesMax") > 0) {
                    int nMax = getInt("nFeaturesMax"), nMin = getInt("nFeaturesMin");
                    int lowerHessianThreshold = 0, higherHessianThreshold = 800;
                    int hessianThreshold = (higherHessianThreshold - lowerHessianThreshold) / 2;
                    do {
                        detector = SURF.create(hessianThreshold, 4, 3, false, false);
                        detector.detectAndCompute(mat, mask, keypoints, descriptors);

                        Log.d("SURF", "thresh: " + hessianThreshold + ", desc: " + keypoints.size().height);

                        // modify the threshold to get into the right feature range
                        // binary search on the threshold
                        if(keypoints.size().height > nMax) {
                            lowerHessianThreshold = hessianThreshold;
                            higherHessianThreshold *= 2;
                        }
                        else if(keypoints.size().height < nMin) {
                            higherHessianThreshold = hessianThreshold;
                        }
                        hessianThreshold = (higherHessianThreshold - lowerHessianThreshold) / 2;
                    } while ((keypoints.size().height > nMax || keypoints.size().height < nMin)
                             && (hessianThreshold > 100 && hessianThreshold < 10000));
                }
                else {
                    detector = SURF.create(200, 4, 3, false, false);
                    detector.detectAndCompute(mat, mask, keypoints, descriptors);
                }
                // draw keypoints
                Features2d.drawKeypoints(mat, keypoints, mat, new Scalar(255, 255, 0), 4);

                break;
            case ORB:
                if(getInt("nFeaturesMax") > 0)
                    detector = ORB.create(getInt("nFeaturesMax"), 1.2f, 8, 31,
                    0, 2, ORB.HARRIS_SCORE, 31,  20);
                else
                    detector = ORB.create();
                detector.detectAndCompute(mat, mask, keypoints, descriptors);
                // draw keypoints
                Features2d.drawKeypoints(mat, keypoints, mat);
                break;
        }


        // put them onto the bitmap
        data.put("mat", mat);
        data.put("bitmap", toBitmap(mat));

        return new FeatureData(detectorType.toString(), keypoints, descriptors, mask);
    }

    private CoinData matchAgainstCoins(FeatureData input, Map<CoinData, FeatureData> set) {

        // create the right matcher
        DescriptorMatcher matcher = null;
        if (input.type.equals("ORB"))
            matcher = BFMatcher.create(BFMatcher.BRUTEFORCE_HAMMING, true);
        else
            matcher = BFMatcher.create();

        // iterate the features and match the input against the coins
        TreeMap<Double, CoinData> result = new TreeMap<>();
        for (CoinData cd : set.keySet()) {
            double score = match(input, set.get(cd), matcher);
            result.put(score, cd);
        }
        for (Double d : result.keySet()) {
            CoinData cd = result.get(d);
            Log.d("MATCH", "Country: " + cd.country + ", value: " + cd.value + " -> score: " + d);
        }

        // return the highest scoring Coin or with lowest distance
        if (matchMethode == MatchMethode.SMALLEST_DISTANCE)
            return result.get(result.firstKey());
        else
            return result.get(result.lastKey());
    }

    private double match(FeatureData input, FeatureData feature, DescriptorMatcher matcher) {
        // do the matching and start evaluation
        MatOfDMatch match = new MatOfDMatch();

        // match according to the chosen method
        List<DMatch> matches = null;
        switch (matchMethode) {
            case LOWE_RATIO_TEST:
                List<MatOfDMatch> knnMatches = new ArrayList<>();

                // k-nearest-neighbors with k=2 for Lowe
                matcher.knnMatch(input.descriptor, feature.descriptor, knnMatches, 2);
                int found = 0;
                // filter matches using the Lowe's ratio test
                double ratioThresh = 0.7;
                //List<DMatch> listOfGoodMatches = new ArrayList<>();
                for (int i = 0; i < knnMatches.size(); i++) {
                    if (knnMatches.get(i).rows() > 1) {
                        DMatch[] ma = knnMatches.get(i).toArray();
                        if (ma[0].distance < ratioThresh * ma[1].distance) {
                            //listOfGoodMatches.add(matches[0]);
                            found++;
                        }
                    }
                }
                return (double) found / knnMatches.size();


            // intuitive
            case SMALLEST_DISTANCE:
                // simple match
                matcher.match(input.descriptor, feature.descriptor, match);
                matches = match.toList();

                // calculate the total distance per keypoint
                double total = 0;

                for (int i = 0; i < matches.size(); i++) {
                    total += matches.get(i).distance;
                }
                total /= matches.size();
                return total;

            case DISTANCE_THRESHOLD:
                // simple match
                matcher.match(input.descriptor, feature.descriptor, match);
                matches = match.toList();

                double maxDist = 0;
                double minDist = Double.MAX_VALUE;

                // Quick calculation of max and min distances between keypoints
                for (int i = 0; i < matches.size(); i++) {
                    double dist = matches.get(i).distance;
                    if (dist < minDist) minDist = dist;
                    if (dist > maxDist) maxDist = dist;
                }

                // get only "good" matches (whose distance is less than 2 * minDist)
                int found2 = 0;
                for (int i = 0; i < matches.size(); i++) {
                    if (matches.get(i).distance <= 2 * minDist)
                        found2++;
                }

                // return the ratio between "good" and bad matches
                return (double) found2 / matches.size();
        }
        return 0;
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
            int value, version = 0;
            if(tmp.length == 2)
                value = Integer.parseInt(tmp[1].substring(0, tmp[1].indexOf(".")));
            else {
                value = Integer.parseInt(tmp[1]);
                version = Integer.parseInt(tmp[2].substring(0, tmp[2].indexOf(".")));
            }

            // if coin not there add as well
            boolean found = false;
            if(coins.get(country) != null){
                for (int i = 0; i < coins.get(country).length; i++) {
                    if(coins.get(country)[i].value == value && coins.get(country)[i].version == version){
                        found = true;
                        break;
                    }
                }
            }
            if(!found)
                databaseManager.putCoin(new CoinData(value, version, country));

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
                    databaseManager.putFeature(feature, new CoinData(value, version, country));
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

    private void loadFlag(String country){
        String binFile = "FLAGS.bin";
        File bin = null;
        File storage = context.getFilesDir();

        // find it
        for (File fileEntry : storage.listFiles())
            if(fileEntry.getName().equals(binFile)) {
                bin = fileEntry;
                break;
            }

        // load the information about the binarys from the database
        int[] binSL = databaseManager.getCountryFlag(country);
        Mat flag = null;
        RandomAccessFile output = null;
        try {
            // open and read from binary file
            output = new RandomAccessFile(bin, "rw");

                byte[] binary = new byte[binSL[1]];

                output.seek(binSL[0]);
                try {
                    output.read(binary, 0, binary.length);
                } finally {
                    // convert flag
                    flag = MatSerializer.matFromBytes(binary);
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
        if(flag != null)
            data.put("flag", toBitmap(flag));
    }
}
