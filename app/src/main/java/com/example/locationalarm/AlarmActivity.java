package com.example.locationalarm;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Vibrator;
import android.view.animation.AnimationUtils;
import androidx.appcompat.app.AppCompatActivity;
import com.example.missed.R;
import com.google.android.material.button.MaterialButton;

public class AlarmActivity extends AppCompatActivity {
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);
        
        // Configurar WakeLock para manter a tela ligada
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK | 
            PowerManager.ACQUIRE_CAUSES_WAKEUP | 
            PowerManager.ON_AFTER_RELEASE,
            "Missed:AlarmLock"
        );
        wakeLock.acquire(10*60*1000L); // 10 minutos
        
        // Iniciar vibração
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator.hasVibrator()) {
            long[] pattern = {0, 1000, 1000}; // Vibrar a cada segundo
            vibrator.vibrate(pattern, 0);
        }
        
        // Iniciar som
        mediaPlayer = MediaPlayer.create(this, R.raw.alarm_sound);
        mediaPlayer.setLooping(true);
        mediaPlayer.start();
        
        // Configurar botão de parar
        MaterialButton btnStopAlarm = findViewById(R.id.btn_stop_alarm);
        btnStopAlarm.setOnClickListener(v -> {
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_click));
            stopAlarmAndFinish();
        });
    }
    
    private void stopAlarmAndFinish() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        
        if (vibrator != null) {
            vibrator.cancel();
        }
        
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        
        // Voltar para a MainActivity
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }
    
    @Override
    public void onBackPressed() {
        // Impedir que o usuário feche a atividade com o botão voltar
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
} 