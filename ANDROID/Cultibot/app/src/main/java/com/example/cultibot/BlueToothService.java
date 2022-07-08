package com.example.cultibot;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.UUID;

public class BlueToothService extends IntentService implements ToastInterface{
    public static final String ACTION_REPORT = "com.example.cultibot.intent.action.REPORT";
    public static final String ACTION_WATER = "com.example.cultibot.intent.action.WATER";

    // SPP UUID service  - Funciona en la mayoria de los dispositivos
    private static final UUID BT_MODULE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public static String address = "";
    public static String command = "";
    final int handlerState = 0; // used to identify handler message
    Handler bluetoothIn;
    private BluetoothSocket btSocket = null;
    private final StringBuilder recDataString = new StringBuilder();
    private ConnectedThread mConnectedThread;

    // String for MAC address del Hc05

    public BlueToothService() {
        super("BlueToothService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        String data = extras.getString(getString(R.string.SensorsDataIntentKey));
        Log.i("ARDUINO", data);
        Intent bcIntent = new Intent();
        if (command.contains(getString(R.string.BLUETOOTH_REPORT_COMMAND)))
            bcIntent.setAction(ACTION_REPORT);
        else if(command.contains(getString(R.string.BLUETOOTH_WATER_COMMAND)))
            bcIntent.setAction(ACTION_WATER);

        bcIntent.putExtra(getString(R.string.SensorsDataIntentKey), data);
        sendBroadcast(bcIntent);
    }

    @SuppressLint("MissingPermission")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {        //obtengo el adaptador del bluethoot
        Bundle extras = Objects.requireNonNull(intent).getExtras();

        command = extras.getString(getString(R.string.CommandIntentKey));

        if (address.equals("")) {

            address = extras.getString(getString(R.string.BluetoothAddressIntentKey));

            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();

            //defino el Handler de comunicacion entre el hilo Principal  el secundario.
            //El hilo secundario va a mostrar informacion al layout atraves utilizando indeirectamente a este handler
            bluetoothIn = Handler_Msg_Hilo_Principal();
            //Obtengo el parametro, aplicando un Bundle, que me indica la Mac Adress del HC05

            BluetoothDevice device = btAdapter.getRemoteDevice(address);

            // Se realiza la conexion del Bluethoot crea y se conectandose a atraves de un socket
            try {
                btSocket = createBluetoothSocket(device);

                try {
                    btSocket.connect();
                } catch (IOException e) {
                    showToast(getApplicationContext(), getString(R.string.socketConnectionFailure));
                    try {
                        btSocket.close();
                    } catch (IOException e2) {
                        showToast(getApplicationContext(), getString(R.string.socketDesconnectionFailure));
                    }
                }

                // Una establecida la conexion con el Hc05 se crea el hilo secundario, el cual va a
                // recibir los datos de Arduino atraves del bluetooth
                mConnectedThread = new ConnectedThread(btSocket);
                mConnectedThread.start();

                mConnectedThread.write(command);

            } catch (IOException e) {
                showToast(getApplicationContext(), getString(R.string.socketCreationFailure));
            }
        }


        return START_STICKY;
    }


    @SuppressLint("MissingPermission")
    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        return device.createRfcommSocketToServiceRecord(BT_MODULE_UUID);
    }

    @SuppressLint("HandlerLeak")
    private Handler Handler_Msg_Hilo_Principal ()
    {
        return new Handler() {
            public void handleMessage(Message msg)
            {
                //si se recibio un msj del hilo secundario
                if (msg.what == handlerState)
                {
                    // voy concatenando el msj
                    String readMessage = (String) msg.obj;
                    recDataString.append(readMessage);
                    int endOfLineIndex = recDataString.indexOf("#");

                    // Cuando recibo toda una linea la muestro en el layout
                    if (endOfLineIndex > 0)
                    {
                            String data = recDataString.substring(0, endOfLineIndex);

                            recDataString.delete(0, recDataString.length());

                            Intent bcIntent = new Intent();
                            bcIntent.putExtra(getString(R.string.SensorsDataIntentKey), data);
                            onHandleIntent(bcIntent);
                    }
                }
            }
        };

    }


    //********************************** Hilo secundario del Service *******************************
    //****************************** recibe los datos enviados por el HC05 *************************

    private class ConnectedThread extends Thread
    {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        //Constructor de la clase del hilo secundario
        public ConnectedThread(BluetoothSocket socket)
        {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try
            {
                //Create I/O streams for connection
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                showToast(getApplicationContext(), getString(R.string.IOStreamFailure));
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        //metodo run del hilo, que va a entrar en una espera activa para recibir los msjs del HC05
        public void run()
        {
            byte[] buffer = new byte[256];
            int bytes;

            // el hilo secundario se queda esperando mensajes del HC05
            while (true)
            {
                try
                {
                    // Se leen los datos del Bluethoot
                    bytes = mmInStream.read(buffer);
                    String readMessage = new String(buffer, 0, bytes);

                    // Se muestran en el layout de la activity, utilizando el handler del hilo
                    // Principal antes mencionado
                    bluetoothIn.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }


        //write method
        public void write(String input) {
            byte[] msgBuffer = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(msgBuffer);                //write bytes over BT connection via outstream
            } catch (IOException e) {
                //if you cannot write, close the application
                showToast(getApplicationContext(), getString(R.string.OutStreamWriteFailure));
            }
        }
    }
}