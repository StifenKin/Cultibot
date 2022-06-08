package com.example.dashboardview;

import static java.lang.Math.abs;

import android.annotation.SuppressLint;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private final int UPDATING_STATUS = 1000;
    private final int UPDATED_STATUS = 1001;
    private final int SHAKE_THRESHOLD = 10;
    private final int SECONDS = 3000;

    private boolean semaphore = false;

    private SensorManager mSensorManager;

    private TextView statusText;

    private TextView temperatureText;
    private TextView lightText;
    private TextView humidityText;

    private TextView temperatureValue;
    private TextView lightValue;
    private TextView humidityValue;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Defino boton
        Button refreshButton = (Button) findViewById(R.id.refreshButton);

        // Defino los txt para representar los datos de los sensores
        statusText      = (TextView) findViewById(R.id.statusText);

        temperatureText = (TextView) findViewById(R.id.temperatureText);
        lightText       = (TextView) findViewById(R.id.lightText);
        humidityText    = (TextView) findViewById(R.id.humidityText);

        temperatureValue = (TextView) findViewById(R.id.temperatureValue);
        lightValue       = (TextView) findViewById(R.id.lightValue);
        humidityValue    = (TextView) findViewById(R.id.humidityValue);


        // Accedemos al servicio de sensores
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        // Boton que muestra el listado de los sensores disponibles
        refreshButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                updateStatus(UPDATING_STATUS);
            }
        });

        temperatureText.setText("Temperatura");
        humidityText.setText("Humedad");
        lightText.setText("Luminosidad");

        // Hardcodeo de sensores
        temperatureValue.setTypeface(temperatureValue.getTypeface(), Typeface.BOLD);
        humidityValue.setTypeface(humidityValue.getTypeface(), Typeface.BOLD);
        lightValue.setTypeface(lightValue.getTypeface(), Typeface.BOLD);

        temperatureValue.setText("22°");
        humidityValue.setText("75%");
        lightValue.setText("55%");

        updateStatus(UPDATED_STATUS);
    }

    protected void ini_sensor()
    {
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),   SensorManager.SENSOR_DELAY_NORMAL);
    }

    protected void stop_sensor()
    {
        mSensorManager.unregisterListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
    }

    @SuppressLint("SetTextI18n")
    protected void updateStatus(int newState)
    {
        switch(newState){
            case UPDATING_STATUS:
                if(semaphore) {
                    return;
                }
                statusText.setText("Obteniendo data de sensores...");

                 ThreadAsyncTask hilo =  new ThreadAsyncTask();
                 hilo.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                semaphore = true;
                break;
            case UPDATED_STATUS:
                statusText.setText("Actualizado");
                break;
            default:
                statusText.setText("Error al actualizar estado!");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {
        if ((abs(event.values[0]) > SHAKE_THRESHOLD) || (abs(event.values[1]) > SHAKE_THRESHOLD) || (abs(event.values[2]) > SHAKE_THRESHOLD))
        {
            updateStatus(UPDATING_STATUS);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {

    }

    @Override
    protected void onPause()
    {
        stop_sensor();
        super.onPause();
    }

    @Override
    protected void onRestart()
    {
        ini_sensor();
        super.onRestart();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        ini_sensor();
    }

    private class ThreadAsyncTask extends AsyncTask<Integer, Void, Integer> {

        @Override
        protected Integer doInBackground(Integer... integers) {
            try {
                Thread.sleep(SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return 1;
        }

        protected void onPostExecute(Integer result){
            updateStatus(UPDATED_STATUS);
            semaphore = false;
        }
    }
}

