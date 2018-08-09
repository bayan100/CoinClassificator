package com.hhu.yannick.coinclassificator;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.hhu.yannick.coinclassificator.AsyncProcessor.AsyncGraphicsProcessor;
import com.hhu.yannick.coinclassificator.AsyncProcessor.EllipseAndFeatureAGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.FeatureAGP;
import com.hhu.yannick.coinclassificator.AsyncProcessor.OnTaskCompleted;
import com.hhu.yannick.coinclassificator.SQLite.CoinData;
import com.hhu.yannick.coinclassificator.SQLite.DatabaseManager;

import org.opencv.core.RotatedRect;


public class FeatureFragment extends Fragment implements AdapterView.OnItemSelectedListener, OnTaskCompleted {

    // Views
    private Spinner detectorSpinner;
    private Spinner matcherSpinner;
    private TextView countryText;
    private TextView accuracyText;
    private ImageView coinView;
    private ProgressBar progressBar;

    private DatabaseManager databaseManager;
    private Bitmap bitmap;
    private AsyncGraphicsProcessor main;
    private boolean loaded;

    // cached variables
    private RotatedRect ellipse;

    public FeatureFragment() {
        // Required empty public constructor
    }

    public static FeatureFragment newInstance() {
        FeatureFragment fragment = new FeatureFragment();
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
        return inflater.inflate(R.layout.fragment_feature, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // findViewById must happen after onCreate and onViewCreate so calling it here

        // get the spinner and fill with the options
        detectorSpinner = (Spinner) view.findViewById(R.id.descriptionSpinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getContext(),
                R.array.descriptor_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // set spinner dropdown items
        detectorSpinner.setAdapter(adapter);
        detectorSpinner.setOnItemSelectedListener(this);

        // get the spinner and fill with the options
        matcherSpinner = (Spinner) view.findViewById(R.id.matcherSpinner);
        adapter = ArrayAdapter.createFromResource(getContext(),
                R.array.matcher_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // set spinner dropdown items
        matcherSpinner.setAdapter(adapter);
        matcherSpinner.setOnItemSelectedListener(this);

        // gather Views
        countryText = view.findViewById(R.id.countryText);
        accuracyText = view.findViewById(R.id.accuracyText);
        coinView = view.findViewById(R.id.coinView);
        progressBar = view.findViewById(R.id.pBar);
    }

    public void putLoadedData(DatabaseManager databaseManager, Bitmap bitmap) {
        this.databaseManager = databaseManager;
        this.bitmap = bitmap;
        loaded = true;
    }

    public void startExecution(){
        // only start if loading was done
        if(loaded) {
            progressBar.setVisibility(View.VISIBLE);

            // check if ellipse is already cached
            if (ellipse == null) {
                main = new EllipseAndFeatureAGP(bitmap, databaseManager, getContext(),
                        detectorSpinner.getSelectedItem().toString(),
                        matcherSpinner.getSelectedItem().toString(),
                        this);
            } else {
                main = new FeatureAGP(bitmap, databaseManager, getContext(), ellipse,
                        detectorSpinner.getSelectedItem().toString(),
                        matcherSpinner.getSelectedItem().toString(),
                        this);
            }
            // run the async task
            main.execute();
        }
    }

    @Override
    public void onTaskCompleted() {
        // when task finished display information


        coinView.setImageBitmap((Bitmap)main.result.get("bitmap"));
        // DEBUG
        if(main.result.containsKey("coin")) {
            String country = ((CoinData)main.result.get("coin")).country;
            countryText.setText(country);
        }
        // main.result.get("ellipse")

        progressBar.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            Log.d("FEATURE", "vis to user");

            // now execute the main work
            startExecution();
        }
        else {
            Log.d("FEATURE", "not vis to user");
        }
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
