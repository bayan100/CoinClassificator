package com.hhu.yannick.coinclassificator.SQLite;

import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;

public class FeatureData {
    public String type;
    public MatOfKeyPoint keypoints;
    public Mat descriptor;
    public Mat mask;

    public int start, length;

    public FeatureData(String type){
        this.type = type;
    }

    public FeatureData(String type, int start, int length) {
        this.type = type;
        this.start = start;
        this.length = length;
    }


    public FeatureData(String type, MatOfKeyPoint keypoints, Mat descriptor, Mat mask){
        this.type = type;
        this.keypoints = keypoints;
        this.descriptor = descriptor;
        this.mask = mask;
    }
}
