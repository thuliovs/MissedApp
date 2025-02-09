package com.example.locationalarm;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.Place;
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
        private final ImageView suggestionIcon;

        ViewHolder(View itemView) {
            super(itemView);
            suggestionText = itemView.findViewById(R.id.suggestion_text);
            suggestionIcon = itemView.findViewById(R.id.suggestion_icon);
            
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onSuggestionClick(suggestions.get(position));
                }
            });
        }

        void bind(AutocompletePrediction suggestion) {
            String primaryText = suggestion.getPrimaryText(null).toString();
            String secondaryText = suggestion.getSecondaryText(null).toString();
            String fullAddress = String.format("%s, %s", primaryText, secondaryText);
            suggestionText.setText(fullAddress);

            // Verificar todos os tipos do lugar para encontrar o mais relevante
            List<Place.Type> types = suggestion.getPlaceTypes();
            if (types != null && !types.isEmpty()) {
                for (Place.Type type : types) {
                    int iconRes = getIconForPlaceType(type);
                    if (iconRes != R.drawable.ic_location) {
                        suggestionIcon.setImageResource(iconRes);
                        return;
                    }
                }
                // Se nenhum ícone específico for encontrado, usa o padrão
                suggestionIcon.setImageResource(R.drawable.ic_location);
            }
        }

        private int getIconForPlaceType(Place.Type type) {
            switch (type) {
                // Educação
                case SCHOOL:
                case UNIVERSITY:
                case SECONDARY_SCHOOL:
                case PRIMARY_SCHOOL:
                case BOOK_STORE:
                case LIBRARY:
                case EMBASSY:
                    return R.drawable.ic_school;

                // Alimentação
                case RESTAURANT:
                case CAFE:
                case BAR:
                case BAKERY:
                case FOOD:
                case MEAL_DELIVERY:
                case MEAL_TAKEAWAY:
                case NIGHT_CLUB:
                    return R.drawable.ic_restaurant;

                // Lazer e Turismo
                case PARK:
                case TOURIST_ATTRACTION:
                case MUSEUM:
                case ART_GALLERY:
                case AMUSEMENT_PARK:
                case ZOO:
                case AQUARIUM:
                case BOWLING_ALLEY:
                case CASINO:
                case MOVIE_THEATER:
                case STADIUM:
                case GYM:
                case CAMPGROUND:
                case CHURCH:
                case HINDU_TEMPLE:
                case MOSQUE:
                case SYNAGOGUE:
                case PLACE_OF_WORSHIP:
                    return R.drawable.ic_park;

                // Transporte
                case TRANSIT_STATION:
                case BUS_STATION:
                case TRAIN_STATION:
                case SUBWAY_STATION:
                case AIRPORT:
                case TAXI_STAND:
                case LIGHT_RAIL_STATION:
                case GAS_STATION:
                case PARKING:
                case CAR_RENTAL:
                case CAR_REPAIR:
                case CAR_DEALER:
                case CAR_WASH:
                    return R.drawable.ic_transport;

                // Comércio e Shopping
                case SHOPPING_MALL:
                case SUPERMARKET:
                case CONVENIENCE_STORE:
                case DEPARTMENT_STORE:
                case CLOTHING_STORE:
                case SHOE_STORE:
                case ELECTRONICS_STORE:
                case FURNITURE_STORE:
                case HARDWARE_STORE:
                case HOME_GOODS_STORE:
                case JEWELRY_STORE:
                case PET_STORE:
                case LIQUOR_STORE:
                case BICYCLE_STORE:
                case FLORIST:
                case BEAUTY_SALON:
                case HAIR_CARE:
                case SPA:
                case MOVIE_RENTAL:
                    return R.drawable.ic_shopping;

                // Saúde
                case HOSPITAL:
                case DOCTOR:
                case DENTIST:
                case PHARMACY:
                case PHYSIOTHERAPIST:
                case VETERINARY_CARE:
                    return R.drawable.ic_health;

                // Negócios e Serviços
                case ACCOUNTING:
                case BANK:
                case CITY_HALL:
                case COURTHOUSE:
                case ELECTRICIAN:
                case FIRE_STATION:
                case FUNERAL_HOME:
                case INSURANCE_AGENCY:
                case LAUNDRY:
                case LOCAL_GOVERNMENT_OFFICE:
                case LOCKSMITH:
                case LODGING:
                case MOVING_COMPANY:
                case PAINTER:
                case PLUMBER:
                case POLICE:
                case POST_OFFICE:
                case REAL_ESTATE_AGENCY:
                case ROOFING_CONTRACTOR:
                case STORAGE:
                case TRAVEL_AGENCY:
                    return R.drawable.ic_business;

                // Localização padrão para outros tipos
                default:
                    return R.drawable.ic_location;
            }
        }
    }
} 