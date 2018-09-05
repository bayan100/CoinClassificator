package com.hhu.yannick.coinclassificator;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.hhu.yannick.coinclassificator.AsyncProcessor.AsyncGraphicsProcessor;
import com.hhu.yannick.coinclassificator.AsyncProcessor.EllipseAndTensorflowAGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.OnTaskCompleted;
import com.hhu.yannick.coinclassificator.AsyncProcessor.TensorflowAGP;
import com.hhu.yannick.coinclassificator.SQLite.CoinData;
import com.hhu.yannick.coinclassificator.SQLite.DatabaseManager;

import org.opencv.core.RotatedRect;

import java.text.DecimalFormat;
import java.util.TreeMap;

public class MachineLearningFragment extends Fragment implements OnTaskCompleted {
    private boolean loaded;
    private DatabaseManager databaseManager;
    private Bitmap bitmap;
    private Activity activity;

    // Views
    private TextView countryText;
    private TextView valueText;
    private TextView accuracyText;
    private TextView informationText;
    private ImageView coinView;
    private ImageView flagView;
    private ProgressBar progressBar;

    private RotatedRect ellipse;
    private AsyncGraphicsProcessor main;

    public MachineLearningFragment() {
        // Required empty public constructor
    }

    public static MachineLearningFragment newInstance() {
        MachineLearningFragment fragment = new MachineLearningFragment();
        Bundle args = new Bundle();
        //args.putString(ARG_PARAM1, param1);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            //mParam1 = getArguments().getString(ARG_PARAM1);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_machine_learning, container, false);
    }

    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // findViewById must happen after onCreate and onViewCreate so calling it here
        countryText = view.findViewById(R.id.countryText);
        valueText = view.findViewById(R.id.valueText);
        accuracyText = view.findViewById(R.id.accuracyText);
        informationText = view.findViewById(R.id.informationText);
        coinView = view.findViewById(R.id.coinView);
        flagView = view.findViewById(R.id.flagView);
        progressBar = view.findViewById(R.id.pBar);
    }

    public void putLoadedData(DatabaseManager databaseManager, Bitmap bitmap, Activity activity) {
        this.databaseManager = databaseManager;
        this.bitmap = bitmap;
        this.activity = activity;
        loaded = true;
    }

    public void startExecution(){
        // only start if loading was done
        if(loaded) {
            progressBar.setVisibility(View.VISIBLE);

            if(ellipse == null){
                main = new EllipseAndTensorflowAGP(bitmap, databaseManager, activity, this);
            }
            else {
                main = new TensorflowAGP(bitmap, databaseManager, activity, this);
            }

            // run
            main.execute();
        }
    }

    public void showMoreResults(){
        // show 4 more results in the information textbox
        TreeMap<Double, CoinData> result = (TreeMap<Double, CoinData>)main.result.get("results");
        DecimalFormat formatter = new DecimalFormat("#.00");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Double key : result.descendingKeySet()){
            count++;
            if(count == 1)
                continue;
            else if(count > 10)
                break;

            CoinData d = result.get(key);
            sb.append(d.country);
            sb.append(" ");
            sb.append(CoinData.valueToString(d.value));
            sb.append("       ");
            sb.append(formatter.format(key * 100));
            sb.append("%\n");
        }
        informationText.setText(sb.toString());
    }

    @Override
    public void onTaskCompleted() {

        coinView.setImageBitmap((Bitmap)main.result.get("bitmap"));
        if (main.result.containsKey("coin")) {
            String country = ((CoinData) main.result.get("coin")).country;
            country = Character.toUpperCase(country.charAt(0)) + country.substring(1, country.length());
            String value = CoinData.valueToString(((CoinData) main.result.get("coin")).value);
            float accuracy = (Float)main.result.get("accuracy");
            countryText.setText(country);
            valueText.setText(value);
            DecimalFormat formatter = new DecimalFormat("#.00");
            accuracyText.setText(formatter.format(accuracy * 100) + "%");
            // set accuracy-string color
            if(accuracy >= 0.10)
                accuracyText.setTextColor(Color.argb(255, 0, 226,26));
            else if(accuracy >= 0.03)
                accuracyText.setTextColor(Color.argb(255, 190, 255, 0));
            else if(accuracy >= 0.01)
                accuracyText.setTextColor(Color.argb(255,255, 216,0));
            else
                accuracyText.setTextColor(Color.argb(255, 255, 0, 0));

            // get the corresponding flag
            if(main.result.containsKey("flag"))
                flagView.setImageBitmap((Bitmap) main.result.get("flag"));
        }

        progressBar.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

}
