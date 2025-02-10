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
import android.view.animation.AccelerateInterpolator;
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

import android.widget.SeekBar;
import android.widget.TextView;
import android.view.ViewGroup;
import android.graphics.Color;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import java.util.Locale;

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

    private double selectedRadius = 1.0; // em km
    private final double[] RADIUS_OPTIONS = {0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 4.0, 5.0};

    private View instructionCard;
    private ViewGroup cardContent;
    private MaterialButton btnConfirmLocation;
    private MaterialButton btnBack;
    private Circle radiusCircle;

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

        // Configurar RecyclerView
        suggestionAdapter = new PlaceSuggestionAdapter();
        suggestionsRecyclerView.setAdapter(suggestionAdapter);
        suggestionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Controlar visibilidade das sugestões com o foco
        searchInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
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

        // TextWatcher para buscar sugestões enquanto digita
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchInput.hasFocus() && isUserInput) {
                    searchHandler.removeCallbacks(searchRunnable);
                    searchRunnable = () -> {
                        if (s.length() > 2) {
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
            MaterialCardView instructionCard = (MaterialCardView) findViewById(R.id.instruction_card);
            
            // Medir a altura atual do card
            int startHeight = instructionCard.getHeight();
            
            // Inflar o novo layout sem adicionar à view para medir sua altura
            View radiusSelector = getLayoutInflater().inflate(R.layout.radius_selector, null);
            radiusSelector.measure(
                View.MeasureSpec.makeMeasureSpec(cardContent.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            int targetHeight = radiusSelector.getMeasuredHeight();

            // Animar a altura do card
            ValueAnimator heightAnimator = ValueAnimator.ofInt(startHeight, targetHeight);
            heightAnimator.addUpdateListener(animation -> {
                ViewGroup.LayoutParams params = instructionCard.getLayoutParams();
                params.height = (int) animation.getAnimatedValue();
                instructionCard.setLayoutParams(params);
            });
            heightAnimator.setDuration(300);
            heightAnimator.setInterpolator(new DecelerateInterpolator());

            // Animar saída dos elementos atuais
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
                        inflateAndSetupRadiusSelector();
                        heightAnimator.start();
                    })
                    .start();
                
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
            TextView radiusValue = radiusSelector.findViewById(R.id.radius_value);
            MaterialButton btnConfirmRadius = radiusSelector.findViewById(R.id.btn_confirm_radius);
            MaterialButton btnBackRadius = radiusSelector.findViewById(R.id.btn_back_radius);
            
            // Configurar valor inicial
            updateRadiusText(radiusValue, RADIUS_OPTIONS[2]); // 1.0 km default
            radiusSeekBar.setProgress(2); // Posição do 1.0 km
            
            radiusSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    selectedRadius = RADIUS_OPTIONS[progress];
                    updateRadiusText(radiusValue, selectedRadius);
                    updateRadiusCircle();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            btnConfirmRadius.setOnClickListener(v -> {
                // TODO: Salvar localização e raio selecionados
                finish();
                overridePendingTransition(0, R.anim.slide_out_right);
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
        } catch (Exception e) {
            e.printStackTrace();
            restoreOriginalUI();
        }
    }

    private void restoreOriginalUI() {
        View currentContent = cardContent.getChildAt(0);
        MaterialCardView instructionCard = (MaterialCardView) findViewById(R.id.instruction_card);
        
        if (currentContent != null) {
            // Inflar o layout original sem adicionar à view para medir sua altura
            View originalContent = getLayoutInflater().inflate(R.layout.map_instruction_content, null);
            originalContent.measure(
                View.MeasureSpec.makeMeasureSpec(cardContent.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            int targetHeight = originalContent.getMeasuredHeight();

            // Animar a altura do card
            ValueAnimator heightAnimator = ValueAnimator.ofInt(instructionCard.getHeight(), targetHeight);
            heightAnimator.addUpdateListener(animation -> {
                ViewGroup.LayoutParams params = instructionCard.getLayoutParams();
                params.height = (int) animation.getAnimatedValue();
                instructionCard.setLayoutParams(params);
            });
            heightAnimator.setDuration(300);
            heightAnimator.setInterpolator(new DecelerateInterpolator());

            currentContent.animate()
                    .translationX(cardContent.getWidth())
                    .alpha(0f)
                    .setDuration(300)
                    .setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> {
                        cardContent.removeAllViews();
                        
                        // Inflar e adicionar o layout original
                        originalContent.setTranslationX(-cardContent.getWidth());
                        originalContent.setAlpha(0f);
                        cardContent.addView(originalContent);
                        
                        // Configurar os botões
                        btnConfirmLocation = originalContent.findViewById(R.id.btn_confirm_location);
                        btnBack = originalContent.findViewById(R.id.btn_back);
                        setupButtons();
                        
                        if (selectedLocation != null) {
                            btnConfirmLocation.setVisibility(View.VISIBLE);
                            btnConfirmLocation.setAlpha(1f);
                        }
                        
                        // Animar entrada do conteúdo
                        originalContent.animate()
                                .translationX(0f)
                                .alpha(1f)
                                .setDuration(300)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                            
                        heightAnimator.start();
                    }).start();
        }
    }

    private void updateRadiusText(TextView textView, double radius) {
        if (radius >= 1.0) {
            textView.setText(String.format(Locale.getDefault(), "%.1f km", radius));
        } else {
            textView.setText(String.format(Locale.getDefault(), "%d m", (int)(radius * 1000)));
        }
    }

    private void updateRadiusCircle() {
        if (selectedLocation != null) {
            if (radiusCircle != null) {
                radiusCircle.remove();
            }
            
            CircleOptions circleOptions = new CircleOptions()
                    .center(selectedLocation)
                    .radius(selectedRadius * 1000) // Converter km para metros
                    .strokeWidth(2)
                    .strokeColor(Color.argb(255, 33, 150, 243))
                    .fillColor(Color.argb(50, 33, 150, 243));
            
            radiusCircle = mMap.addCircle(circleOptions);
        }
    }

    private void updateUIState(boolean locationSelected) {
        btnConfirmLocation.setVisibility(locationSelected ? View.VISIBLE : View.GONE);
        btnConfirmLocation.setAlpha(locationSelected ? 1f : 0f);
        // Atualizar outros elementos UI conforme necessário
    }
} 