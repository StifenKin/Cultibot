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
import android.view.View;
import android.widget.Button;
import android.widget.ListView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;

/***************************************************************************************************
 * Activity que muestra el listado de los dispositivos Bluetooth (des)conectados
 **************************************************************************************************/

public class DeviceListActivity extends Activity implements ToastInterface {
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

        //Se crea un adaptador para poder manejar el Bluetooth del celular
        myBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Se determina si existe Bluetooth en el celular
        checkIfBluetoothEnabled();

        btnFind.setOnClickListener(btnBuscarListener);

        //Se Crea la ventana de dialogo que indica que se esta buscando dispositivos Bluetooth
        myProgressDialog = new ProgressDialog(DeviceListActivity.this);

        myProgressDialog.setMessage(getString(R.string.onDiscoveryText));
        myProgressDialog.setCancelable(false);
        //se asocia un listener al boton cancelar para la ventana de dialogo ue busca los dispositivos Bluetooth
        myProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.cancelText), btnCancelarDialogListener);


        // Defino un adaptador para el ListView donde se van mostrar en la activity los dispositovs encontrados
        myAdapter = new DeviceListAdapter(this);

        // Asocio el listado de los dispositovos pasado en el bundle al adaptador del Listview
        myAdapter.setData(myDeviceList);

        // Defino un listener en el boton emparejar del listview
        myAdapter.setListener(pairBtnListener);

        myListView.setAdapter(myAdapter);

        // Se definen un broadcastReceiver que captura el broadcast del SO cuando captura los siguientes eventos:
        IntentFilter filter = new IntentFilter();

        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);      // Cambia el estado del Bluetooth (Acrtivado /Desactivado)
        filter.addAction(BluetoothDevice.ACTION_FOUND);               // Se encuentra un dispositivo Bluetooth al realizar una busqueda
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);  // Cuando se comienza una busqueda de Bluetooth
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED); // Cuando la busqueda de Bluetooth finaliza
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);  // Cuando se empareja o desempareja el Bluetooth

        //se define (registra) el handler que captura los broadcast anterirmente mencionados.
        registerReceiver(bluetoothReceiver, filter);
    }

    private final View.OnClickListener btnBuscarListener = new View.OnClickListener() {
        @SuppressLint("MissingPermission")
        @Override
        public void onClick(View v) {
            myBluetoothAdapter.startDiscovery();
        }
    };

    @SuppressLint("MissingPermission")
    @Override
    //Cuando se llama al metodo OnPausa se cancela la busqueda de dispositivos Bluetooth
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
            // Obtengo los datos del dispostivo seleccionado del listview por el usuario
            BluetoothDevice device = myDeviceList.get(position);

            // Se chequea si el dipositivo ya esta emparejado
            if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                // Si esta emparejado, entonces se lo desempareja
                unpairDevice(device);
            } else {
                // Si no esta emparejado, entonces se lo empareja
                showToast(getApplicationContext(),getString(R.string.pairingText));
                posicionListBluetooth = position;
                pairDevice(device);
            }
        }
    };

    // Handler que captura los brodacast que emite el SO al ocurrir los eventos del Bluetooth
    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        public void onReceive(Context context, Intent intent) {
            //Atraves del Intent obtengo el evento de Bluetooth que informo el broadcast del SO
            String action = intent.getAction();

            switch(action) {

                // Cambia el estado del Bluetooth (Activado/Desactivado)
                case BluetoothAdapter.ACTION_STATE_CHANGED : {
                    //Obtengo el parametro, aplicando un Bundle, que me indica el estado del Bluetooth
                    final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                    // Si esta activado
                    if (state == BluetoothAdapter.STATE_ON)
                    {
                        showToast(getApplicationContext(),getString(R.string.bluetoothEnabled));
                    }
                    break;
                }

                // Si se inicio la busqueda de dispositivos Bluetooth
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED : {
                    // Creo la lista donde voy a mostrar los dispositivos encontrados
                    myDeviceList = new ArrayList<>();

                    myAdapter.setData(myDeviceList);
                    myListView.setAdapter(myAdapter);

                    btnFind.setEnabled(false);
                    // Muestro el cuadro de dialogo de busqueda
                    myProgressDialog.show();
                    break;
                }

                // Si finalizo la busqueda de dispositivos Bluetooth
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED : {
                    // Se cierra el cuadro de dialogo de busqueda
                    myProgressDialog.dismiss();
                    btnFind.setEnabled(true);
                    break;
                }

                // Si se encontro un dispositivo Bluetooth
                case BluetoothDevice.ACTION_FOUND : {
                    // Se agregan datos del dispositivo a una lista de dispositivos encontrados
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    myDeviceList.add(device);
                    HashSet nonRepeatedDevices = new HashSet(myDeviceList);
                    myDeviceList.clear();
                    myDeviceList.addAll(nonRepeatedDevices);
                    myAdapter.setData(myDeviceList);
                    myListView.setAdapter(myAdapter);
                    break;
                }

                // Si el SO detecto un (des)emparejamiento de Bluetooth
                case BluetoothDevice.ACTION_BOND_STATE_CHANGED: {
                    // Obtengo los parametro, aplicando un Bundle, que me indica el estado
                    final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                    final int prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);

                    if (state == BluetoothDevice.BOND_BONDED && prevState == BluetoothDevice.BOND_BONDING) {
                        // Si se emparejo un dispositivo
                        BluetoothDevice device = (BluetoothDevice) myAdapter.getItem(posicionListBluetooth);
                        address = device.getAddress();
                        showToast(getApplicationContext(), getString(R.string.pairedDevice));
                    }
                    else if (state == BluetoothDevice.BOND_NONE && prevState == BluetoothDevice.BOND_BONDED) {
                        // Si se desemparejo un dispositivo
                        showToast(getApplicationContext(), getString(R.string.deviceUnpaired));
                        address = null;
                    }

                    myAdapter.notifyDataSetChanged();
                    break;
                }
            }
        }
    };

}


