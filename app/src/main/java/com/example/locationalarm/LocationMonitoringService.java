package com.example.locationalarm;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.example.missed.R;

public class LocationMonitoringService extends Service {
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "location_monitoring_channel";
    private static final long UPDATE_INTERVAL = 10000; // 10 segundos
    private static final long FASTEST_INTERVAL = 5000; // 5 segundos
    
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LatLng destinationLocation;
    private double targetRadius;
    private boolean isMonitoring = false;
    
    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createNotificationChannel();
        setupLocationCallback();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if ("ACTION_STOP_MONITORING".equals(intent.getAction())) {
                // Parar o serviço sem lançar activity
                stopMonitoring();
                stopSelf();
                return START_NOT_STICKY;
            }
            
            destinationLocation = intent.getParcelableExtra("destination");
            targetRadius = intent.getDoubleExtra("radius", 1.0);
            
            if (!isMonitoring) {
                startMonitoring();
            }
        }
        return START_STICKY;
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Location Monitoring",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Monitoring location to wake you up at destination");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.enableLights(true);
            channel.setShowBadge(true);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }
    
    private Notification createNotification() {
        // Intent para abrir o app ao clicar na notificação
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );
        
        // Intent para cancelar o monitoramento
        Intent cancelIntent = new Intent(this, LocationMonitoringService.class);
        cancelIntent.setAction("ACTION_STOP_MONITORING");
        PendingIntent cancelPendingIntent = PendingIntent.getService(
            this, 0, cancelIntent, PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Missed - Location Alarm")
            .setContentText("Monitoring your location...")
            .setSmallIcon(R.drawable.ic_location)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent);

        // Adicionar ação de cancelar
        builder.addAction(R.drawable.ic_close, "Cancel", cancelPendingIntent);
        
        return builder.build();
    }
    
    private void startMonitoring() {
        LocationRequest locationRequest = LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setInterval(UPDATE_INTERVAL)
            .setFastestInterval(FASTEST_INTERVAL);
            
        if (ActivityCompat.checkSelfPermission(this, 
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            
            try {
                // Primeiro criar a notificação
                Notification notification = createNotification();
                
                // Iniciar o serviço em foreground antes de pedir atualizações de localização
                startForeground(NOTIFICATION_ID, notification);
                
                // Depois pedir atualizações de localização
                fusedLocationClient.requestLocationUpdates(
                    locationRequest, locationCallback, Looper.getMainLooper()
                );
                
                isMonitoring = true;
                android.util.Log.d("LocationMonitoring", "Location monitoring started successfully");
            } catch (Exception e) {
                android.util.Log.e("LocationMonitoring", "Error starting location updates: " + e.getMessage());
            }
        }
    }
    
    private void stopMonitoring() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        } catch (SecurityException e) {
            android.util.Log.e("LocationMonitoring", "Error removing location updates: " + e.getMessage());
        }
        stopForeground(true);
        stopSelf();
        isMonitoring = false;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isMonitoring) {
            stopMonitoring();
        }
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                
                Location currentLocation = locationResult.getLastLocation();
                checkDestinationReached(currentLocation);
            }
        };
    }
    
    private void checkDestinationReached(Location currentLocation) {
        if (currentLocation == null || destinationLocation == null) return;
        
        float[] results = new float[1];
        Location.distanceBetween(
            currentLocation.getLatitude(), currentLocation.getLongitude(),
            destinationLocation.latitude, destinationLocation.longitude,
            results
        );
        
        // Log para debug
        android.util.Log.d("LocationMonitoring", 
            "Distance to destination: " + results[0] + "m, Target radius: " + (targetRadius * 1000) + "m");
        
        // Converter o raio de km para metros e verificar
        if (results[0] <= targetRadius * 1000) {
            android.util.Log.d("LocationMonitoring", "Destination reached! Triggering alarm...");
            triggerAlarm();
        }
    }
    
    private void triggerAlarm() {
        // Criar intent para abrir a atividade de alarme
        Intent alarmIntent = new Intent(this, AlarmActivity.class);
        alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(alarmIntent);
        
        // Parar o monitoramento
        stopMonitoring();
    }
}