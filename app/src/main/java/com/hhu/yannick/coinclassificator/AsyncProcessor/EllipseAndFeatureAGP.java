package com.hhu.yannick.coinclassificator.AsyncProcessor;

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
    public EllipseAndFeatureAGP(Bitmap bitmap, DatabaseManager databaseManager, String detector, String matcher, OnTaskCompleted listener)
    {
        super(listener);

        ArrayList<GraphicsProcessor> processors = new ArrayList<>();
        ResizeGP gp = new ResizeGP(ResizeGP.Type.LINEAR);
        gp.set("bitmap", bitmap.copy(bitmap.getConfig(), true));
        processors.add(gp);
        processors.add(new BlurGP(BlurGP.Type.MEDIAN));
        processors.add(new ContourGP(ContourGP.Type.CANNYEDGE));
        processors.add(new ContourGP(ContourGP.Type.FIND));
        processors.add(new ContourGP(ContourGP.Type.SPLIT));
        processors.add(new ContourGP(ContourGP.Type.FILTER));
        processors.add(new EllipseGP(EllipseGP.Type.FIND));

        GrayScaleGP gsp = new GrayScaleGP();
        gsp.set("bitmap", bitmap.copy(bitmap.getConfig(), true));
        gsp.set("mat", gsp.toMat((Bitmap)gsp.getData().get("bitmap")));
        processors.add(gsp);
        processors.add(new ResizeGP(ResizeGP.Type.RESIZE_ELLIPSE));
        FeatureGP fp = new FeatureGP(
                getEnum(FeatureGP.DetectorType.class, detector),
                getEnum(FeatureGP.MatcherType.class, matcher),
                databaseManager);
        processors.add(fp);

        task = processors;
    }

    private static <T extends Enum<T>> T getEnum(Class<T> c, String string) {
        if( c != null && string != null ) {
            try {
                return Enum.valueOf(c, string.trim().replaceAll("\\s","").toUpperCase());
            } catch(IllegalArgumentException ex) {
                ex.printStackTrace();
            }
        }
        return null;
    }
}
