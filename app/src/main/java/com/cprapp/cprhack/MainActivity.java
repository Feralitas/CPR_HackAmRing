package com.cprapp.cprhack;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;


public class MainActivity extends AppCompatActivity implements SensorEventListener, View.OnClickListener {
    private SensorManager mSensorManager;
    private Sensor accelerometerSensor;
    private String saveString;
    private boolean isRecording;

    private float[][] accelometer;
    private float[][] accelZAVG;
    private int numberOfValues;
    private int milisecondsRecorded;
    private float[] lastMinValue; // 0 - time 1 - value
    private float[] LastMaxValue; // 0 - time 1 - value
    float[] lastAmplitudes;
    long lastReadInMS;
    int timeOfFrameInMS;
    int currentAmplitude;
    int numberOfValuesAVG;
    int bpm;
    int amplitudeInMM;
    private int ABTASTFREQ = 160; // Delta time between 2 abfragen in ms. (time of averaging)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // creation of list 1

        accelometer = new float[2][300];
        accelZAVG = new float[2][3000];
        lastAmplitudes = new float[3000];
        currentAmplitude = 0;
        numberOfValues = 0;
        numberOfValuesAVG = 0;
        timeOfFrameInMS = 0;
        isRecording = false;
        Deque<Float> stack;
        lastReadInMS= System.currentTimeMillis();
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        accelerometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        //mSensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);

        final TextView helloTextView = (TextView) findViewById(R.id.t_debug);

        helloTextView.setText("Test1");

        // create botton listener
        Button b_startRec = (Button) findViewById(R.id.b_startRec);
        Button b_StopAndSave = (Button) findViewById(R.id.b_StopAndSave);
        b_startRec.setOnClickListener(this);
        b_StopAndSave.setOnClickListener(this);
        boolean something  = isExternalStorageWritable();


