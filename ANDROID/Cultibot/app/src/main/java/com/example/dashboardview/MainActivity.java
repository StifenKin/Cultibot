package com.example.dashboardview;

import static java.lang.Math.abs;

import android.annotation.SuppressLint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private final int UPDATING_STATUS = 1000;
    private final int UPDATED_STATUS = 1001;
    private final int SHAKE_THRESHOLD = 10;
    private final int FIVE_SECONDS = 5000;

    private boolean semaphore = false;

    private SensorManager mSensorManager;

    private TextView statusText;
    private TextView temperatureText;
    private TextView lightText;
    private TextView humidityText;

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

        // Hardcodeo de sensores
        temperatureText.setText("Temperatura: 22°C");
        humidityText.setText("Humedad 75%");
        lightText.setText("Luminosidad 55%");

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
                Log.i("onCreate",  "Se creo un nuevo hilo");
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
                Log.i("onCreate", "Hilo pre siesta");
                Thread.sleep(FIVE_SECONDS);
                Log.i("onCreate", "Hilo post siesta");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return 1;
        }

        protected void onPostExecute(Integer result){
            updateStatus(UPDATED_STATUS);
            semaphore = false;
            Log.i("onCreate",  "Se termino el hilo");
        }
    }
}

