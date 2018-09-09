package com.hhu.yannick.coinclassificator.Tensorflow;
import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.GraphicsProcessor;
import com.hhu.yannick.coinclassificator.R;
import com.hhu.yannick.coinclassificator.SQLite.CoinData;
import com.hhu.yannick.coinclassificator.SQLite.DatabaseManager;
import com.hhu.yannick.coinclassificator.SQLite.MatSerializer;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class TensorflowProcessor extends GraphicsProcessor {
    private final String MODEL_PATH = "tensorflow/optimized_graph.lite";
    private final String LABEL_PATH = "tensorflow/retrained_labels.txt";
    private final String MODEL_PATH_MERGED = "tensorflow/optimized_graph_merged.lite";
    private final String LABEL_PATH_MERGED = "tensorflow/retrained_labels_merged.txt";
    private boolean loadMergedModel = false;

    private Interpreter tflite;
    private List<String> labelList;
    private float[][] labelProbArray, filterLabelProbArray;

    private ByteBuffer imgData;
    private int[] intValues;

    private Activity activity;
    private DatabaseManager databaseManager;

    private Task type;
    public enum Task{
        CLASSIFY
    }

    public TensorflowProcessor(Task task, Activity activity, DatabaseManager dbm){
        parameter.put("dimBatchSize", 1);
        parameter.put("dimPixelSize", 3);

        parameter.put("tensorImageWidth", 224);
        parameter.put("tensorImageHeight", 224);

        parameter.put("tensorImageMean", 128);
        parameter.put("tensorImageSTD", 128);

        parameter.put("filterStages", 3);
        parameter.put("filterFactor", 0.4f);

        type = task;
        this.task = "Tensorflow_" +  task.toString();
        this.activity = activity;
        this.databaseManager = dbm;
    }

    @Override
    protected Status executeProcess() {
        // start Timer
        long starttime = System.nanoTime();

        switch (type) {
            case CLASSIFY:
                try {
                    loadInterpreter();
                    //prepareImage();
                    classifyImage();
                }
                catch (IOException e){
                    e.printStackTrace();
                }
                break;
        }

        // timer stop
        Log.d("TIMER", "Task " + task + " completed in: " + ((System.nanoTime() - starttime) / 1000000) + " ms");
        return Status.PASSED;
    }


    private void loadInterpreter() throws IOException {
        // load the TensorFlow Interpreter (kinda like Session in python)
        tflite = new Interpreter(loadModelFile(activity));

        labelList = loadLabelList(activity);
        // the image as ByteBuffer
        imgData = ByteBuffer.allocateDirect((int)(4 *
                getInt("dimBatchSize") *
                getInt("tensorImageWidth") *
                getInt("tensorImageHeight") *
                getInt("dimPixelSize")));
        imgData.order(ByteOrder.nativeOrder());
        labelProbArray = new float[1][labelList.size()];
        filterLabelProbArray = new float[getInt("filterStages")][labelList.size()];
    }

    private void classifyImage(){
        Bitmap bitmap = (Bitmap)data.get("bitmap");

        // prepare the image
        convertBitmapToByteBuffer(bitmap);

        // classify it!
        tflite.run(imgData, labelProbArray);

        // smooth the results
        applyFilter();

        // input all into a sorted list
        TreeMap<Double, CoinData> result = new TreeMap<>();
        for (int i = 0; i < labelList.size(); i++) {
            if(loadMergedModel) {
                String s = labelList.get(i);
                String country = Character.toUpperCase(s.charAt(0)) + s.substring(1, s.length());
                result.put((double) labelProbArray[0][i], new CoinData(-1, 0, country));
            }
            else {
                String[] s = labelList.get(i).split(" ");
                String country = Character.toUpperCase(s[0].charAt(0)) + s[0].substring(1, s[0].length());
                result.put((double) labelProbArray[0][i], new CoinData(Integer.parseInt(s[1]), 0, country));
            }
        }
        // pick the maximum
        CoinData cd = result.get(result.lastKey());

        data.put("coin", cd);
        data.put("accuracy", result.lastKey());
        data.put("results", result);
        loadFlag(cd.country);
        // load information
        data.put("information", databaseManager.loadInformation(cd));
    }

    public void changeModel(boolean loadMergedModel){
        this.loadMergedModel = loadMergedModel;
    }

    private MappedByteBuffer loadModelFile(Activity activity) throws IOException {
        // Memory-map the model file in Assets
        AssetFileDescriptor fileDescriptor;
        if(loadMergedModel)
            fileDescriptor = activity.getAssets().openFd(MODEL_PATH_MERGED);
        else
            fileDescriptor = activity.getAssets().openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private List<String> loadLabelList(Activity activity) throws IOException {
        // read the labels list from the assets
        List<String> labelList = new ArrayList<String>();
        BufferedReader reader;
        if(loadMergedModel)
            reader = new BufferedReader(new InputStreamReader(activity.getAssets().open(LABEL_PATH_MERGED)));
        else
            reader = new BufferedReader(new InputStreamReader(activity.getAssets().open(LABEL_PATH)));
        String line;
        while ((line = reader.readLine()) != null) {
            labelList.add(line);
        }
        reader.close();
        return labelList;
    }

    private void convertBitmapToByteBuffer(Bitmap bitmap) {
        int imageWidth = getInt("tensorImageWidth");
        int imageHeight = getInt("tensorImageHeight");
        float imageMean = getInt("tensorImageMean");
        float imageSTD = getInt("tensorImageSTD");

        intValues = new int[imageWidth * imageHeight];
        imgData.rewind();
        //Log.d("TENSOR", "tiw: " + imageWidth + ", tih: "+ imageHeight + ", bw: " + bitmap.getWidth() + ", bh: " + bitmap.getHeight());
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        // Convert the image to floating point.
        int pixel = 0;
        for (int i = 0; i < imageWidth; ++i) {
            for (int j = 0; j < imageHeight; ++j) {
                final int val = intValues[pixel++];
                imgData.putFloat((((val >> 16) & 0xFF)-imageMean)/imageSTD);
                imgData.putFloat((((val >> 8) & 0xFF)-imageMean)/imageSTD);
                imgData.putFloat((((val) & 0xFF)-imageMean)/imageSTD);
            }
        }
    }

    private void applyFilter(){
        int num_labels =  labelList.size();
        int filterStages = getInt("filterStages");
        float filterFactor = getFloat("filterFactor");

        // Low pass filter `labelProbArray` into the first stage of the filter.
        for(int j = 0; j < num_labels; ++j){
            filterLabelProbArray[0][j] += filterFactor * (labelProbArray[0][j] - filterLabelProbArray[0][j]);
        }
        // Low pass filter each stage into the next.
        for (int i = 1; i < filterStages; ++i){
            for(int j = 0; j < num_labels; ++j){
                filterLabelProbArray[i][j] += filterFactor * (filterLabelProbArray[i - 1][j] - filterLabelProbArray[i][j]);
            }
        }

        // Copy the last stage filter output back to `labelProbArray`.
        for(int j = 0; j < num_labels; ++j){
            labelProbArray[0][j] = filterLabelProbArray[filterStages - 1][j];
        }
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
