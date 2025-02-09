package com.example.locationalarm;

import android.Manifest;
import android.animation.Animator;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import com.google.android.material.card.MaterialCardView;

import android.animation.ValueAnimator;
import android.animation.AnimatorListenerAdapter;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import java.io.IOException;
import java.util.List;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FetchPlaceResponse;
import java.util.Arrays;
import android.util.Log;
import com.google.android.libraries.places.api.model.TypeFilter;
import com.google.android.libraries.places.api.model.LocationBias;
import com.google.android.libraries.places.api.model.RectangularBounds;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LatLng selectedLocation;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private PlacesClient placesClient;
    private PlaceSuggestionAdapter suggestionAdapter;
    private MaterialCardView suggestionsCard;
    private Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private boolean isUserInput = true; // Nova flag para controlar input do usuário

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Inicializar FusedLocationClient em background
        new Thread(() -> {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        }).start();
        
        // Inicializar Places com sua chave API
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
        }
        placesClient = Places.createClient(this);
        
        setContentView(R.layout.activity_map);
        
        // Inicializar o mapa em background
        new Handler(Looper.getMainLooper()).post(() -> {
            setupMapFragment();
            setupButtons();
        });

        // Se tiver permissão, já começa a carregar a localização em paralelo
        if (checkLocationPermission()) {
            new Thread(() -> {
                loadLastLocation();
            }).start();
        }
    }

    private void setupMapFragment() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.setRetainInstance(true); // Mantém o fragmento na memória
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
                        overridePendingTransition(0, R.anim.slide_out_right);
                    }
                    break;
            }
            return true;
        });
    }

    @Override
    public void finish() {
        super.finish();
        // Apenas anima a saída do MapActivity, sem animar a MainActivity
        overridePendingTransition(0, R.anim.slide_out_right);
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
        
        // Carrega o estilo do mapa em background
        new Thread(() -> {
            try {
                MapStyleOptions style = MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark);
                runOnUiThread(() -> mMap.setMapStyle(style));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        setupMapSettings();
        setupSearchBar(); // Configurar a barra de pesquisa

        // Aguardar um pouco para garantir que o mapa está totalmente carregado
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Esconder loading com animação suave
            View loadingOverlay = findViewById(R.id.loading_overlay);
            View instructionCard = findViewById(R.id.instruction_card);
            View topBar = findViewById(R.id.top_bar_container);
            
            loadingOverlay.animate()
                    .alpha(0f)
                    .setDuration(800)
                    .setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> {
                        loadingOverlay.setVisibility(View.GONE);
                        
                        // Mostrar a top bar com a mesma animação do card
                        topBar.setVisibility(View.VISIBLE);
                        topBar.animate()
                                .alpha(1f)
                                .setDuration(500)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                        
                        // Mostrar o card com animação após o loading desaparecer
                        instructionCard.setVisibility(View.VISIBLE);
                        instructionCard.animate()
                                .alpha(1f)
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

        // Configurar click listener do mapa
        mMap.setOnMapClickListener(latLng -> {
            mMap.clear();
            selectedLocation = latLng;
            mMap.addMarker(new MarkerOptions().position(latLng));
            
            // Mostrar o botão Confirm com animação
            showConfirmButton();
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

    private void showConfirmButton() {
        MaterialButton confirmButton = findViewById(R.id.btn_confirm_location);
        MaterialButton cancelButton = findViewById(R.id.btn_back);

        // Garantir que o texto está em minúsculo
        confirmButton.setText("confirm");
        
        if (confirmButton.getVisibility() == View.GONE) {
            // Preparar o botão Confirm vazio
            confirmButton.setText("");
            LinearLayout.LayoutParams confirmParams = (LinearLayout.LayoutParams) confirmButton.getLayoutParams();
            confirmParams.weight = 0;
            // Adicionar margem direita ao Confirm
            confirmParams.rightMargin = (int) (5 * getResources().getDisplayMetrics().density); // 8dp
            confirmButton.setLayoutParams(confirmParams);
            confirmButton.setAlpha(0f);
            confirmButton.setVisibility(View.VISIBLE);

            // Primeira animação: expandir o botão vazio
            ValueAnimator weightAnimator = ValueAnimator.ofFloat(0f, 1f);
            weightAnimator.addUpdateListener(animation -> {
                float fraction = (float) animation.getAnimatedValue();
                
                // Animar peso do Cancel
                LinearLayout.LayoutParams cancelParams = (LinearLayout.LayoutParams) cancelButton.getLayoutParams();
                cancelParams.weight = 1f - (fraction * 0.5f);
                // Adicionar margem esquerda ao Cancel
                cancelParams.leftMargin = (int) (5 * getResources().getDisplayMetrics().density); // 8dp
                cancelButton.setLayoutParams(cancelParams);
                
                // Animar peso do Confirm
                confirmParams.weight = fraction * 0.5f;
                confirmButton.setLayoutParams(confirmParams);
                
                // Animar alpha do Confirm
                confirmButton.setAlpha(fraction);

                // Atualizar o texto gradualmente
                if (fraction > 0.8f && confirmButton.getText().toString().isEmpty()) {
                    confirmButton.setText("confirm");
                }
            });

            weightAnimator.setDuration(300);
            weightAnimator.setInterpolator(new DecelerateInterpolator());
            weightAnimator.start();
        }
    }

    private void setupSearchBar() {
        EditText searchInput = findViewById(R.id.search_input);
        MaterialCardView myLocationButton = findViewById(R.id.my_location_button);
        suggestionsCard = findViewById(R.id.suggestions_card);
        RecyclerView suggestionsRecyclerView = findViewById(R.id.suggestions_recycler_view);

        // Configurar RecyclerView
        suggestionAdapter = new PlaceSuggestionAdapter();
        suggestionsRecyclerView.setAdapter(suggestionAdapter);
        suggestionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Configurar listener de sugestões
        suggestionAdapter.setOnSuggestionClickListener(suggestion -> {
            isUserInput = false; // Desabilitar processamento do TextWatcher
            String primaryText = suggestion.getPrimaryText(null).toString();
            String secondaryText = suggestion.getSecondaryText(null).toString();
            String fullAddress = String.format("%s, %s", primaryText, secondaryText);
            
            searchInput.setText(fullAddress); // Isso não vai disparar busca de sugestões
            hideKeyboard();
            hideSuggestions();
            searchInput.clearFocus();
            fetchPlaceDetails(suggestion.getPlaceId());
            isUserInput = true; // Reabilitar processamento do TextWatcher
        });

        // TextWatcher que só busca quando for input do usuário
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchInput.hasFocus() && isUserInput) { // Verificar se é input do usuário
                    searchHandler.removeCallbacks(searchRunnable);
                    searchRunnable = () -> {
                        if (s.length() > 2) {
                            fetchSuggestions(s.toString());
                        } else {
                            hideSuggestions();
                        }
                    };
                    searchHandler.postDelayed(searchRunnable, 300);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Configurar ação de pesquisa no teclado
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                hideSuggestions();
                searchInput.clearFocus();
                return true;
            }
            return false;
        });

        // Listener de foco que controla as sugestões
        searchInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                hideKeyboard();
                hideSuggestions();
            } else if (searchInput.getText().length() > 2) {
                // Só mostra sugestões ao ganhar foco se já tiver texto
                fetchSuggestions(searchInput.getText().toString());
            }
        });

        // Configurar botão de localização
        myLocationButton.setOnClickListener(v -> {
            v.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .withEndAction(() -> 
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start())
                .start();

            if (checkLocationPermission()) {
                centerOnMyLocation();
            }
        });
    }

    private void fetchSuggestions(String query) {
        if (ActivityCompat.checkSelfPermission(this, 
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            
            fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        LocationBias bias = RectangularBounds.newInstance(
                            new LatLng(location.getLatitude() - 0.1, location.getLongitude() - 0.1),
                            new LatLng(location.getLatitude() + 0.1, location.getLongitude() + 0.1)
                        );

                        FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                                .setQuery(query)
                                .setLocationBias(bias)
                                .setTypeFilter(null)
                                .setOrigin(new LatLng(location.getLatitude(), location.getLongitude()))
                                .build();

                        placesClient.findAutocompletePredictions(request)
                                .addOnSuccessListener((response) -> {
                                    List<AutocompletePrediction> predictions = response.getAutocompletePredictions();
                                    if (!predictions.isEmpty()) {
                                        suggestionAdapter.setSuggestions(predictions);
                                        showSuggestions();
                                    } else {
                                        hideSuggestions();
                                    }
                                })
                                .addOnFailureListener((exception) -> {
                                    Log.e("Places", "Error fetching suggestions", exception);
                                    exception.printStackTrace();
                                    hideSuggestions();
                                });
                    }
                });
        } else {
            // Fallback para busca sem localização
            FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                    .setQuery(query)
                    .setTypeFilter(TypeFilter.ADDRESS)
                    .build();

            placesClient.findAutocompletePredictions(request)
                    .addOnSuccessListener((response) -> {
                        List<AutocompletePrediction> predictions = response.getAutocompletePredictions();
                        if (!predictions.isEmpty()) {
                            suggestionAdapter.setSuggestions(predictions);
                            showSuggestions();
                        } else {
                            hideSuggestions();
                        }
                    })
                    .addOnFailureListener((exception) -> {
                        Log.e("Places", "Error fetching suggestions", exception);
                        exception.printStackTrace();
                        hideSuggestions();
                    });
        }
    }

    private void fetchPlaceDetails(String placeId) {
        List<Place.Field> placeFields = Arrays.asList(Place.Field.LAT_LNG);
        FetchPlaceRequest request = FetchPlaceRequest.newInstance(placeId, placeFields);

        placesClient.fetchPlace(request)
                .addOnSuccessListener((response) -> {
                    Place place = response.getPlace();
                    if (place.getLatLng() != null) {
                        selectedLocation = place.getLatLng();
                        mMap.clear();
                        mMap.addMarker(new MarkerOptions().position(selectedLocation));
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(selectedLocation, 15f));
                        showConfirmButton();
                    }
                })
                .addOnFailureListener((exception) -> {
                    exception.printStackTrace();
                });
    }

    private void showSuggestions() {
        if (suggestionsCard.getVisibility() != View.VISIBLE) {
            suggestionsCard.setVisibility(View.VISIBLE);
            suggestionsCard.setAlpha(0f);
            suggestionsCard.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private void hideSuggestions() {
        if (suggestionsCard != null && suggestionsCard.getVisibility() == View.VISIBLE) {
            suggestionsCard.animate()
                    .alpha(0f)
                    .setDuration(150)
                    .setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> {
                        suggestionsCard.setVisibility(View.GONE);
                        suggestionsCard.setAlpha(0f);
                    })
                    .start();
        }
    }

    private void centerOnMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, 
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            
            fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        LatLng currentLocation = new LatLng(
                            location.getLatitude(), 
                            location.getLongitude()
                        );
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15f));
                    }
                });
        }
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            View view = getCurrentFocus();
            if (view != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                view.clearFocus();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
} 