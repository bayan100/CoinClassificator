package com.hhu.yannick.coinclassificator.AsyncProcessor;

import android.content.Context;
import android.graphics.Bitmap;

import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.BlurGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.ContourGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.EllipseGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.FeatureGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.GraphicsProcessor;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.GrayScaleGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.ResizeGP;
import com.hhu.yannick.coinclassificator.SQLite.DatabaseManager;

import org.opencv.core.RotatedRect;

import java.util.ArrayList;

public class EllipseAndFeatureAGP extends AsyncGraphicsProcessor {
    public EllipseAndFeatureAGP(Bitmap bitmap, DatabaseManager databaseManager, Context context, String detector, String matcher, OnTaskCompleted listener)
    {
        super(listener);

        ArrayList<GraphicsProcessor> processors = new ArrayList<>();
        ResizeGP gp = new ResizeGP(ResizeGP.Type.LINEAR);
        gp.setImage(bitmap.copy(bitmap.getConfig(), true));
        processors.add(gp);
        processors.add(new BlurGP(BlurGP.Type.MEDIAN));
        processors.add(new ContourGP(ContourGP.Type.CANNYEDGE));
        processors.add(new ContourGP(ContourGP.Type.FIND));
        processors.add(new ContourGP(ContourGP.Type.SPLIT));
        processors.add(new ContourGP(ContourGP.Type.FILTER));
        processors.add(new EllipseGP(EllipseGP.Type.FIND));

        GrayScaleGP gsp = new GrayScaleGP();
        gsp.setImage(bitmap.copy(bitmap.getConfig(), true));
        processors.add(gsp);
        processors.add(new ResizeGP(ResizeGP.Type.RESIZE_ELLIPSE));
        processors.add(new ResizeGP(ResizeGP.Type.CROP));
        FeatureGP fp = new FeatureGP(
                getEnum(FeatureGP.DetectorType.class, detector),
                getEnum(matcher),
                databaseManager, context);
        /*FeatureGP fp = new FeatureGP(
                FeatureGP.DetectorType.ORB,
                getEnum(FeatureGP.MatcherType.class, matcher),
                databaseManager, context);*/
        fp.setParameter("nFeaturesMax", 256);
        fp.setParameter("nFeaturesMin", 128);
        processors.add(fp);

        task = processors;
    }

    public static <T extends Enum<T>> T getEnum(Class<T> c, String string) {
        if( c != null && string != null ) {
            try {
                return Enum.valueOf(c, string.trim().replaceAll("\\s","").toUpperCase());
            } catch(IllegalArgumentException ex) {
                ex.printStackTrace();
            }
        }
        return null;
    }

    private static FeatureGP.MatchMethode getEnum(String s){
        if(s.equals("Lowe Ratio Test"))
            return FeatureGP.MatchMethode.LOWE_RATIO_TEST;
        else if(s.equals("Smallest Total Distance"))
            return FeatureGP.MatchMethode.SMALLEST_DISTANCE;
        else
            return FeatureGP.MatchMethode.DISTANCE_THRESHOLD;
    }
}
