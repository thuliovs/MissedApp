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
        startEntranceAnimation();
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

    private void startEntranceAnimation() {
        View icon = findViewById(R.id.location_icon);
        View title = findViewById(R.id.title_text);
        View description = findViewById(R.id.description_text);
        View buttons = findViewById(R.id.buttons_container);

        // Configurar animações
        ObjectAnimator iconScale = ObjectAnimator.ofFloat(icon, "scaleX", 0.8f, 1f);
        ObjectAnimator iconScale2 = ObjectAnimator.ofFloat(icon, "scaleY", 0.8f, 1f);
        ObjectAnimator iconAlpha = ObjectAnimator.ofFloat(icon, "alpha", 0f, 1f);

        ObjectAnimator titleAlpha = ObjectAnimator.ofFloat(title, "alpha", 0f, 1f);
        ObjectAnimator titleTranslate = ObjectAnimator.ofFloat(title, "translationY", 20f, 0f);

        ObjectAnimator descAlpha = ObjectAnimator.ofFloat(description, "alpha", 0f, 1f);
        ObjectAnimator descTranslate = ObjectAnimator.ofFloat(description, "translationY", 20f, 0f);

        ObjectAnimator buttonsAlpha = ObjectAnimator.ofFloat(buttons, "alpha", 0f, 1f);
        ObjectAnimator buttonsTranslate = ObjectAnimator.ofFloat(buttons, "translationY", 20f, 0f);

        // Criar conjunto de animações
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(iconScale, iconScale2, iconAlpha, 
                               titleAlpha, titleTranslate,
                               descAlpha, descTranslate,
                               buttonsAlpha, buttonsTranslate);
        
        animatorSet.setDuration(500);
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.start();
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