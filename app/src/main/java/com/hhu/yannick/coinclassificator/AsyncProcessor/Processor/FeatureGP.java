package com.hhu.yannick.coinclassificator.AsyncProcessor.Processor;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.hhu.yannick.coinclassificator.R;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private Activity activity;
    private String directory = null;
    private boolean extendedFeatureDraw = false;

    public FeatureGP(DetectorType detectorType, MatchMethode matchMethode,
                     DatabaseManager databaseManager, Context context, Activity activity){
        this.detectorType = detectorType;
        this.matchMethode = matchMethode;
        this.databaseManager = databaseManager;
        this.context = context;
        this.activity = activity;

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
            //generateInformation();
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
            // load information
            data.put("information", databaseManager.loadInformation(result));
            return Status.PASSED;
        }
    }

    /*private*/ FeatureData generateFeatures(Mat data){
        return generateFeatures(data, new RotatedRect(new Point(data.size().width / 2, data.size().height / 2), data.size(), 0));
    }

    public void setExtendedFeatureDraw(boolean e){
        this.extendedFeatureDraw = e;
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
                break;

            case SURF:
                if(getInt("nFeaturesMax") > 0) {
                    int nMax = getInt("nFeaturesMax"), nMin = getInt("nFeaturesMin");
                    int lowerHessianThreshold = 0, higherHessianThreshold = 800;
                    int hessianThreshold = (higherHessianThreshold - lowerHessianThreshold) / 2;
                    do {
                        detector = SURF.create(hessianThreshold, 4, 3, false, false);
                        detector.detectAndCompute(mat, mask, keypoints, descriptors);

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
                break;
            case ORB:
                if(getInt("nFeaturesMax") > 0)
                    detector = ORB.create(getInt("nFeaturesMax"), 1.2f, 8, 31,
                    0, 2, ORB.HARRIS_SCORE, 31,  20);
                else
                    detector = ORB.create();
                detector.detectAndCompute(mat, mask, keypoints, descriptors);
                break;
        }

        // draw keypoints according to chosen style
        if(extendedFeatureDraw)
            Features2d.drawKeypoints(mat, keypoints, mat, new Scalar(255, 255, 0), 4);
        else
            Features2d.drawKeypoints(mat, keypoints, mat);

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
        /*for (Double d : result.keySet()) {
            CoinData cd = result.get(d);
            Log.d("MATCH", "Country: " + cd.country + ", value: " + cd.value + " -> score: " + d);
        }*/
        data.put("results", result);
        DecimalFormat formatter = new DecimalFormat("#.00");

        // return the highest scoring Coin or with lowest distance
        if (matchMethode == MatchMethode.SMALLEST_DISTANCE) {
            data.put("accuracy", "Score: " + formatter.format(result.firstKey()));
            return result.get(result.firstKey());
        }
        else {
            data.put("accuracy", "Score: " + formatter.format(result.lastKey() * 100));
            return result.get(result.lastKey());
        }
    }

    TreeMap<Double, CoinData> mAC(FeatureData input, Map<CoinData, FeatureData> set) {

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
        /*for (Double d : result.keySet()) {
            CoinData cd = result.get(d);
            Log.d("MATCH", "Country: " + cd.country + ", value: " + cd.value + " -> score: " + d);
        }*/

        return result;
    }

    private double match(FeatureData input, FeatureData feature, DescriptorMatcher matcher) {
        // do the matching and start evaluation
        MatOfDMatch match = new MatOfDMatch();

        // match according to the chosen method
        List<DMatch> matches = null;
        switch (matchMethode) {
            case LOWE_RATIO_TEST:
                if(detectorType != DetectorType.ORB) {
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
                            if (ma[0].distance < ratioThresh * ma[1].distance
                                    || ma[1].distance < ratioThresh * ma[0].distance) {
                                //listOfGoodMatches.add(matches[0]);
                                found++;
                            }
                        }
                    }
                    return (double) found / knnMatches.size();
                }

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
        }
    }

    private void generateInformation(){

        // lookup the text file in the give directory
        File text = new File(directory);
        // read content and give to database
        try(BufferedReader br = new BufferedReader(new FileReader(text))) {
            for(String line; (line = br.readLine()) != null; ) {
                String[] s = line.split(":");
                String country = s[0].split("_")[0];
                int value = Integer.parseInt(s[0].split("_")[1]);

                // put into database
                databaseManager.putInformation(new CoinData(value, -1, country), s[1]);
            }
        }catch (Exception e){
            e.printStackTrace();
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

    /*private*/ Map<CoinData, FeatureData> loadFeatures(){

        // load the information about the binarys from the database
        Map<CoinData, FeatureData> featureDataMap = databaseManager.getFeaturesByType(detectorType.toString());
        try{
            // take the right resource
            InputStream inputStream;
            if(detectorType == DetectorType.SIFT)
                inputStream = activity.getResources().openRawResource(R.raw.sift);
            else if(detectorType == DetectorType.SURF)
                inputStream = activity.getResources().openRawResource(R.raw.surf);
            else
                inputStream = activity.getResources().openRawResource(R.raw.orb);

            // read features, sorted by lowest start-byte
            Set<CoinData> keySet = new HashSet<>(featureDataMap.keySet());
            while (keySet.size() > 0){
                int lowestStart = Integer.MAX_VALUE;
                CoinData lowestKey = null;

                // find lowest
                for (CoinData c : keySet){
                    if(featureDataMap.get(c).start < lowestStart){
                        lowestKey = c;
                        lowestStart = featureDataMap.get(c).start;
                    }
                }

                // read from InputStream
                byte[] buffer = new byte[featureDataMap.get(lowestKey).length];
                inputStream.read(buffer, 0, buffer.length);
                featureDataMap.get(lowestKey).descriptor = MatSerializer.matFromBytes(buffer);

                // remove the filled key
                keySet.remove(lowestKey);
            }
            inputStream.close();

        }catch (Exception e){
            e.printStackTrace();
        }
        return featureDataMap;
    }

    private void loadFlag(String country){
       try {
            InputStream inputStream = activity.getResources().openRawResource(R.raw.flags);

            int[] binSL = databaseManager.getCountryFlag(country);
            Mat flag = null;

            byte[] buffer = new byte[binSL[1]];
            inputStream.skip(binSL[0]);
            inputStream.read(buffer);

            // convert flag
            flag = MatSerializer.matFromBytes(buffer);
            if(flag != null)
                data.put("flag", toBitmap(flag));

            inputStream.close();

        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
