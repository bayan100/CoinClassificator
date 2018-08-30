package com.hhu.yannick.coinclassificator.AsyncProcessor.Processor;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.opencv.core.CvType.CV_8UC3;

public class ContourGP extends GraphicsProcessor {

    private Type type;
    public enum Type{
        CANNYEDGE,
        FIND,
        FILTER,
        SPLIT,
        MERGE,
        DRAW
    }

    public ContourGP(Type type){
        this.type = type;
        task = type.toString() + "_Contour";

        // parameter
        parameter.put("minArea", 30);
        parameter.put("threshold1", 50);
        parameter.put("threshold2", 130);
        parameter.put("nBasePoints", 7);
        parameter.put("numberOfRetainedContours", 5);
        parameter.put("endpointRadius", 7.0);
        parameter.put("mTolerance", 10.0);
    }

    @Override
    protected Status executeProcess() {
        // choose from the right task to perform
        switch (type){
            case CANNYEDGE: return cannyEdge();
            case FIND: return find();
            case FILTER: return filter();
            case SPLIT: return split();
            case MERGE: return merge();
            case DRAW: return draw();
        }

        return Status.PASSED;
    }

    private Status cannyEdge(){
        Mat mat = null;
        if (data.containsKey("mat"))
            mat = (Mat) data.get("mat");
        else if (data.containsKey("bitmap"))
            mat = toMat((Bitmap) data.get("bitmap"));
        else
            return Status.FAILED;

        int threshold1 = getInt("threshold1");
        int threshold2 = getInt("threshold2");

        Mat im_canny = new Mat();
        Imgproc.Canny(mat, im_canny, threshold1, threshold2);

        data.put("mat", im_canny);

        return Status.PASSED;
    }

    private Status find(){
        Mat mat = null;
        if (data.containsKey("mat"))
            mat = (Mat) data.get("mat");
        else
            return Status.FAILED;

        Mat hirachy = new Mat();
        ArrayList<MatOfPoint> tempContours = new ArrayList<>();

        Imgproc.findContours(mat, tempContours, hirachy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_NONE);

        // Filter the contours to prevent unrealistic ellipses
        ArrayList<Contour> contours = Contour.create(tempContours);
        int minArea = getInt("minArea");

        // remove those who are too small to be part of the main ellipse
        for (int i = 0; i < contours.size(); i++) {
            if(contours.get(i).area < minArea) {
                contours.remove(i);
                i--;
            }
        }

        // sort by maximum area DESC order
        Collections.sort(contours);

        data.put("contours", contours);
        return Status.PASSED;
    }


    private Status filter() {

        ArrayList<Contour> contours = null;
        if(data.containsKey("contours"))
            contours = (ArrayList<Contour>) data.get("contours");
        else
            return Status.FAILED;

        int nBasePoints = getInt("nBasePoints");
        int numberOfRetainedContours = getInt("numberOfRetainedContours");

        // calculate the area under the contour with accurate trapeze shape
        // and find the centerpoint of the contour
        for (int i = 0; i < contours.size(); i++) {
            Contour c = contours.get(i);
            c.calculateArea(nBasePoints);
            c.calculateCenterPoint();
            c.calculateClosure();
        }

        // resort the contours
        Collections.sort(contours);

        // average those, weighted by their area for the best few
        double xmean = 0, ymean = 0, totalarea = 0;
        for (int i = 0; i < contours.size() /*&& i < numberOfRetainedContours*/; i++) {
            Contour c = contours.get(i);
            xmean += c.center.x * c.area;
            ymean += c.center.y * c.area;
            totalarea += c.area;
        }
        // approximate coin center
        data.put("approximateCenter", new Point(xmean / totalarea, ymean / totalarea));
        //additionalData.put("contours", new ArrayList<Contour>(contours.subList(0, numberOfRetainedContours)));

        return Status.PASSED;
    }

    private Status split() {
        ArrayList<Contour> contours;
        if(data.containsKey("contours"))
            contours = (ArrayList<Contour>) data.get("contours");
        else
            return Status.FAILED;

        int minArea = getInt("minArea");

        // try to split each contour
        ArrayList<Contour> tempContours = new ArrayList<>();
        for (int i = 0; i < contours.size(); i++) {
            ContourMap map = ContourMap.fromContour(contours.get(i).points);

            // get the split-points
            List<ContourNode> splitpoints = map.getSplitpoints();

            // remove the splitpoints from the map data
            map.removeSplitpoints();

            // remove the old contour
            contours.remove(i);
            i--;

            // convert the map back to single contours
            List<List<Point>> points = map.convertToPoints();
            for (int j = 0; j < points.size(); j++) {
                if (points.get(j).size() > 5) {
                    MatOfPoint mat = new MatOfPoint();
                    mat.fromList(points.get(j));
                    Contour c = new Contour(mat);

                    // prefilter contour
                    if (c.area >= minArea) {
                        tempContours.add(c);
                    }
                }
            }
        }

        contours.addAll(tempContours);

        // resort the contours
        Collections.sort(contours);

        return Status.PASSED;
    }

    private Status merge(){
        ArrayList<Contour> contours;
        if(data.containsKey("contours"))
            contours = (ArrayList<Contour>) data.get("contours");
        else
            return Status.FAILED;

        double endpointRadius = (Double)parameter.get("endpointRadius");
        double mTolerance = (Double)parameter.get("mTolerance");

        // calculate endpoints
        for (int i = 0; i < 40 && i < contours.size(); i++)
            contours.get(i).calculateEndDirections();

        // try to merge every contour
        boolean merge;
        do {
            merge = false;
            for (int i = 0; i < contours.size(); i++) {
                for (int j = i + 1; j < contours.size(); j++) {
                    // merger?
                    if (contours.get(i).tryMerge(contours.get(j), endpointRadius, mTolerance)) {
                        // remove the merged contour j
                        contours.remove(j);
                        j--;
                        merge = true;
                    }
                }
            }
        }while (merge);

        // resort the contours
        Collections.sort(contours);

        return Status.PASSED;
    }

    private Status draw() {
        ArrayList<Contour> contours;
        Mat target;
        if(data.containsKey("contours") && data.containsKey("mat")) {
            target = (Mat)data.get("mat");
            contours = (ArrayList<Contour>) data.get("contours");
        }
        else
            return Status.FAILED;

        Mat contoursMat = Mat.zeros(target.rows(), target.cols(), CV_8UC3);
        if(data.containsKey("approximateCenter")) {
            Point approximateCenter = (Point)data.get("approximateCenter");
            if (approximateCenter != null)
                Imgproc.drawMarker(contoursMat, approximateCenter, new Scalar(255, 255, 255));
        }
        Bitmap contoursBM = toBitmap(contoursMat);

        for (int i = 0; i < 40 && i < contours.size(); i++) {
            if (i != 0)
                contours.get(i).draw(contoursBM, Color.rgb((int) ((contours.size() - i) * (255f / contours.size())), (int) (i * (255f / contours.size())), 0));
                //contours.get(i).draw(contoursBM, Color.HSVToColor(new float[] {i * (255f / contours.size()), 255f, 255f}));
            else {
                contours.get(i).draw(contoursBM, Color.rgb(255, 255, 255));
            }
            //contours.get(i).drawMultiColored(contoursBM);
        }

        data.put("bitmap", contoursBM);
        return Status.PASSED;
    }
}
