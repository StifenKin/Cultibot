package com.example.cultibot;

import static java.lang.Math.abs;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
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

public class SensorsActivity extends AppCompatActivity implements ToastInterface, SensorEventListener{
    private static final String LOG_TAG = "SENSORS";
    // MAC ADDRESS BT
    private static String address = null;

    private final int UPDATING_STATUS = 1000;
    private final int UPDATED_STATUS = 1001;

    private boolean semaphore = false;

    private SensorManager mySensorManager;
    public ProgressReceiver rcv;
    private TextView statusText;

    private TextView temperatureValue;
    private TextView lightValue;
    private TextView humidityValue;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensors);

        address = getIntent().getExtras().getString(getString(R.string.BluetoothAddressIntentKey));

        // Defino boton
        Button refreshButton = findViewById(R.id.refreshButton);

        // Defino los txt para representar los datos de los sensores
        statusText      = findViewById(R.id.statusText);

        temperatureValue = findViewById(R.id.temperatureValue);
        lightValue       = findViewById(R.id.lightValue);
        humidityValue    = findViewById(R.id.humidityValue);

        // Accedemos al servicio de sensores
        mySensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        // Boton que muestra el listado de los sensores disponibles
        refreshButton.setOnClickListener(v -> updateStatus(UPDATING_STATUS));

        // Hardcodeo de sensores
        temperatureValue.setTypeface(temperatureValue.getTypeface(), Typeface.BOLD);
        humidityValue.setTypeface(humidityValue.getTypeface(), Typeface.BOLD);
        lightValue.setTypeface(lightValue.getTypeface(), Typeface.BOLD);

        temperatureValue.setText("22°");
        humidityValue.setText("75%");
        lightValue.setText("55%");

        updateStatus(UPDATED_STATUS);

        //se especifica que mensajes debe aceptar el broadcastreceiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(BlueToothService.ACTION_MAIN_MENU);
        rcv = new ProgressReceiver();
        registerReceiver(rcv, filter);
    }

    protected void ini_sensor()
    {
        mySensorManager.registerListener(this, mySensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),   SensorManager.SENSOR_DELAY_NORMAL);
    }

    protected void stop_sensor()
    {
        mySensorManager.unregisterListener(this, mySensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
        unregisterReceiver(rcv);
    }

    /*Called when the user taps the Volver Button*/
    public void gotoMainActivity(View view) {
        Intent intent = new Intent(this, MainActivity.class);
        Bundle extras;
        extras = new Bundle();
        extras.putString(getString(R.string.BluetoothAddressIntentKey), address);
        intent.putExtras(extras);
        startActivity(intent);
    }

    protected void updateStatus(int newState)
    {
        switch(newState){
            case UPDATING_STATUS:
                Log.i(LOG_TAG, "Address is " + address + ".");
                if(semaphore || address.equals("")) {
                    Log.i(LOG_TAG, "State is not updated.");
                    return;
                }
                Intent msgIntent = new Intent(SensorsActivity.this, BlueToothService.class);
                msgIntent.putExtra(getString(R.string.BluetoothAddressIntentKey), address);
                msgIntent.putExtra(getString(R.string.CommandIntentKey), "GET");
                startService(msgIntent);

                statusText.setText(R.string.updatingStatusText);

                SensorsActivity.ThreadAsyncTask hilo =  new SensorsActivity.ThreadAsyncTask();
                hilo.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                semaphore = true;
                break;
            case UPDATED_STATUS:
                statusText.setText(R.string.updatedStatusText);
                break;
            default:
                statusText.setText(R.string.statusUpdateFailure);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {
        if (shakeDetected(event.values[0], event.values[1], event.values[2]))
        {
            updateStatus(UPDATING_STATUS);
        }
    }

    private boolean shakeDetected(final float x, final float y, final float z)
    {
        int SHAKE_THRESHOLD = 10;
        return (abs(x) > SHAKE_THRESHOLD) || (abs(y) > SHAKE_THRESHOLD) || (abs(z) > SHAKE_THRESHOLD);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {}

    @Override
    protected void onRestart()
    {
        ini_sensor();
        super.onRestart();
    }

    @Override
    protected void onPause() {
        stop_sensor();
        super.onPause();
    }

    @Override
    //Cada vez que se detecta el evento OnResume se establece la comunicacion con el HC05, creando un
    //socketBluethoot
    public void onResume() {
        super.onResume();
        ini_sensor();

        // Obtengo el parametro, aplicando un Bundle, que me indica la Mac Adress del HC05
        if(!address.equals("")) {
            Intent intent = getIntent();
            try {
                if (intent.hasExtra(getString(R.string.BluetoothAddressIntentKey))) {
                    Bundle extras = intent.getExtras();
                    address = extras.getString(getString(R.string.BluetoothAddressIntentKey));
                }else{
                    address = "";
                }
            }
            catch (Exception ex)
            {
                showToast(getApplicationContext(), "Fallo el intent: " + ex);
            }
        }

        updateStatus(UPDATING_STATUS);
    }


    public class ProgressReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(BlueToothService.ACTION_MAIN_MENU)) {
                Bundle extras = intent.getExtras();
                String sensorsData = extras.getString(getString(R.string.SensorsDataIntentKey));
                formatData(sensorsData);
            }
            else {
                showToast(getApplicationContext(), getString(R.string.dataFetchFailure));
            }
        }

        private void formatData(String data) {
            Log.i(LOG_TAG, data);

            String[] sensors = data.split(";");
            temperatureValue.setText(sensors[0] + "°");
            lightValue.setText(sensors[1] + "%");
            humidityValue.setText(sensors[2] + "%");
        }
    }

    private class ThreadAsyncTask extends AsyncTask<Integer, Void, Integer> {

        @Override
        protected Integer doInBackground(Integer... integers) {
            try {
                int SECONDS = 3000;
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
