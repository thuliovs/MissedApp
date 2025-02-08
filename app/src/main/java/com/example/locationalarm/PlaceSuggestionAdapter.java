package com.example.locationalarm;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.example.missed.R;
import java.util.ArrayList;
import java.util.List;

public class PlaceSuggestionAdapter extends RecyclerView.Adapter<PlaceSuggestionAdapter.ViewHolder> {
    private List<AutocompletePrediction> suggestions = new ArrayList<>();
    private OnSuggestionClickListener listener;

    public interface OnSuggestionClickListener {
        void onSuggestionClick(AutocompletePrediction suggestion);
    }

    public void setSuggestions(List<AutocompletePrediction> suggestions) {
        this.suggestions = suggestions;
        notifyDataSetChanged();
    }

    public void setOnSuggestionClickListener(OnSuggestionClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_place_suggestion, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AutocompletePrediction suggestion = suggestions.get(position);
        holder.bind(suggestion);
    }

    @Override
    public int getItemCount() {
        return Math.min(suggestions.size(), 5); // Máximo 5 sugestões
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView suggestionText;

        ViewHolder(View itemView) {
            super(itemView);
            suggestionText = itemView.findViewById(R.id.suggestion_text);
            
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onSuggestionClick(suggestions.get(position));
                }
            });
        }

        void bind(AutocompletePrediction suggestion) {
            // Combinar texto primário (rua) com secundário (cidade, país)
            String primaryText = suggestion.getPrimaryText(null).toString();
            String secondaryText = suggestion.getSecondaryText(null).toString();
            
            // Formatar como "Rua Principal, Cidade, País"
            String fullAddress = String.format("%s, %s", primaryText, secondaryText);
            suggestionText.setText(fullAddress);
        }
    }
} 