package com.hhu.yannick.coinclassificator.AsyncProcessor.Processor;

import android.graphics.Bitmap;
import android.util.Log;

import com.hhu.yannick.coinclassificator.SQLite.CoinData;
import com.hhu.yannick.coinclassificator.SQLite.DatabaseManager;
import com.hhu.yannick.coinclassificator.SQLite.FeatureData;

import org.opencv.core.Core;
import org.opencv.core.DMatch;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
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

import java.util.ArrayList;
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
    private RotatedRect ellipse;

    public FeatureGP(DetectorType detectorType, MatcherType matcherType, DatabaseManager databaseManager){
        this.detectorType = detectorType;
        this.matcherType = matcherType;
        this.databaseManager = databaseManager;

        this.task = "Feature: " + detectorType.toString() + ", matcher: " + matcherType.toString();
    }

    @Override
    protected Status executeProcess() {
        Log.d("KEYS", data.keySet().toString());

        if(!data.containsKey("ellipses"))
            return Status.FAILED;
        if(!data.containsKey("mat") && data.containsKey("bitmap"))
            data.put("mat", toMat((Bitmap) data.get("bitmap")));
        else if(!data.containsKey("mat"))
            return Status.FAILED;

        // load the Coin-Features for the selected detector from the database
        Map<CoinData, FeatureData> features = databaseManager.getFeaturesByType(detectorType.toString());

        // generate the Features for the current Coin (slow!!!)
        ellipse = ((ArrayList<RotatedRect>)data.get("ellipses")).get(0);

        Log.d("ELLIPSE", ellipse.toString());
        FeatureData featureData = generateFeatures();

        // match against the coins
        CoinData result = matchAgainstCoins(featureData, features);
        data.put("coin", result);
        return Status.PASSED;
    }

    private FeatureData generateFeatures(){
        Mat mat = (Mat)data.get("mat");

        // generate a mask to lay on the image (the found ellipse)
        Mat mask = Mat.zeros(mat.size(), mat.type());
        Imgproc.ellipse(mask, ellipse, new Scalar(255,255,255), -1);
        MatOfKeyPoint keypoints = new MatOfKeyPoint();

        Log.d("BP", mat.size().toString());
        Core.bitwise_and(mat, mask, mat);
        Log.d("BP", mat.size().toString());
        data.put("bitmap", toBitmap(mat));

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
}
