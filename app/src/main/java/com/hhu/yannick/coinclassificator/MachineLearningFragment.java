package com.hhu.yannick.coinclassificator;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.hhu.yannick.coinclassificator.SQLite.DatabaseManager;

public class MachineLearningFragment extends Fragment {


    private boolean loaded;
    private DatabaseManager databaseManager;
    private Bitmap bitmap;

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

    public void putLoadedData(DatabaseManager databaseManager, Bitmap bitmap) {
        this.databaseManager = databaseManager;
        this.bitmap = bitmap;
        loaded = true;
    }

    public void startExecution(){
        Log.d("EXE", "starting machine");
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
