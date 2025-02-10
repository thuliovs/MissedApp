package com.example.locationalarm;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.appcompat.app.AppCompatActivity;
import com.example.missed.R;
import com.google.android.material.button.MaterialButton;

public class LocationCheckActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_check);
        setupButtons();
    }

    private void setupButtons() {
        MaterialButton turnOnButton = findViewById(R.id.btn_turn_on);
        MaterialButton notNowButton = findViewById(R.id.btn_not_now);

        turnOnButton.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        });

        notNowButton.setOnClickListener(v -> {
            finish();
            overridePendingTransition(0, R.anim.slide_out_right);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            // Se a localização foi ativada, inicia o MapActivity
            Intent intent = new Intent(this, MapActivity.class);
            startActivity(intent);
            finish();
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_right);
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, R.anim.slide_out_right);
    }
} 