package com.example.cultibot;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;

import java.lang.reflect.Method;
import java.util.ArrayList;

/***************************************************************************************************
 * Activity que muestra el listado de los dispositivos Bluetooth (des)conectados
 **************************************************************************************************/

@SuppressWarnings("ALL")
public class DeviceListActivity extends Activity implements ToastInterface {
    private static final String LOG_TAG = "DEVICE_LIST";
    private ListView myListView;
    private DeviceListAdapter myAdapter;
    private ArrayList<BluetoothDevice> myDeviceList;
    private int posicionListBluetooth;
    private ProgressDialog myProgressDialog;
    private BluetoothAdapter myBluetoothAdapter;
    private Button btnFind;
    private String address = null;

    @SuppressLint("MissingPermission")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_paired_devices);

        btnFind = findViewById(R.id.btnFindDevices);

        //defino los componentes de layout
        myListView = findViewById(R.id.deviceList);

        myDeviceList = new ArrayList<>();

        //Se crea un adaptador para poder manejar el bluethoot del celular
        myBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Se determina si existe bluethoot en el celular
        checkIfBluetoothEnabled();

        btnFind.setOnClickListener(btnBuscarListener);

        // myBluetoothAdapter.startDiscovery();

        //Se Crea la ventana de dialogo que indica que se esta buscando dispositivos bluethoot
        myProgressDialog = new ProgressDialog(getApplicationContext());

        myProgressDialog.setMessage("Buscando dispositivos...");
        myProgressDialog.setCancelable(false);
        //se asocia un listener al boton cancelar para la ventana de dialogo ue busca los dispositivos bluethoot
        myProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancelar", btnCancelarDialogListener);


        // Defino un adaptador para el ListView donde se van mostrar en la activity los dispositovs encontrados
        myAdapter = new DeviceListAdapter(this);

        // Asocio el listado de los dispositovos pasado en el bundle al adaptador del Listview
        myAdapter.setData(myDeviceList);

        // Defino un listener en el boton emparejar del listview
        myAdapter.setListener(pairBtnListener);

        myListView.setAdapter(myAdapter);

        // Se definen un broadcastReceiver que captura el broadcast del SO cuando captura los siguientes eventos:
        IntentFilter filter = new IntentFilter();

        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);      // Cambia el estado del Bluethoot (Acrtivado /Desactivado)
        filter.addAction(BluetoothDevice.ACTION_FOUND);               // Se encuentra un dispositivo bluethoot al realizar una busqueda
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);  // Cuando se comienza una busqueda de bluethoot
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED); // Cuando la busqueda de bluethoot finaliza
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);  // Cuando se empareja o desempareja el bluethoot

        //se define (registra) el handler que captura los broadcast anterirmente mencionados.
        registerReceiver(bluetoothReceiver, filter);

        Log.i(LOG_TAG, "Se logea desde el onCreate method");
    }

    private final View.OnClickListener btnBuscarListener = new View.OnClickListener() {
        @SuppressLint("MissingPermission")
        @Override
        public void onClick(View v) {
            myBluetoothAdapter.startDiscovery();
            btnFind.setEnabled(false);
            Log.i(LOG_TAG,"Se logea desde el onClick del boton de Buscar");
        }
    };

    @SuppressLint("MissingPermission")
    @Override
    //Cuando se llama al metodo OnPausa se cancela la busqueda de dispositivos bluethoot
    public void onPause()
    {
        if (myBluetoothAdapter != null) {
            if (myBluetoothAdapter.isDiscovering()) {
                myBluetoothAdapter.cancelDiscovery();
            }
        }
        super.onPause();
    }

    /*Called when the user taps the Chequear Estado Button*/
    public void gotoMainActivity(View view){
        Intent intent = new Intent(this, MainActivity.class);
        Bundle extras;
        extras = new Bundle();
        extras.putString(getString(R.string.BluetoothAddressIntentKey), address);
        intent.putExtras(extras);
        startActivity(intent);
    }

    public void checkIfBluetoothEnabled() {
        if (myBluetoothAdapter == null)
        {
            showUnsupported();
        }
        else
        {
            if (myBluetoothAdapter.isEnabled())
            {
                showEnabled();
            }
            else
            {
                showDisabled();
            }
        }
    }

    private final DialogInterface.OnClickListener btnCancelarDialogListener = new DialogInterface.OnClickListener() {
        @SuppressLint("MissingPermission")
        @Override
        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            Log.i(LOG_TAG, "Se logea desde el CancelarListener method");
            myBluetoothAdapter.cancelDiscovery();
        }
    };

    @Override
    public void onDestroy() {
        unregisterReceiver(bluetoothReceiver);

        super.onDestroy();
    }

    private void pairDevice(BluetoothDevice device) {
        try {
            Method method = device.getClass().getMethod("createBond", (Class[]) null);
            method.invoke(device, (Object[]) null);
            address = device.getAddress();
            Log.i(LOG_TAG, device.getAddress());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void unpairDevice(BluetoothDevice device) {
        try {
            //noinspection rawtypes
            Method method = device.getClass().getMethod("removeBond", (Class[]) null);
            method.invoke(device, (Object[]) null);
            address = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showEnabled() {
        showToast(getApplicationContext(), getString(R.string.bluetoothEnabled));
    }

    private void showDisabled() {
        showToast(getApplicationContext(), getString(R.string.bluetoothDisabled));
    }

    private void showUnsupported() {
        showToast(getApplicationContext(),getString(R.string.bluetoothNotSupported));
    }

    //Metodo que actua como Listener de los eventos que ocurren en los componentes graficos de la activty
    private final DeviceListAdapter.OnPairButtonClickListener pairBtnListener = new DeviceListAdapter.OnPairButtonClickListener() {
        @SuppressLint("MissingPermission")
        @Override
        public void onPairButtonClick(int position) {
            //Obtengo los datos del dispostivo seleccionado del listview por el usuario
            BluetoothDevice device = myDeviceList.get(position);

            //Se checkea si el sipositivo ya esta emparejado
            if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                //Si esta emparejado,quiere decir que se selecciono desemparjar y entonces se le desempareja
                unpairDevice(device);
            } else {
                //Si no esta emparejado,quiere decir que se selecciono emparjar y entonces se le empareja
                showToast(getApplicationContext(),"Emparejando");
                posicionListBluetooth = position;
                pairDevice(device);
            }
        }
    };

    //Handler que captura los brodacast que emite el SO al ocurrir los eventos del bluethoot
    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        public void onReceive(Context context, Intent intent) {
            //Atraves del Intent obtengo el evento de Bluethoot que informo el broadcast del SO
            String action = intent.getAction();

            switch(action) {

                //Cambia el estado del Bluethoot (Acrtivado /Desactivado)
                case BluetoothAdapter.ACTION_STATE_CHANGED : {
                    //Obtengo el parametro, aplicando un Bundle, que me indica el estado del Bluethoot
                    final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                    //Si esta activado
                    if (state == BluetoothAdapter.STATE_ON)
                    {
                        showToast(getApplicationContext(),"Activar");
                    }
                    break;
                }

                // Si se inicio la busqueda de dispositivos Bluetooth
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED : {
                    //Creo la lista donde voy a mostrar los dispositivos encontrados
                    myDeviceList = new ArrayList<>();
                    Log.i(LOG_TAG,"Se logea desde el Discovery Started");

                    //muestro el cuadro de dialogo de busqueda
                    //myProgressDialog.show();
                    break;
                }

                // Si finalizo la busqueda de dispositivos Bluetooth
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED : {
                    //se cierra el cuadro de dialogo de busqueda
                    myProgressDialog.dismiss();
                    myAdapter.setData(myDeviceList);
                    myListView.setAdapter(myAdapter);
                    btnFind.setEnabled(true);
                    Log.i(LOG_TAG,"Se logea desde el Discovery Finished");
                    break;
                }

                // Si se encontro un dispositivo Bluetooth
                case BluetoothDevice.ACTION_FOUND : {
                    //Se lo agregan sus datos a una lista de dispositivos encontrados
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    Log.i(LOG_TAG,"Se logea desde el Device Found");
                    myDeviceList.add(device);
                    showToast(getApplicationContext(),"Dispositivo Encontrado: " + device.getName());

                    break;
                }

                // Si el SO detecto un (des)emparejamiento de Bluetooth
                case BluetoothDevice.ACTION_BOND_STATE_CHANGED: {

                    //Obtengo los parametro, aplicando un Bundle, que me indica el estado del Bluethoot
                    final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                    final int prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);

                    //se analiza si se puedo emparejar o no
                    if (state == BluetoothDevice.BOND_BONDED && prevState == BluetoothDevice.BOND_BONDING) {
                        //Si se detecto que se puedo emparejar el bluethoot
                        showToast(getApplicationContext(), getString(R.string.pairedDevice));
                        BluetoothDevice dispositivo = (BluetoothDevice) myAdapter.getItem(posicionListBluetooth);

                        //se inicia el Activity de comunicacion con el bluethoot, para transferir los datos.
                        //Para eso se le envia como parametro la direccion(MAC) del bluethoot Arduino
                        String direccionBluethoot = dispositivo.getAddress();
                        Intent i = new Intent(DeviceListActivity.this, MainActivity.class);
                        i.putExtra(getString(R.string.BluetoothAddressIntentKey), direccionBluethoot);

                        startActivity(i);

                    }
                    //si se detrecto un desaemparejamiento
                    else if (state == BluetoothDevice.BOND_NONE && prevState == BluetoothDevice.BOND_BONDED) {
                        showToast(getApplicationContext(), getString(R.string.deviceUnpaired));
                    }

                    myAdapter.notifyDataSetChanged();
                    break;
                }
            }
        }
    };

}


