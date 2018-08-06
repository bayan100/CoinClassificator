package com.hhu.yannick.coinclassificator.AsyncProcessor.Processor;

import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;

public class EllipseGP extends GraphicsProcessor{
    private Type type;
    public enum Type{
        FIND,
        DRAW
    }

    public EllipseGP(Type type){
        this.type = type;
        task = type.toString() + "_Ellipse";

        parameter.put("centerDifference", 5);
    }

    @Override
    protected Status executeProcess() {
        switch (type){
            case FIND: return find();
            case DRAW: return draw();
        }
        return Status.FAILED;
    }

    private Status find() {
        ArrayList<Contour> contours = null;
        if(data.containsKey("contours"))
            contours = (ArrayList<Contour>) data.get("contours");
        else
            return Status.FAILED;

        ArrayList<RotatedRect> ellipses = new ArrayList<>(contours.size());

        for (int i = 0; i < contours.size(); i++) {
            MatOfPoint2f dst = new MatOfPoint2f();
            contours.get(i).data.convertTo(dst, CvType.CV_32FC2);

            ellipses.add(Imgproc.fitEllipse(dst));
        }

        // check if the first Ellipses have similar center (indicating one is the outer and the other the inner ring
        if (ellipses.size() > 0) {
            RotatedRect e0 = ellipses.get(0);
            int centerDifference = getInt("centerDifference");

            for (int i = 1; i < ellipses.size(); i++) {
                RotatedRect e1 = ellipses.get(i);
                if (Math.sqrt((e1.center.x - e0.center.x) * (e1.center.x - e0.center.x) + (e1.center.y - e0.center.y) * (e1.center.y - e0.center.y)) < centerDifference) {
                    // search for an ellipse that is bigger by a good margin than the first
                    if (e0.size.width * e0.size.height < 0.9 * e1.size.width * e1.size.height) {
                        ellipses.set(0, e1);
                        ellipses.set(i, e0);
                        break;
                    }
                }
            }
        }

        data.put("ellipses", ellipses);
        return Status.PASSED;
    }

    private Status draw() {
        Mat image;
        ArrayList<RotatedRect> ellipses = null;
        if(data.containsKey("ellipses") && data.containsKey("mat")){
            image = (Mat)data.get("mat");
            ellipses = (ArrayList<RotatedRect>)data.get("ellipses");
        }
        else
            return Status.FAILED;

        for (int i = 0; i < 1 && i < ellipses.size(); i++) {
            Imgproc.ellipse(image, ellipses.get(i), new Scalar(255, 0, 0), 2);
        }

        data.put("mat", image);
        data.put("bitmap", toBitmap(image));

        return Status.PASSED;

    }
}