        bpm=0;
        amplitudeInMM=0;

    }
    private void zeroArray()
    {
        for (int i = 0 ; i < 2 ; i++){
            for(int j = 0; j < 300; j++) {
                accelometer[i][j] = 0.0f;
            }}
    }

    private void slidingAveraging()
    {
        float sum =0;
        for(int i = 0; i < numberOfValues; i++)
        {
            sum  = sum + accelometer[1][i];
        }
        if(numberOfValues != 0)
        accelZAVG[1][numberOfValuesAVG] = sum / numberOfValues;
        else
            accelZAVG[1][numberOfValuesAVG] = 0;
        accelZAVG[0][numberOfValuesAVG] = numberOfValuesAVG * ABTASTFREQ;
        numberOfValuesAVG++;
        if(numberOfValuesAVG > 2999)
            numberOfValuesAVG = 0;

        if(isRecording)
        {
                 long time= System.currentTimeMillis();
                 saveString += "" +  accelZAVG[0][numberOfValuesAVG-1]+ "," + accelZAVG[1][numberOfValuesAVG-1] + ";\n";
            final TextView helloTextView = (TextView) findViewById(R.id.t_hw);
            helloTextView.setText(""+numberOfValuesAVG);
        }
        final TextView helloTextView = (TextView) findViewById(R.id.t_debug2);
        if(numberOfValuesAVG>2)
            helloTextView.setText(""+accelZAVG[1][numberOfValuesAVG-1]);

    }

    private int howManyToLookAt = 6; // nulldurchgaenge
    private int[][] recentperiods; // 0 from 1 to; and number is number of howmany to look at

    private int whenAmpliRecalc = 5;
    private int ampliRecalc =0;
    Deque<Float> stack;

    private int getBPM()
    {

        // first of all check the last 5 values wether they were more than 3??
       /* boolean passts = false;
        if(numberOfValuesAVG>5)
        {
            for(int i = numberOfValuesAVG ; i <  numberOfValuesAVG -5 ; i--)
            {
                if(accelZAVG[1][i] > 2 ||accelZAVG[1][i] < 2)
                {
                    // nice also passts
                    passts = true;
                }
            }
            if(passts == false)
            {
                final TextView helloTextView = (TextView) findViewById(R.id.t_bpm);
                helloTextView.setText("BPM: 0");
                final TextView t2 = (TextView) findViewById(R.id.t_Amplitude);
                t2.setText("A: " +  ("0") + " mm");
                return 0;
            }
        }*/




        // step 1: get latest position and value
        recentperiods = new int[2][howManyToLookAt];
        int periodsFound = 0;
        boolean foundbeginning = false;
        int posPointer = numberOfValuesAVG;
        if(posPointer < 1)
            return 0;
        // look for howmanytolookat last 0 durchgaenge
        for(int i = numberOfValuesAVG; i > 1; i--)
        {
            if(accelZAVG[1][i] > 0 && accelZAVG[1][i-1] < 0 || accelZAVG[1][i] < 0 && accelZAVG[1][i-1] > 0  )// nulldurchgang positiv
            {
                if(!foundbeginning) {
                    recentperiods[0][periodsFound] = i;
                }
                else{
                    recentperiods[1][periodsFound] = i;
                    periodsFound++;
                }
                foundbeginning = !foundbeginning;
                if(periodsFound == howManyToLookAt)
                {
                    i=0;
                }
            }
        }

        /// Now we are going to do some sanity check if something is happenning at all since about 2 sek.
//use latest value found.  this should be this
        int latestThingy = recentperiods[0][0];
        int beforethatlatest = numberOfValuesAVG; // what the fuck am i even coding here? my brain is on energy safe maaan  #dontcare.
        int differenzDing = (beforethatlatest - latestThingy) * ABTASTFREQ;
        if(differenzDing > 2000)
        {
            final TextView helloTextView = (TextView) findViewById(R.id.t_bpm);
            helloTextView.setText("Rate: 0 bpm");
            final TextView t2 = (TextView) findViewById(R.id.t_Amplitude);
            t2.setText("Depth: " +  ("0") + " mm");
            return 0; // kek
        }
        // calc BPM and return
        if(periodsFound < howManyToLookAt)
        {
            bpm =0 ;
            amplitudeInMM = 0;
            return 0;
        }

        // calc actual bpm
        int aBPM = 0;

        bpm = (int)(60000.0 / ( ((accelZAVG[0][recentperiods[0][0]]  -  accelZAVG[0][recentperiods[0][periodsFound-1]] )/ (periodsFound))));
        final TextView helloTextView = (TextView) findViewById(R.id.t_bpm);
        helloTextView.setText("Rate: " +  (bpm-30) + " bpm");

        // now calculate the double integration for these periods.
        // get from m/s^2 to m/s from TRAPEZ integration
        float[] integral1 = new float[periodsFound];
        float[] travelled = new float[periodsFound];
        for(int i = 0; i <  periodsFound; i++)
        {
            integral1[i] = 0;
            travelled[i] = 0;
        }

        for(int k = 0; k < periodsFound; k++)
        {
            int howMany = recentperiods[0][k]  -  recentperiods[1][k];
            float someintegral = 0;
            for(int i = 0; i < howMany-1; i++)
            {
                someintegral = Math.abs( someintegral + (float)(((accelZAVG[0][recentperiods[0][k]-i] - accelZAVG[0][recentperiods[0][k]-(i+1)])/1000.0) * (((accelZAVG[1][recentperiods[0][k]-i] + accelZAVG[1][recentperiods[0][k]-(i+1)]))/2.0)));
            }
            integral1[k] = someintegral; // average m/s in this time interval.
            travelled[k] = (float)(integral1[k] / ((accelZAVG[0][recentperiods[0][k]] - accelZAVG[0][recentperiods[1][k]])/1000.0));
            // now we can calculate the way travelled for this part
        }
        // calculate average travelled over iterations
        float avgtravel=0;
        for (int i = 0; i < periodsFound; i++)
        {
            avgtravel = avgtravel + travelled[i];
        }
        avgtravel = (float)(avgtravel*100.0)/(float)periodsFound;
        Log.e("TRAVEL", " Amount:" + avgtravel);

        ampliRecalc++;
        lastAmplitudes[currentAmplitude] = avgtravel;
        currentAmplitude++;
        float outputValueOfMovement = 0;
        if(currentAmplitude > 10)
        {
            float[] af = new float[10];
            for (int k = 0; k < 10 ; k ++)
            {
                af[k] = lastAmplitudes[currentAmplitude-k];
            }
            Arrays.sort(af);
            outputValueOfMovement = af[8];
        }

            final TextView t2 = (TextView) findViewById(R.id.t_Amplitude);
            t2.setText("Depth: " +  ((int) outputValueOfMovement * 2 ) + " mm");



        // first for one half.


        // get from m/s to m from TRAPEZ integration

        return 42;
    }

    private float getAmplitudeBetweenPeaks()
    {
return 2;
    }


    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }



    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //TODO: we'll see later about it
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        // The light sensor returns a single value.
        // Many sensors return 3 values, one for each axis.
        float[] gravity= new float[3];
        float[] linear_acceleration= new float[3];

        gravity[0] = event.values[0];
        gravity[1] = event.values[1];
        gravity[2] = event.values[2];

        //output of debug values
        final TextView helloTextView = (TextView) findViewById(R.id.t_debug);
        helloTextView.setText("X: " + gravity[0] + " Y: "+ gravity[1] + " Z: " + gravity[2]);


        accelometer[1][numberOfValues] = gravity[2];
        accelometer[0][numberOfValues] =  System.currentTimeMillis() - lastReadInMS;
        lastReadInMS = System.currentTimeMillis();
        timeOfFrameInMS = timeOfFrameInMS + (int)accelometer[0][numberOfValues];
        // if longer than 170ms create new entry in curve
        if(timeOfFrameInMS > ABTASTFREQ)
        {
            timeOfFrameInMS =0;
            slidingAveraging();
            zeroArray();
            numberOfValues=0;
            getBPM();
        }
        else
        {
            numberOfValues++;
        }

        if(isRecording)
        {
       //     long time= System.currentTimeMillis();
       //     saveString += time + "," + gravity[0] + "," + gravity[1] + "," + gravity[2] + ";\n";
        }



        //   float alpha = 0.8f;
