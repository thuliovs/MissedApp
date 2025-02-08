package com.example.locationalarm;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AnimationUtils;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import com.example.missed.R;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

/**
 * MainActivity - The main screen of the Missed app
 * Displays a list of location-based alarms and provides functionality to add new ones
 */
public class MainActivity extends AppCompatActivity {
    
    private MaterialCardView emptyStateCard;
    private View emptyStateContainer;
    private RecyclerView alarmsRecyclerView;
    private ExtendedFloatingActionButton fabAddAlarm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize views
        initializeViews();
        
        // Set up click listeners
        setupClickListeners();
        
        // Initially show empty state with animation
        showEmptyState(true);
    }

    private void initializeViews() {
        emptyStateCard = findViewById(R.id.empty_state_card);
        emptyStateContainer = findViewById(R.id.empty_state_container);
        alarmsRecyclerView = findViewById(R.id.alarms_recycler_view);
        fabAddAlarm = findViewById(R.id.fab_add_alarm);
    }

    private void setupClickListeners() {
        fabAddAlarm.setOnClickListener(v -> {
            // Animate FAB when clicked
            v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_click));
            
            // Navigate to MapActivity with transition
            Intent intent = new Intent(this, MapActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_right);
        });
    }

    /**
     * Toggle between empty state and list of alarms with smooth animations
     * @param show true to show empty state, false to show list
     */
    private void showEmptyState(boolean show) {
        if (show) {
            emptyStateCard.setVisibility(View.VISIBLE);
            emptyStateCard.setAlpha(0f);
            emptyStateCard.setScaleX(0.8f);
            emptyStateCard.setScaleY(0.8f);
            emptyStateCard.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .start();
            
            alarmsRecyclerView.setVisibility(View.GONE);
        } else {
            emptyStateCard.setVisibility(View.GONE);
            alarmsRecyclerView.setVisibility(View.VISIBLE);
            alarmsRecyclerView.setAlpha(0f);
            alarmsRecyclerView.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start();
        }
    }

    /**
     * Method to be called when alarms are added or removed
     * to update the UI state accordingly
     */
    private void updateUIState(boolean hasAlarms) {
        showEmptyState(!hasAlarms);
        fabAddAlarm.extend(); // Show the full FAB text when no alarms
    }
}