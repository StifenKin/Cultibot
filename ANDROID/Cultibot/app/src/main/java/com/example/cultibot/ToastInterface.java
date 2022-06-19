package com.example.cultibot;

import android.content.Context;
import android.widget.Toast;

public interface ToastInterface {
    default void showToast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}
