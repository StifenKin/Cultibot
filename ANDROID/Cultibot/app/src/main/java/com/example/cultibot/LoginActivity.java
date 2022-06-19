package com.example.cultibot;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {
    EditText inputDate;
    TextView errorText;

    private final static Integer MIN_BIRTHDAY_ALLOWED = 2004;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        inputDate = findViewById(R.id.inputDate);
        errorText = findViewById(R.id.errorText);
        errorText.setText("");
    }

    private boolean userHas18YearsOldOrMore(String userBirthday) {
         String[] parsedBirthday = userBirthday.split("/"); // Formato fecha: DD/MM/AAAA
         if(parsedBirthday.length < 2) {
             return false;
         }
         return MIN_BIRTHDAY_ALLOWED >= Integer.parseInt(parsedBirthday[2]);
    }

    /* Called when the user taps the Enviar Button */
    public void gotoMainActivity(View view) {

        String userInput = inputDate.getText().toString();

        // Se valida edad ingresada
        if(!userHas18YearsOldOrMore(userInput)) {
            errorText.setText(R.string.invalidDate);
            return;
        }

        // Se limpia mensaje de error
        errorText.setText("");

        Intent intent = new Intent(this, MainActivity.class);
        Bundle extras;
        extras = new Bundle();
        extras.putString(getString(R.string.BluetoothAddressIntentKey), null);
        intent.putExtras(extras);
        startActivity(intent);
    }
}