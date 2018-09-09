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
import com.hhu.yannick.coinclassificator.AsyncProcessor.Processor.FeatureGP;
import com.hhu.yannick.coinclassificator.SQLite.CoinData;
import com.hhu.yannick.coinclassificator.SQLite.DatabaseManager;

import org.opencv.core.RotatedRect;

import java.text.DecimalFormat;
import java.util.TreeMap;


public class FeatureFragment extends Fragment implements AdapterView.OnItemSelectedListener, OnTaskCompleted {

    // Views
    private Spinner detectorSpinner;
    private Spinner matcherSpinner;
    private TextView countryText;
    private TextView valueText;
    private TextView accuracyText;
    private TextView informationText;
    private ImageView coinView;
    private ImageView flagView;
    private ProgressBar progressBar;

    private DatabaseManager databaseManager;
    private Bitmap bitmap;
    private AsyncGraphicsProcessor main;
    private boolean loaded;
    private FeatureGP.DetectorType detectorType = FeatureGP.DetectorType.SIFT;
    private FeatureGP.MatchMethode matcherType = FeatureGP.MatchMethode.LOWE_RATIO_TEST;

    // cached variables
    private RotatedRect ellipse;
    private boolean moreResults;
    private boolean drawExtendedFeatures;

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
        valueText = view.findViewById(R.id.valueText);
        accuracyText = view.findViewById(R.id.accuracyText);
        informationText = view.findViewById(R.id.informationText);
        coinView = view.findViewById(R.id.coinView);
        flagView = view.findViewById(R.id.flagView);
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
                        getActivity(),
                        drawExtendedFeatures,
                        this);
            } else {
                main = new FeatureAGP(bitmap, databaseManager, getContext(), ellipse,
                        detectorSpinner.getSelectedItem().toString(),
                        matcherSpinner.getSelectedItem().toString(),
                        drawExtendedFeatures,
                        this);
            }
            // run the async task
            main.execute();
        }
    }

    public void drawExtendedFeatures(){
        drawExtendedFeatures = !drawExtendedFeatures;
        startExecution();
    }

    public void showMoreResults(){
        // show 4 more results in the information textbox
        if(main != null && main.result != null && main.result.get("results") != null) {
            moreResults = true;
            TreeMap<Double, CoinData> result = (TreeMap<Double, CoinData>) main.result.get("results");
            DecimalFormat formatter = new DecimalFormat("#.00");
            StringBuilder sb = new StringBuilder();
            int count = 0;
            if (matcherType == FeatureGP.MatchMethode.SMALLEST_DISTANCE)
                for (Double key : result.keySet()) {
                    count++;
                    if (count == 1)
                        continue;
                    else if (count > 8)
                        break;

                    CoinData d = result.get(key);
                    sb.append(d.country);
                    sb.append(" ");
                    sb.append(CoinData.valueToString(d.value));
                    sb.append("     Score: ");
                    sb.append(formatter.format(key));
                    sb.append("\n");
                }
            else
                for (Double key : result.descendingKeySet()) {
                    count++;
                    if (count == 1)
                        continue;
                    else if (count > 7)
                        break;

                    CoinData d = result.get(key);
                    sb.append(d.country);
                    sb.append(" ");
                    sb.append(CoinData.valueToString(d.value));
                    sb.append("     Score: ");
                    sb.append(formatter.format(key * 100));
                    sb.append("\n");
                }
            informationText.setText(sb.toString());
        }
    }


    @Override
    public void onTaskCompleted() {
        // when task finished display information
        if (main.result != null) {
            coinView.setImageBitmap((Bitmap) main.result.get("bitmap"));
            if (main.result.containsKey("coin")) {
                String country = ((CoinData) main.result.get("coin")).country;
                String value = CoinData.valueToString(((CoinData) main.result.get("coin")).value);
                countryText.setText(country);
                valueText.setText(value);
                accuracyText.setText((String)main.result.get("accuracy"));

                if(moreResults)
                    showMoreResults();

                // get the corresponding flag
                if(main.result.containsKey("flag"))
                    flagView.setImageBitmap((Bitmap) main.result.get("flag"));
                informationText.setText((String)main.result.get("information"));
            }
            // main.result.get("ellipse")
        }
        progressBar.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        switch (parent.getId()){
            case R.id.descriptionSpinner:
                FeatureGP.DetectorType dt = EllipseAndFeatureAGP.getEnum(FeatureGP.DetectorType.class, detectorSpinner.getSelectedItem().toString());
                if(detectorType != dt) {
                    startExecution();
                    detectorType = dt;
                }
                break;
            case R.id.matcherSpinner:
                String mm = matcherSpinner.getSelectedItem().toString();
                FeatureGP.MatchMethode mme;

                if(mm.equals("Lowe Ratio Test"))
                    mme = FeatureGP.MatchMethode.LOWE_RATIO_TEST;
                else if(mm.equals("Smallest Total Distance"))
                    mme = FeatureGP.MatchMethode.SMALLEST_DISTANCE;
                else
                    mme = FeatureGP.MatchMethode.DISTANCE_THRESHOLD;

                // only restart if a different method was selected
                if(matcherType != mme) {
                    startExecution();
                    matcherType = mme;
                }
                break;
        }
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