//
  //      gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
    //    gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
      //  gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

//        linear_acceleration[0] = event.values[0] - gravity[0];
  //      linear_acceleration[1] = event.values[1] - gravity[1];
    //    linear_acceleration[2] = event.values[2] - gravity[2];

        //output of debug values
       // final TextView helloTextView = (TextView) findViewById(R.id.t_debug);
        //helloTextView.setText("X: " + linear_acceleration[0] + " Y: "+ linear_acceleration[1] + " Z: "+ linear_acceleration[2]);

        // save values if needed

    }

    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_FASTEST);
        final TextView helloTextView = (TextView) findViewById(R.id.t_debug);
        helloTextView.setText("Test2");
    }

    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }


    private void writeToFile(String data,Context context) {
        try {
            long time= System.currentTimeMillis();

            // new approach
            File file = new File(this.getExternalFilesDir(null), "Recording" + time + ".txt");
            String outdir = this.getExternalFilesDir(null).getAbsolutePath();
            FileOutputStream fileOutput = new FileOutputStream(file);
            OutputStreamWriter outputStreamWriter=new OutputStreamWriter(fileOutput);
            outputStreamWriter.write(saveString);
            outputStreamWriter.flush();
            fileOutput.getFD().sync();
            outputStreamWriter.close();

            MediaScannerConnection.scanFile(
                    this,
                    new String[]{file.getAbsolutePath()},
                    null,
                    null);

        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

    @Override
    public void onClick(View v)
    {
        switch (v.getId()) {
            case  R.id.b_startRec: {
                saveString = "";
                isRecording = true;
                break;
            }

            case R.id.b_StopAndSave: {
                isRecording = false;
                writeToFile(saveString,this);
                final TextView helloTextView = (TextView) findViewById(R.id.t_hw);
                helloTextView.setText("SAVED!!!");
                // do something for button 2 click
                break;
            }

        }
    }
}
