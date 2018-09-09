package com.hhu.yannick.coinclassificator;

import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.support.design.widget.TabLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import android.widget.TextView;
import android.widget.Toast;

import com.hhu.yannick.coinclassificator.AsyncProcessor.DatabaseLoader;
import com.hhu.yannick.coinclassificator.AsyncProcessor.OnTaskCompleted;
import com.hhu.yannick.coinclassificator.SQLite.DatabaseManager;

/**
 * The android activity for the image classification. It embeds two fragments, one for
 * Feature-classification and the other for CNN-based classification.
 */
public class ClassifyActivity extends AppCompatActivity implements OnTaskCompleted {

    private DatabaseLoader databaseLoader;
    private DatabaseManager databaseManager;
    private ProgressDialog progress;

    private MachineLearningFragment machineLearningFragment;
    private FeatureFragment featureFragment;

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_classify);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // create a progress dialog to load the database
        progress = new ProgressDialog(this);
        progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progress.setMessage("Loading Database");
        progress.show();

        // now load the database and bitmap async
        String file = getIntent().getStringExtra("File");
        databaseManager = new DatabaseManager(getApplicationContext());
        databaseLoader = new DatabaseLoader(this, databaseManager, file, getApplicationContext());
        databaseLoader.execute();

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);

        mViewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
        tabLayout.addOnTabSelectedListener(new TabLayout.ViewPagerOnTabSelectedListener(mViewPager));
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_classify, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        // show more results selected
        if (id == R.id.action_results) {
            if(machineLearningFragment != null)
                machineLearningFragment.showMoreResults();
            if(featureFragment != null)
                featureFragment.showMoreResults();

            return true;
        }
        else if(id == R.id.action_dataset){
            if(machineLearningFragment != null)
                machineLearningFragment.loadDifferentGraph();
        }
        else if(id == R.id.action_features){
            if(featureFragment != null)
                featureFragment.drawExtendedFeatures();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        // close database connection
        databaseManager.close();

        super.onDestroy();
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // return the right fragment
            switch (position){
                case 0: machineLearningFragment = MachineLearningFragment.newInstance();
                    return machineLearningFragment;
                case 1: featureFragment = FeatureFragment.newInstance();
                    return featureFragment;
            }
            return null;
        }

        @Override
        public int getCount() {
            // Show 2 total pages.
            return 2;
        }
    }

    /**
     * This method gets called once a started AsyncGraphicsProcessor with this class as
     * reference finishes execution.
     *
     * @see     com.hhu.yannick.coinclassificator.AsyncProcessor.AsyncGraphicsProcessor
     */
    @Override
    public void onTaskCompleted() {
        // finished loading the database
        progress.cancel();

        Log.d("EXE", "initialLoad");

        // start the first task execution
        machineLearningFragment.putLoadedData(databaseManager, databaseLoader.bitmap, this);
        featureFragment.putLoadedData(databaseManager, databaseLoader.bitmap);

        // first task so execute at first
        machineLearningFragment.startExecution();
    }
}
