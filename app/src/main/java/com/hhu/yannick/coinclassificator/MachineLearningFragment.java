package com.hhu.yannick.coinclassificator;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
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
import com.hhu.yannick.coinclassificator.SQLite.DatabaseManager;

import org.opencv.core.RotatedRect;

public class MachineLearningFragment extends Fragment implements OnTaskCompleted {
    private boolean loaded;
    private DatabaseManager databaseManager;
    private Bitmap bitmap;
    private Activity activity;

    // Views
    private TextView countryText;
    private TextView accuracyText;
    private ImageView coinView;
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
        accuracyText = view.findViewById(R.id.accuracyText);
        coinView = view.findViewById(R.id.coinView);
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


    @Override
    public void onTaskCompleted() {

        coinView.setImageBitmap((Bitmap)main.result.get("bitmap"));
        countryText.setText((String)main.result.get("country"));

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
