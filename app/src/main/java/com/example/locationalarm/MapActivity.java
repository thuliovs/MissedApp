package com.example.locationalarm;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.missed.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButton;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LatLng selectedLocation;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        // Inicializar location services antes do mapa
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        
        // Carregar localização atual em paralelo
        if (checkLocationPermission()) {
            loadLastLocation();
        }

        setupMapFragment();
        setupButtons();
    }

    private void setupMapFragment() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            // Configurar o tema do mapa antes de carregá-lo
            mapFragment.setRetainInstance(true);
            mapFragment.getMapAsync(this);
        }
    }

    private void setupButtons() {
        MaterialButton confirmButton = findViewById(R.id.btn_confirm_location);
        MaterialButton cancelButton = findViewById(R.id.btn_back);
        
        // Animação de escala para os botões
        confirmButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(100)
                        .start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start();
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        if (selectedLocation != null) {
                            // TODO: Passar para a próxima tela
                        }
                    }
                    break;
            }
            return true;
        });

        cancelButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(100)
                        .start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start();
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        finish();
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_right);
                    }
                    break;
            }
            return true;
        });
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_right);
    }

    private void loadLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, 
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            
            fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null && mMap != null) {
                        LatLng currentLocation = new LatLng(
                            location.getLatitude(), 
                            location.getLongitude()
                        );
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15f));
                    }
                });
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        try {
            boolean success = mMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark)
            );
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Configurações do mapa...
        setupMapSettings();

        // Aguardar um pouco para garantir que o mapa está totalmente carregado
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Esconder loading com animação suave
            View loadingOverlay = findViewById(R.id.loading_overlay);
            View instructionCard = findViewById(R.id.instruction_card);
            
            loadingOverlay.animate()
                    .alpha(0f)
                    .setDuration(800)
                    .setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> {
                        loadingOverlay.setVisibility(View.GONE);
                        
                        // Mostrar o card com animação após o loading desaparecer
                        instructionCard.setVisibility(View.VISIBLE);
                        instructionCard.animate()
                                .alpha(1f)
                                .translationY(0f)
                                .setDuration(500)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                    })
                    .start();
        }, 3000);
    }

    private void setupMapSettings() {
        // Desabilitar elementos do mapa
        mMap.getUiSettings().setMyLocationButtonEnabled(false);
        mMap.getUiSettings().setCompassEnabled(false);
        mMap.getUiSettings().setMapToolbarEnabled(false);
        mMap.getUiSettings().setZoomControlsEnabled(false);

        // Centralizar logo do Google
        setupGoogleLogo();

        // Configurar click listener
        mMap.setOnMapClickListener(latLng -> {
            mMap.clear();
            selectedLocation = latLng;
            mMap.addMarker(new MarkerOptions().position(latLng));
        });

        if (checkLocationPermission()) {
            enableMyLocation();
        }
    }

    private void setupGoogleLogo() {
        // Centralizar o logo do Google Maps
        View mapView = getSupportFragmentManager().findFragmentById(R.id.map).getView();
        if (mapView != null) {
            View locationButton = ((View) mapView.findViewById(Integer.parseInt("1")).getParent()).findViewById(Integer.parseInt("2"));
            View googleLogo = mapView.findViewWithTag("GoogleWatermark");
            
            if (googleLogo != null) {
                RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) googleLogo.getLayoutParams();
                // Remove regras de alinhamento anteriores
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, 0);
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_START, 0);
                // Centraliza horizontalmente
                layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                googleLogo.setLayoutParams(layoutParams);
            }
        }
    }

    private boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return false;
        }
    }

    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation();
            }
        }
    }
} 