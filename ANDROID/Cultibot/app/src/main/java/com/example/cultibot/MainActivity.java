package com.example.cultibot;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity implements ToastInterface {
    private String address = null;
    Button btnCheckState;
    Button btnConnectDevice;

    public static final int MULTIPLE_PERMISSIONS = 10;

    final String[] permissions= new String[]{
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    }; // Array de Strings con los permisos a solicitar en tiempo de ejecucion

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Defino boton
        btnCheckState = findViewById(R.id.btnCheckState);
        btnConnectDevice = findViewById(R.id.btnConnectDevice);

        address = getIntent().getExtras().getString(getString(R.string.BluetoothAddressIntentKey));

        btnConnectDevice.setEnabled(checkPermissions());

        btnCheckState.setEnabled(!(address == null || address.equals("")));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MULTIPLE_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // permissions granted.
                btnConnectDevice.setEnabled(true);
            } else {
                // permissions list of don't granted permission
                showToast(getApplicationContext(), getString(R.string.lackOfPermissions));
            }
        }
    }


    // Metodo que chequea si estan habilitados los permisos
    private  boolean checkPermissions() {
        int result;
        List<String> listPermissionsNeeded = new ArrayList<>();

        // Se chequea si la version de Android es menor a la 6
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }


        for (String p:permissions) {
            result = ContextCompat.checkSelfPermission(this,p);
            if (result != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(p);
            }
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]),MULTIPLE_PERMISSIONS );
            return false;
        }
        return true;
    }


    /*Called when the user taps the Chequear Estado Button*/
    public void gotoSensorsActivity(View view){
        Intent intent = new Intent(this, SensorsActivity.class);
        Bundle extras;
        extras = new Bundle();
        extras.putString(getString(R.string.BluetoothAddressIntentKey), address);
        intent.putExtras(extras);
        startActivity(intent);
    }

    /*Called when the user taps the Conectar dispositivo Button*/
    public void gotoDeviceListActivity(View view){
        Intent intent = new Intent(this, DeviceListActivity.class);
        Bundle extras;
        extras = new Bundle();
        intent.putExtras(extras);
        startActivity(intent);
    }

}

