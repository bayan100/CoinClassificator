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
import com.hhu.yannick.coinclassificator.SQLite.FeatureData;

import org.opencv.core.RotatedRect;

import java.util.ArrayList;

public class FeatureAGP extends AsyncGraphicsProcessor {
    public FeatureAGP(Bitmap bitmap, DatabaseManager databaseManager, Context context, RotatedRect ellipse, String detector, String matcher, OnTaskCompleted listener)
    {
        super(listener);

        ArrayList<GraphicsProcessor> processors = new ArrayList<>();
        GrayScaleGP gp = new GrayScaleGP();
        gp.setImage(bitmap.copy(bitmap.getConfig(), true));
        ArrayList<RotatedRect> ellipses = new ArrayList<>();
        ellipses.add(ellipse);
        gp.set("ellipses", ellipses);
        processors.add(gp);

        FeatureGP fp = new FeatureGP(
                getEnum(FeatureGP.DetectorType.class, detector),
                getEnum(FeatureGP.MatcherType.class, matcher),
                databaseManager, context);
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
