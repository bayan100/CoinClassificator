package com.hhu.yannick.coinclassificator.AsyncProcessor.Processor;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.util.Log;

import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Contour implements Comparable {
    MatOfPoint data;
    Point[] points;
    double[] endDirections;
    Point[] endVectors;

    Point center;
    double area;
    private int centerLinePoint;

    Contour(MatOfPoint data) {
        this.data = data;

        // convert and find the dimension in space
        points = data.toArray();
        double[] size = new double[4];
        size[0] = points[0].x;
        size[2] = points[0].y;

        for (Point p : points) {
            if (p.x < size[0]) {
                size[0] = p.x;
            }
            if (p.x > size[1]) {
                size[1] = p.x;
            }
            if (p.y < size[2]) {
                size[2] = p.y;
            }
            if (p.y > size[3]) {
                size[3] = p.y;
            }
        }

        // rough area estimation
        area = (size[1] - size[0]) * (size[3] - size[2]);
    }

    void calculateArea(int nBasePoints){
        // calculate the area under the contour with accurate trapeze shape
        // reset area
        double area = 0;

        // base line from both endpoints
        double sy = points[points.length - 1].y - points[0].y;
        double sx = points[points.length - 1].x - points[0].x;
        double m = sy / sx;
        double m_ = m + 1 / m;
        double c = points[0].y - m * points[0].x;

        double side = 0;
        double h0 = 0;
        double xj = 0, yj = 0;
        double xj_ = 0, yj_ = 0;
        double dx = (points.length / nBasePoints);

        for (int j = 1; j < nBasePoints; j++) {
            int k = (int)(j * dx);
            double xi = points[k].x, yi = points[k].y;

            // get the coordinates on the baseline
            double x_ = (yi + xi / m - c) / m_;
            double y_ = x_ * m + c;

            // in case the points are parallel vertical or horizontal
            if(sx == 0) {
                x_ = points[0].x;
                y_ = points[0].y + (sy / nBasePoints) * j;
            }
            if(sy == 0) {
                y_ = points[0].y;
                x_ = points[0].x + (sx / nBasePoints) * j;
            }

            // compute on which side of the line the contour point lies
            double side_ = Math.signum((xi - points[0].x) * sy - (yi - points[0].y) * sx);

            // if not on the same side:
            if(side != side_ && side != 0) {
                // calculate the zeropoint between the two
                double m0 = (yj - yi) / (xj - xi);
                double x0 = (yi - m0 * xi - c) / (m - m0);
                double y0 = x0 * m0 + (yi - m0 * xi) + c;

                // now add the area from xj_ to x0 and x0 to x_
                double d0 = Math.sqrt((x0 - xj_) * (x0 - xj_) + (y0 - yj_) * (y0 - yj_));
                area += side * d0 * h0 / 2;

                double h = Math.sqrt((y_ - yi) * (y_ - yi) + (x_ - xi) * (x_ - xi));
                d0 = Math.sqrt((x0 - x_) * (x0 - x_) + (y0 - y_) * (y0 - y_));
                area += side_ * d0 * h / 2;
            }
            else {
                // calculate length from point n curve to base point
                double h = Math.sqrt((y_ - yi) * (y_ - yi) + (x_ - xi) * (x_ - xi));
                double da = (h + h0) / 2 * dx;

                h0 = h;
                // add area according to side, that way a curve has more than a wobbly line
                area += da * side_;
            }

            side = side_;
            xj = xi;
            yj = yi;
            xj_ = x_;
            yj_ = y_;
        }

        // save the accurate area
        if(!Double.isNaN(area)) {
            // compare to area estimate. If new area is much bigger than estimate we got a
            // unwanted cluster that needs to be removed
            /*if(this.area * 1 < area)
                this.area = -1;
            else*/
                this.area = Math.abs(area);
        }
        else
            this.area = -1;
    }

    void calculateCenterPoint(){
        double xmean = 0, ymean = 0;
        for (int i = 0; i < points.length; i++) {
            xmean += points[i].x;
            ymean += points[i].y;
        }
        center = new Point(xmean / points.length, ymean / points.length);
    }

    void calculateClosure(){

        // base line from both endpoints
        double sy = points[points.length - 1].y - points[0].y;
        double sx = points[points.length - 1].x - points[0].x;
        double m = sy / sx;
        double c = points[0].y - m * points[0].x;

        // get the coordinates on the baseline
        double x_ = (center.y + center.x / m - c) / (m + 1 / m);
        double y_ = x_ * m + c;

        // line through centerpoint
        double m_ = -1 / m;
        double c_ = center.y - m_ * center.x;

        // find meeting point between centerline and contour
        for (int i = 0; i < points.length; i++) {
            double y = m_ * points[i].x + c_;

            // find a close point
            if(Math.abs(y - points[i].y) < 2){
                centerLinePoint = i;

                // now calculate the distance between center point and both other points
                double contourPoint = dist(points[centerLinePoint], center);
                double basePoint = dist(new Point(x_, y_), center);
                break;
            }
        }
    }

    public void calculateEndDirections() {
        int n = 8;

        // save vector (for debuging) and angle for merging
        endVectors = new Point[2];
        endDirections = new double[2];

        // one end
        double x = 0, y = 0;
        for (int i = (n < points.length - 1 ? n : points.length - 1); i > 0; i--) {
            x += points[i - 1].x - points[i].x;
            y += points[i - 1].y - points[i].y;
        }
        double len = Math.sqrt(x * x + y * y);
        x /= len;
        y /= len;
        endVectors[0] = new Point(x, y);
        // get the angle from vector
        endDirections[0] = (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;

        // other end
        x = 0; y = 0;
        for (int i = points.length - n; i > -1 && i < points.length - 1; i++) {
            x += points[i + 1].x - points[i].x;
            y += points[i + 1].y - points[i].y;
        }
        len = Math.sqrt(x * x + y * y);
        x /= len;
        y /= len;
        endVectors[1] = new Point(x, y);
        endDirections[1] = (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    private boolean angleInTolerance(double a1, double a2, double tolerance){
        a1 = (a1 + 180) % 360;
        return a2 - tolerance < a1 && a2 + tolerance > a1;
    }

    public boolean tryMerge(Contour item, double maxDistance, double mTolerance){
        // try out each endpoint combination
        for (int i = 0; i < 4; i++) {
            int ind1 = (i % 2 == 0 ? 0 : points.length - 1);
            int ind2 = (i / 2 == 0 ? 0 : item.points.length - 1);

            double d = dist(points[ind1], item.points[ind2]);
            // found 2 close contours
            if(d < maxDistance){
                // only merge if angle in tolerance
                if(endDirections != null && item.endDirections != null &&
                   angleInTolerance(endDirections[i % 2], item.endDirections[i / 2], mTolerance)) {
                    // merge with right order
                    ArrayList<Point> tmp = new ArrayList<>();
                    if(ind1 == 0) {
                        Collections.addAll(tmp, item.points);
                        if(ind2 == 0)
                            Collections.reverse(tmp);
                        Collections.addAll(tmp, points);
                    }else {
                        Collections.addAll(tmp, points);
                        if(ind2 == 0)
                            Collections.addAll(tmp, item.points);
                        else {
                            ArrayList<Point> tmp2 = new ArrayList<>();
                            Collections.addAll(tmp2, item.points);
                            Collections.reverse(tmp2);
                            tmp.addAll(tmp2);
                        }
                    }

                    // to mat and point array
                    MatOfPoint matOfPoint = new MatOfPoint();
                    matOfPoint.fromList(tmp);
                    data = matOfPoint;

                    points = tmp.toArray(new Point[tmp.size()]);

                    // recalculate endDirections
                    calculateEndDirections();

                    return true;
                }
            }
        }
        return false;
    }

    private double dist(Point p1, Point p2) {
        return Math.sqrt((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y));
    }

    public void drawMultiColored(Bitmap material)
    {
        int matWidth = material.getWidth();
        int matHeight = material.getHeight();

        int[] colors = new int[matWidth * matHeight];
        material.getPixels(colors, 0, matWidth, 0, 0, matWidth, matHeight);

        Point[] points = data.toArray();
        for (int i = 0; i < points.length; i++) {
            colors[matWidth * (int)points[i].y + (int)points[i].x] = Color.rgb((int)((points.length - i) * (255f / points.length)), (int)(i * (255f / points.length)), 0);
        }

        //Log.d("ENDDIR", endDirections[0] + ", " + endDirections[1]);
        //Log.d("ENDDIR", endVectors[0] + ", " + endVectors[1]);
        //Log.d("ENDDIR", points[0] + ", " + points[points.length - 1]);
        material.setPixels(colors, 0, matWidth, 0,0, matWidth, matHeight);
    }

    public void draw(Bitmap material, int color)
    {
        int matWidth = material.getWidth();
        int matHeight = material.getHeight();

        int[] colors = new int[matWidth * matHeight];
        material.getPixels(colors, 0, matWidth, 0, 0, matWidth, matHeight);

        for (int i = 0; i < points.length; i++) {
            if(i == 0 || i == points.length - 1)
                colors[matWidth * (int)points[i].y + (int)points[i].x] = Color.WHITE;
            else if(i == centerLinePoint)
                colors[matWidth * (int)points[i].y + (int)points[i].x] = Color.BLUE;
            else
                colors[matWidth * (int)points[i].y + (int)points[i].x] = color;
        }

        // draw endDirections
        /*for (int i = 0; i < 5; i++) {
            int x = (int)points[0].x + i;
            int y = (int)(points[0].y + endDirections[0] * i);
            colors[matWidth * y + x] = Color.YELLOW;
        }
        for (int i = 0; i < 5; i++) {
            int x = (int)points[points.length - 1].x + i;
            int y = (int)(points[points.length - 1].y + endDirections[1] * i);
            colors[matWidth * y + x] = Color.YELLOW;
        }*/

        /*if(center != null)
            for (int i = 0; i < 5; i++) {
                colors[matWidth * (int) center.y + (int) center.x + i - 2] = color;
                colors[matWidth * ((int) center.y + i - 2) + (int) center.x] = color;
            }*/

        material.setPixels(colors, 0, matWidth, 0,0, matWidth, matHeight);
    }

    @Override
    public int compareTo(@NonNull Object o) {
        return Double.compare(area, ((Contour)o).area) * -1;
    }


    public static ArrayList<Contour> create(ArrayList<MatOfPoint> contours)
    {
        ArrayList<Contour> result = new ArrayList<>(contours.size());
        for (MatOfPoint m: contours) {
            result.add(new Contour(m));
        }
        return result;
    }
}
