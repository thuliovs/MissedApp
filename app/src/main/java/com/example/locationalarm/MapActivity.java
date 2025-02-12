package com.example.locationalarm;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
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
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
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

import android.widget.SeekBar;
import android.widget.TextView;
import android.view.ViewGroup;
import android.graphics.Color;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import java.util.Locale;
import android.graphics.Point;
import com.google.android.gms.maps.CameraUpdate;
import android.widget.Toast;
import com.google.android.gms.common.api.ApiException;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LatLng selectedLocation;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private PlacesClient placesClient;
    private MaterialCardView suggestionsCard;
    private PlaceSuggestionAdapter suggestionAdapter;
    private Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private boolean isUserInput = true; // Nova flag para controlar input do usuário

    private static final double DEFAULT_RADIUS = 1.0; // Valor padrão do raio
    private double selectedRadius = DEFAULT_RADIUS; // Inicializado com valor padrão
    private final double[] RADIUS_OPTIONS = {0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 4.0, 5.0};

    private View instructionCard;
    private ViewGroup cardContent;
    private MaterialButton btnConfirmLocation;
    private MaterialButton btnBack;
    private Circle radiusCircle;
    private Circle currentCircle;
    private ValueAnimator radiusAnimator;
    private Marker currentMarker;

    private int currentStep = 1; // 1: localização, 2: raio, 3: opções de rota

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        
        // Inicializar views primeiro
        instructionCard = findViewById(R.id.instruction_card);
        cardContent = findViewById(R.id.card_content);
        btnConfirmLocation = findViewById(R.id.btn_confirm_location);
        btnBack = findViewById(R.id.btn_back);
        
        // Inicializar FusedLocationClient em background
        new Thread(() -> {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        }).start();
        
        // Inicializar Places
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
        }
        placesClient = Places.createClient(this);
        
        // Setup do mapa e botões
        setupMapFragment();
        setupButtons();

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
        // Configurar botão Confirm
        btnConfirmLocation.setOnClickListener(v -> {
            if (selectedLocation != null) {
                showRadiusSelector();
            }
        });

        // Configurar botão Back
        btnBack.setOnClickListener(v -> {
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_click));
            // Resetar o raio para o valor padrão apenas quando voltar para a tela inicial
            selectedRadius = DEFAULT_RADIUS;
            finish();
            overridePendingTransition(0, R.anim.slide_out_right);
        });

        // Adicionar animações de toque
        View.OnTouchListener touchListener = (v, event) -> {
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
                        v.performClick();
                    }
                    break;
            }
            return true;
        };

        btnConfirmLocation.setOnTouchListener(touchListener);
        btnBack.setOnTouchListener(touchListener);
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
        setupSearchBar();
        animateLoadingIndicator(); // Iniciar nova animação
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
        btnConfirmLocation.setText("confirm");
        
        if (btnConfirmLocation.getVisibility() == View.GONE) {
            // Preparar o botão Confirm vazio
            btnConfirmLocation.setText("");
            LinearLayout.LayoutParams confirmParams = (LinearLayout.LayoutParams) btnConfirmLocation.getLayoutParams();
            confirmParams.weight = 0;
            // Adicionar margem direita ao Confirm
            confirmParams.rightMargin = (int) (5 * getResources().getDisplayMetrics().density); // 8dp
            btnConfirmLocation.setLayoutParams(confirmParams);
            btnConfirmLocation.setAlpha(0f);
            btnConfirmLocation.setVisibility(View.VISIBLE);

            // Primeira animação: expandir o botão vazio
            ValueAnimator weightAnimator = ValueAnimator.ofFloat(0f, 1f);
            weightAnimator.addUpdateListener(animation -> {
                float fraction = (float) animation.getAnimatedValue();
                
                // Animar peso do Cancel
                LinearLayout.LayoutParams cancelParams = (LinearLayout.LayoutParams) btnBack.getLayoutParams();
                cancelParams.weight = 1f - (fraction * 0.5f);
                // Adicionar margem esquerda ao Cancel
                cancelParams.leftMargin = (int) (5 * getResources().getDisplayMetrics().density); // 8dp
                btnBack.setLayoutParams(cancelParams);
                
                // Animar peso do Confirm
                confirmParams.weight = fraction * 0.5f;
                btnConfirmLocation.setLayoutParams(confirmParams);
                
                // Animar alpha do Confirm
                btnConfirmLocation.setAlpha(fraction);

                // Atualizar o texto gradualmente
                if (fraction > 0.8f && btnConfirmLocation.getText().toString().isEmpty()) {
                    btnConfirmLocation.setText("confirm");
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
        
        // Configurar RecyclerView e Adapter
        suggestionAdapter = new PlaceSuggestionAdapter();
        suggestionAdapter.setOnSuggestionClickListener(suggestion -> {
            // Esconder teclado e sugestões
            hideKeyboard();
            hideSuggestions();
            
            // Atualizar o texto da busca
            isUserInput = false;
            searchInput.setText(suggestion.getPrimaryText(null));
            isUserInput = true;
            
            // Buscar detalhes do local e mover a câmera
            FetchPlaceRequest request = FetchPlaceRequest.newInstance(
                suggestion.getPlaceId(),
                Arrays.asList(Place.Field.LAT_LNG)
            );
            
            placesClient.fetchPlace(request)
                .addOnSuccessListener(response -> {
                    Place place = response.getPlace();
                    if (place.getLatLng() != null) {
                        // Limpar mapa e adicionar marcador
                        mMap.clear();
                        selectedLocation = place.getLatLng();
                        mMap.addMarker(new MarkerOptions().position(selectedLocation));
                        
                        // Mover câmera para a localização
                        mMap.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(selectedLocation, 15f),
                            300,
                            null
                        );
                        
                        // Mostrar botão de confirmar
                        showConfirmButton();
                    }
                })
                .addOnFailureListener(exception -> {
                    if (exception instanceof ApiException) {
                        Toast.makeText(
                            MapActivity.this,
                            "Error: " + ((ApiException) exception).getStatusCode(),
                            Toast.LENGTH_SHORT
                        ).show();
                    }
                });
        });
        
        suggestionsRecyclerView.setAdapter(suggestionAdapter);
        suggestionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        // TextWatcher para buscar sugestões enquanto digita
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchInput.hasFocus() && isUserInput) {
                    searchHandler.removeCallbacks(searchRunnable);
                    searchRunnable = () -> {
                        if (s.length() > 0) {
                            suggestionsCard.setVisibility(View.VISIBLE);
                            suggestionsCard.setAlpha(1f);
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

        // Controlar visibilidade das sugestões com o foco
        searchInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && searchInput.getText().length() > 0) {
                suggestionsCard.setVisibility(View.VISIBLE);
                suggestionsCard.setAlpha(1f);
                if (searchInput.getText().length() > 2) {
                    fetchSuggestions(searchInput.getText().toString());
                }
            } else {
                hideKeyboard();
                hideSuggestions();
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
        if (suggestionsCard.getVisibility() == View.VISIBLE) {
            suggestionsCard.animate()
                    .alpha(0f)
                    .setDuration(150)
                    .setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> suggestionsCard.setVisibility(View.GONE))
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
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View view = getCurrentFocus();
        if (view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void animateLoadingIndicator() {
        View loadingOverlay = findViewById(R.id.loading_overlay);
        ImageView loadingIcon = findViewById(R.id.loading_icon);
        View dot1 = findViewById(R.id.dot_1);
        View dot2 = findViewById(R.id.dot_2);
        View dot3 = findViewById(R.id.dot_3);

        // Animação de pulsar do ícone
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(loadingIcon, "scaleX", 0.8f, 1.2f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(loadingIcon, "scaleY", 0.8f, 1.2f);
        
        scaleX.setRepeatMode(ValueAnimator.REVERSE);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatMode(ValueAnimator.REVERSE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);

        AnimatorSet pulseAnimation = new AnimatorSet();
        pulseAnimation.playTogether(scaleX, scaleY);
        pulseAnimation.setDuration(1500);
        pulseAnimation.setInterpolator(new DecelerateInterpolator());
        pulseAnimation.start();

        // Animação dos pontos
        float[] alphas = {0.3f, 0.7f, 1f};
        View[] dots = {dot1, dot2, dot3};
        
        ValueAnimator dotsAnimator = ValueAnimator.ofInt(0, 2);
        dotsAnimator.setDuration(1000);
        dotsAnimator.setRepeatCount(ValueAnimator.INFINITE);
        dotsAnimator.addUpdateListener(animation -> {
            int index = (int) animation.getAnimatedValue();
            for (int i = 0; i < dots.length; i++) {
                dots[i].setAlpha(alphas[(i + index) % 3]);
            }
        });
        dotsAnimator.start();

        // Esconder loading após 3 segundos
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            pulseAnimation.cancel();
            dotsAnimator.cancel();
            
            loadingOverlay.animate()
                    .alpha(0f)
                    .setDuration(800)
                    .setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> {
                        loadingOverlay.setVisibility(View.GONE);
                        
                        // Mostrar elementos da UI
                        View topBar = findViewById(R.id.top_bar_container);
                        View instructionCard = findViewById(R.id.instruction_card);
                        
                        topBar.setVisibility(View.VISIBLE);
                        topBar.animate()
                                .alpha(1f)
                                .setDuration(500)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                        
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

    private void showRadiusSelector() {
        try {
            View instructionText = cardContent.findViewById(R.id.instruction_text);
            View buttonsContainer = cardContent.findViewById(R.id.buttons_container);
            MaterialCardView instructionCard = findViewById(R.id.instruction_card);
            View topBarContainer = findViewById(R.id.top_bar_container);
            
            // Desabilitar TODOS os gestos do mapa
            mMap.getUiSettings().setAllGesturesEnabled(false);
            mMap.setOnMapClickListener(null);
            mMap.setOnMarkerClickListener(marker -> true);
            
            // Calcular o zoom baseado no raio inicial (1.0 km)
            LatLngBounds bounds = calculateBoundsForRadius(selectedLocation, 1.0);
            
            // Configurar padding para mover o centro para cima
            mMap.setPadding(0, 0, 0, 750);
            
            // Animar a câmera para a nova posição com zoom apropriado
            mMap.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds, 100),
                300,
                null
            );
            
            // 1. Animar a saída da barra superior
            topBarContainer.animate()
                    .translationY(-topBarContainer.getHeight())
                    .alpha(0f)
                    .setDuration(300)
                    .setInterpolator(new AccelerateInterpolator());

            // 2. Animar a saída dos elementos atuais
            instructionText.animate()
                    .translationX(-instructionText.getWidth())
                    .alpha(0f)
                    .setDuration(300)
                    .setInterpolator(new AccelerateInterpolator());

            buttonsContainer.animate()
                    .translationX(-buttonsContainer.getWidth())
                    .alpha(0f)
                    .setDuration(300)
                    .setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> {
                        // 3. Depois que os elementos saírem, animar o tamanho do card
                        ValueAnimator heightAnimator = ValueAnimator.ofInt(
                                instructionCard.getHeight(),
                                getResources().getDimensionPixelSize(R.dimen.radius_selector_height)
                        );
                        
                        heightAnimator.addUpdateListener(animation -> {
                            int val = (Integer) animation.getAnimatedValue();
                            ViewGroup.LayoutParams layoutParams = instructionCard.getLayoutParams();
                            layoutParams.height = val;
                            instructionCard.setLayoutParams(layoutParams);
                        });
                        
                        heightAnimator.setDuration(300);
                        heightAnimator.setInterpolator(new DecelerateInterpolator());
                        
                        // 4. Quando o card terminar de crescer, mostrar o novo conteúdo
                        heightAnimator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                inflateAndSetupRadiusSelector();
                            }
                        });
                        
                        heightAnimator.start();
                    }).start();
                
            currentStep = 2;
        } catch (Exception e) {
            e.printStackTrace();
            showRadiusSelectorWithoutAnimation();
        }
    }

    private void showRadiusSelectorWithoutAnimation() {
        cardContent.removeAllViews();
        inflateAndSetupRadiusSelector();
    }

    private void inflateAndSetupRadiusSelector() {
        try {
            // Remover todas as views antes de inflar
            cardContent.removeAllViews();
            
            // Inflar o layout do seletor de raio com attachToRoot como true
            View radiusSelector = getLayoutInflater().inflate(R.layout.radius_selector, null);
            
            // Configurar os parâmetros de layout corretos
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 0); // Remove margens
            radiusSelector.setLayoutParams(params);
            
            // Adicionar a view com os parâmetros corretos
            cardContent.addView(radiusSelector, params);
            
            // Setup do SeekBar e outros elementos...
            SeekBar radiusSeekBar = radiusSelector.findViewById(R.id.radius_seekbar);
            EditText radiusValue = radiusSelector.findViewById(R.id.radius_value);
            MaterialButton btnConfirmRadius = radiusSelector.findViewById(R.id.btn_confirm_radius);
            MaterialButton btnBackRadius = radiusSelector.findViewById(R.id.btn_back_radius);
            
            // Configurar o valor inicial
            int initialProgress = getProgressForRadius(selectedRadius);
            radiusSeekBar.setProgress(initialProgress);
            radiusValue.setText(String.format(Locale.getDefault(), "%.2f", selectedRadius));
            
            // Configurar o EditText
            radiusValue.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    try {
                        double value = Double.parseDouble(radiusValue.getText().toString());
                        if (value > 0 && value <= 5.0) {
                            selectedRadius = value;
                            // Encontrar o valor mais próximo no RADIUS_OPTIONS
                            int closestIndex = 0;
                            double minDiff = Math.abs(RADIUS_OPTIONS[0] - value);
                            
                            for (int i = 1; i < RADIUS_OPTIONS.length; i++) {
                                double diff = Math.abs(RADIUS_OPTIONS[i] - value);
                                if (diff < minDiff) {
                                    minDiff = diff;
                                    closestIndex = i;
                                }
                            }
                            radiusSeekBar.setProgress(closestIndex);
                            updateRadiusCircle();
                        } else {
                            // Valor fora do intervalo, resetar para 1.0
                            updateRadiusText(radiusValue, 1.0);
                            radiusSeekBar.setProgress(initialProgress);
                        }
                    } catch (NumberFormatException e) {
                        // Entrada inválida, resetar para 1.0
                        updateRadiusText(radiusValue, 1.0);
                        radiusSeekBar.setProgress(initialProgress);
                    }
                    hideKeyboard();
                    return true;
                }
                return false;
            });

            // Atualizar o SeekBar quando o foco do EditText é perdido
            radiusValue.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    try {
                        double value = Double.parseDouble(radiusValue.getText().toString());
                        if (value > 0 && value <= 5.0) {
                            selectedRadius = value;
                            updateRadiusCircle();
                        } else {
                            updateRadiusText(radiusValue, 1.0);
                            radiusSeekBar.setProgress(initialProgress);
                        }
                    } catch (NumberFormatException e) {
                        updateRadiusText(radiusValue, 1.0);
                        radiusSeekBar.setProgress(initialProgress);
                    }
                }
            });

            // Atualizar o EditText quando o SeekBar muda
            radiusSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        // Converter a posição do SeekBar para o valor do raio
                        switch (progress) {
                            case 0:
                                selectedRadius = 0.1; // 100m
                                break;
                            case 1:
                                selectedRadius = 0.25; // 250m
                                break;
                            case 2:
                                selectedRadius = 0.5; // 500m
                                break;
                            case 3:
                                selectedRadius = 1.0;
                                break;
                            case 4:
                                selectedRadius = 1.5;
                                break;
                            case 5:
                                selectedRadius = 2.0;
                                break;
                            case 6:
                                selectedRadius = 3.0;
                                break;
                            case 7:
                                selectedRadius = 4.0;
                                break;
                            case 8:
                                selectedRadius = 5.0;
                                break;
                            case 9:
                                selectedRadius = 6.0;
                                break;
                            case 10:
                                selectedRadius = 7.0;
                                break;
                            case 11:
                                selectedRadius = 8.0;
                                break;
                            case 12:
                                selectedRadius = 9.0;
                                break;
                            case 13:
                                selectedRadius = 10.0;
                                break;
                        }
                        
                        // Atualizar o texto e o círculo
                        radiusValue.setText(String.format(Locale.getDefault(), "%.2f", selectedRadius));
                        updateRadiusCircle();
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            btnConfirmRadius.setOnClickListener(v -> {
                v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_click));
                showRouteOptions();
            });

            btnBackRadius.setOnClickListener(v -> restoreOriginalUI());

            // Preparar animação de entrada da direita
            radiusSelector.setTranslationX(cardContent.getWidth());
            radiusSelector.setAlpha(0f);
            
            // Animar entrada da direita para a esquerda
            radiusSelector.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(300)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();

            // Mostrar o círculo inicial com o raio padrão
            updateRadiusCircle();
        } catch (Exception e) {
            e.printStackTrace();
            restoreOriginalUI();
        }
    }

    private void restoreOriginalUI() {
        View currentContent = cardContent.getChildAt(0);
        MaterialCardView instructionCard = findViewById(R.id.instruction_card);
        View topBarContainer = findViewById(R.id.top_bar_container);
        
        // Limpar o foco de qualquer EditText ativo
        View currentFocus = getCurrentFocus();
        if (currentFocus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            currentFocus.clearFocus();
        }
        
        // Limpar o mapa e readicionar apenas o marcador
        mMap.clear();
        if (selectedLocation != null) {
            mMap.addMarker(new MarkerOptions().position(selectedLocation));
        }
        
        // Reabilitar os gestos do mapa
        mMap.getUiSettings().setAllGesturesEnabled(true);
        mMap.setOnMapClickListener(latLng -> {
            mMap.clear();
            selectedLocation = latLng;
            mMap.addMarker(new MarkerOptions().position(latLng));
            showConfirmButton();
        });
        mMap.setOnMarkerClickListener(null);
        
        // Remover o padding ao voltar para a tela de seleção de localização
        mMap.setPadding(0, 0, 0, 0);
        
        if (currentContent != null) {
            // 1. Primeiro animar a saída do conteúdo atual
            currentContent.animate()
                    .translationX(cardContent.getWidth())
                    .alpha(0f)
                    .setDuration(300)
                    .setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> {
                        cardContent.removeAllViews();
                        
                        // 2. Animar o card voltando ao tamanho original
                        ValueAnimator heightAnimator = ValueAnimator.ofInt(
                                instructionCard.getHeight(),
                                getResources().getDimensionPixelSize(R.dimen.instruction_card_height)
                        );
                        
                        heightAnimator.addUpdateListener(animation -> {
                            int val = (Integer) animation.getAnimatedValue();
                            ViewGroup.LayoutParams layoutParams = instructionCard.getLayoutParams();
                            layoutParams.height = val;
                            instructionCard.setLayoutParams(layoutParams);
                        });
                        
                        heightAnimator.setDuration(300);
                        heightAnimator.setInterpolator(new DecelerateInterpolator());
                        
                        // 3. Quando o card terminar de diminuir
                        heightAnimator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                // Preparar a barra superior para a animação
                                topBarContainer.setTranslationY(-topBarContainer.getHeight());
                                topBarContainer.setAlpha(0f);
                                topBarContainer.setVisibility(View.VISIBLE);
                                
                                // Animar a barra superior descendo
                                topBarContainer.animate()
                                        .translationY(0f)
                                        .alpha(1f)
                                        .setDuration(300)
                                        .setInterpolator(new DecelerateInterpolator());
                                
                                // Mostrar o conteúdo original
                                View originalContent = getLayoutInflater().inflate(
                                    R.layout.map_instruction_content, 
                                    cardContent, 
                                    false
                                );
                                cardContent.addView(originalContent);
                                
                                // Configurar os botões novamente
                                btnConfirmLocation = originalContent.findViewById(R.id.btn_confirm_location);
                                btnBack = originalContent.findViewById(R.id.btn_back);
                                setupButtons();
                                
                                if (selectedLocation != null) {
                                    btnConfirmLocation.setVisibility(View.VISIBLE);
                                    btnConfirmLocation.setAlpha(1f);
                                }
                                
                                // Animar entrada do conteúdo original
                                originalContent.setTranslationX(-cardContent.getWidth());
                                originalContent.setAlpha(0f);
                                originalContent.animate()
                                        .translationX(0f)
                                        .alpha(1f)
                                        .setDuration(300)
                                        .setInterpolator(new DecelerateInterpolator())
                                        .start();
                            }
                        });
                        
                        heightAnimator.start();
                    }).start();
        }
        
        // Limpar todas as referências
        currentCircle = null;
        currentMarker = null;
        if (radiusAnimator != null) {
            radiusAnimator.cancel();
            radiusAnimator = null;
        }
        currentStep = 1;
    }

    private void updateRadiusText(EditText editText, double radius) {
        if (radius >= 1.0) {
            editText.setText(String.format(Locale.getDefault(), "%.1f", radius));
        } else {
            editText.setText(String.format(Locale.getDefault(), "%.2f", radius));
        }
    }

    private void updateRadiusCircle() {
        if (mMap != null && selectedLocation != null) {
            // Remover apenas os marcadores, não o círculo
            if (currentCircle == null) {
                mMap.clear();
                // Primeira vez - criar o círculo
                currentCircle = mMap.addCircle(new CircleOptions()
                    .center(selectedLocation)
                    .radius(selectedRadius * 1000)
                    .strokeWidth(2)
                    .strokeColor(getResources().getColor(R.color.accent, getTheme()))
                    .fillColor(getResources().getColor(R.color.accent_tertiary, getTheme()) & 0x40FFFFFF));
            } else {
                // Remover marcador anterior se existir
                if (currentMarker != null) {
                    currentMarker.remove();
                }
                
                // Animar a mudança do raio
                if (radiusAnimator != null) {
                    radiusAnimator.cancel();
                }
                
                float oldRadius = (float) currentCircle.getRadius();
                float newRadius = (float) (selectedRadius * 1000);
                
                radiusAnimator = ValueAnimator.ofFloat(oldRadius, newRadius);
                radiusAnimator.setDuration(300);
                radiusAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
                radiusAnimator.addUpdateListener(animation -> {
                    float animatedRadius = (float) animation.getAnimatedValue();
                    currentCircle.setRadius(animatedRadius);
                });
                radiusAnimator.start();
            }
            
            // Adicionar o novo marcador e guardar a referência
            currentMarker = mMap.addMarker(new MarkerOptions().position(selectedLocation));
            
            // Atualizar o zoom do mapa baseado no novo raio
            LatLngBounds bounds = calculateBoundsForRadius(selectedLocation, selectedRadius);
            mMap.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds, 100),
                300,
                null
            );
        }
    }

    // Método para calcular os bounds baseado no raio
    private LatLngBounds calculateBoundsForRadius(LatLng center, double radiusInKm) {
        // Multiplicar o raio por 2.5 apenas para o cálculo do zoom
        double zoomRadius = radiusInKm * 1.3;
        
        // Converter o raio aumentado de km para graus
        double radiusInDegrees = zoomRadius / 111.32;
        
        // Criar um bound que inclua o círculo completo
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(new LatLng(center.latitude + radiusInDegrees, center.longitude + radiusInDegrees));
        builder.include(new LatLng(center.latitude - radiusInDegrees, center.longitude - radiusInDegrees));
        
        return builder.build();
    }

    private void updateUIState(boolean locationSelected) {
        if (locationSelected) {
            // Primeiro mostrar o botão confirm com animação
            btnConfirmLocation.setVisibility(View.VISIBLE);
            btnConfirmLocation.setAlpha(0f);
            btnConfirmLocation.setScaleX(0.8f);
            btnConfirmLocation.setScaleY(0.8f);
            
            btnConfirmLocation.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();

            // Configurar o clique com a sequência correta de animações
            btnConfirmLocation.setOnClickListener(v -> {
                View instructionText = cardContent.findViewById(R.id.instruction_text);
                View buttonsContainer = cardContent.findViewById(R.id.buttons_container);
                MaterialCardView instructionCard = findViewById(R.id.instruction_card);

                // 1. Primeiro animar a saída dos elementos atuais
                instructionText.animate()
                        .translationX(-instructionText.getWidth())
                        .alpha(0f)
                        .setDuration(300)
                        .setInterpolator(new AccelerateInterpolator());

                buttonsContainer.animate()
                        .translationX(-buttonsContainer.getWidth())
                        .alpha(0f)
                        .setDuration(300)
                        .setInterpolator(new AccelerateInterpolator())
                        .withEndAction(() -> {
                            // 2. Depois que os elementos saírem, animar o tamanho do card
                            ValueAnimator heightAnimator = ValueAnimator.ofInt(
                                    instructionCard.getHeight(),
                                    getResources().getDimensionPixelSize(R.dimen.radius_selector_height)
                            );
                            
                            heightAnimator.addUpdateListener(animation -> {
                                int val = (Integer) animation.getAnimatedValue();
                                ViewGroup.LayoutParams layoutParams = instructionCard.getLayoutParams();
                                layoutParams.height = val;
                                instructionCard.setLayoutParams(layoutParams);
                            });
                            
                            heightAnimator.setDuration(300);
                            heightAnimator.setInterpolator(new DecelerateInterpolator());
                            
                            // 3. Quando o card terminar de crescer, mostrar o novo conteúdo
                            heightAnimator.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    inflateAndSetupRadiusSelector();
                                }
                            });
                            
                            heightAnimator.start();
                        }).start();
            });
        } else {
            btnConfirmLocation.animate()
                    .alpha(0f)
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(200)
                    .setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> btnConfirmLocation.setVisibility(View.GONE))
                    .start();
        }
    }

    // Método auxiliar para converter raio em progresso do SeekBar
    private int getProgressForRadius(double radius) {
        if (radius == 0.1) return 0;
        if (radius == 0.25) return 1;
        if (radius == 0.5) return 2;
        if (radius == 1.0) return 3;
        if (radius == 1.5) return 4;
        if (radius == 2.0) return 5;
        if (radius == 3.0) return 6;
        if (radius == 4.0) return 7;
        if (radius == 5.0) return 8;
        if (radius == 6.0) return 9;
        if (radius == 7.0) return 10;
        if (radius == 8.0) return 11;
        if (radius == 9.0) return 12;
        if (radius == 10.0) return 13;
        return 3; // Valor padrão (1.0 km)
    }

    private double getRadiusForProgress(int progress) {
        switch (progress) {
            case 0:
                return 0.1; // 100m
            case 1:
                return 0.25; // 250m
            case 2:
                return 0.5; // 500m
            case 3:
                return 1.0;
            case 4:
                return 1.5;
            case 5:
                return 2.0;
            case 6:
                return 3.0;
            case 7:
                return 4.0;
            case 8:
                return 5.0;
            case 9:
                return 6.0;
            case 10:
                return 7.0;
            case 11:
                return 8.0;
            case 12:
                return 9.0;
            case 13:
                return 10.0;
            default:
                return 1.0; // Valor padrão
        }
    }

    private void showRouteOptions() {
        View routeOptions = getLayoutInflater().inflate(R.layout.route_options, null);
        
        // Animar saída do conteúdo atual
        cardContent.animate()
                .alpha(0f)
                .translationX(-cardContent.getWidth())
                .setDuration(200)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    cardContent.removeAllViews();
                    cardContent.setAlpha(1f); // Resetar alpha
                    cardContent.setTranslationX(0f); // Resetar translação
                    
                    // Animar mudança de altura do card
                    ValueAnimator heightAnimator = ValueAnimator.ofInt(
                        instructionCard.getHeight(),
                        (int) getResources().getDimension(R.dimen.route_options_height)
                    );
                    
                    heightAnimator.addUpdateListener(animation -> {
                        int val = (int) animation.getAnimatedValue();
                        ViewGroup.LayoutParams layoutParams = instructionCard.getLayoutParams();
                        layoutParams.height = val;
                        instructionCard.setLayoutParams(layoutParams);
                    });
                    
                    heightAnimator.setDuration(300);
                    heightAnimator.setInterpolator(new DecelerateInterpolator());
                    
                    heightAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            // Adicionar novo conteúdo
                            cardContent.addView(routeOptions);
                            
                            // Configurar estado inicial da animação
                            routeOptions.setTranslationX(cardContent.getWidth());
                            routeOptions.setAlpha(0f);
                            routeOptions.setVisibility(View.VISIBLE);
                            
                            // Animar entrada
                            routeOptions.animate()
                                    .translationX(0f)
                                    .alpha(1f)
                                    .setDuration(200)
                                    .setInterpolator(new DecelerateInterpolator())
                                    .start();
                                    
                            // Configurar animações de clique dos botões
                            setupRouteOptionsButtonAnimations(routeOptions);
                        }
                    });
                    
                    heightAnimator.start();
                })
                .start();
        currentStep = 3;
    }

    private void setupRouteOptionsButtonAnimations(View routeOptions) {
        MaterialButton addRouteButton = routeOptions.findViewById(R.id.btn_add_route);
        MaterialButton oneTimeButton = routeOptions.findViewById(R.id.btn_one_time);
        
        View.OnTouchListener touchListener = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(100)
                    .start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || 
                       event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start();
            }
            return false;
        };
        
        addRouteButton.setOnTouchListener(touchListener);
        oneTimeButton.setOnTouchListener(touchListener);
    }

    @Override
    public void onBackPressed() {
        switch (currentStep) {
            case 3:
                // Voltar do conjunto 3 para o 2 (opções de rota -> seletor de raio)
                View radiusSelector = getLayoutInflater().inflate(R.layout.radius_selector, null);
                
                // Configurar o estado anterior do seletor de raio
                SeekBar radiusSeekBar = radiusSelector.findViewById(R.id.radius_seekbar);
                EditText radiusValue = radiusSelector.findViewById(R.id.radius_value);
                
                radiusSeekBar.setProgress(getProgressForRadius(selectedRadius));
                radiusValue.setText(String.format(Locale.getDefault(), "%.2f", selectedRadius));
                
                // Animar saída do conteúdo atual
                cardContent.animate()
                        .alpha(0f)
                        .translationX(cardContent.getWidth())
                        .setDuration(200)
                        .setInterpolator(new AccelerateInterpolator())
                        .withEndAction(() -> {
                            cardContent.removeAllViews();
                            cardContent.setAlpha(1f);
                            cardContent.setTranslationX(0f);
                            
                            // Animar mudança de altura do card
                            ValueAnimator heightAnimator = ValueAnimator.ofInt(
                                instructionCard.getHeight(),
                                (int) getResources().getDimension(R.dimen.radius_selector_height)
                            );
                            
                            heightAnimator.addUpdateListener(animation -> {
                                int val = (int) animation.getAnimatedValue();
                                ViewGroup.LayoutParams layoutParams = instructionCard.getLayoutParams();
                                layoutParams.height = val;
                                instructionCard.setLayoutParams(layoutParams);
                            });
                            
                            heightAnimator.setDuration(300);
                            heightAnimator.setInterpolator(new DecelerateInterpolator());
                            
                            heightAnimator.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    // Adicionar novo conteúdo
                                    cardContent.addView(radiusSelector);
                                    
                                    // Configurar estado inicial da animação
                                    radiusSelector.setTranslationX(-cardContent.getWidth());
                                    radiusSelector.setAlpha(0f);
                                    radiusSelector.setVisibility(View.VISIBLE);
                                    
                                    // Animar entrada
                                    radiusSelector.animate()
                                            .translationX(0f)
                                            .alpha(1f)
                                            .setDuration(200)
                                            .setInterpolator(new DecelerateInterpolator())
                                            .start();
                                            
                                    // Reconfigurar listeners dos botões
                                    setupRadiusSelectorListeners(radiusSelector);
                                }
                            });
                            
                            heightAnimator.start();
                        })
                        .start();
                
                currentStep = 2;
                break;
            
            case 2:
                // Voltar do conjunto 2 para o 1 (seletor de raio -> seleção de local)
                restoreOriginalUI();
                currentStep = 1;
                break;
            
            case 1:
                // Voltar do conjunto 1 para a tela inicial
                super.onBackPressed();
                overridePendingTransition(0, R.anim.slide_out_right);
                break;
        }
    }

    private void setupRadiusSelectorListeners(View radiusSelector) {
        SeekBar radiusSeekBar = radiusSelector.findViewById(R.id.radius_seekbar);
        EditText radiusValue = radiusSelector.findViewById(R.id.radius_value);
        MaterialButton confirmButton = radiusSelector.findViewById(R.id.btn_confirm_radius);
        MaterialButton backButton = radiusSelector.findViewById(R.id.btn_back_radius);
        
        // Configurar listeners do SeekBar
        radiusSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    selectedRadius = getRadiusForProgress(progress);
                    radiusValue.setText(String.format(Locale.getDefault(), "%.2f", selectedRadius));
                    updateRadiusCircle();
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // Configurar listeners dos botões
        confirmButton.setOnClickListener(v -> {
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_click));
            showRouteOptions();
        });
        
        backButton.setOnClickListener(v -> {
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_click));
            restoreOriginalUI();
        });
    }
} 