package com.example.incidentmap;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap map;

    private EditText searchEditText;
    private Button searchButton;
    private Button listButton;
    private Spinner typeSpinner;

    private SharedPreferences preferences;

    private static final String PREFS_NAME = "incidents_storage";
    private static final String INCIDENTS_KEY = "incidents";
    private static final int LOCATION_PERMISSION_CODE = 100;

    private final ArrayList<Incident> incidents = new ArrayList<>();

    private final String[] incidentTypes = {
            "ДТП",
            "Ремонт дороги",
            "Пробка",
            "Перекрытие",
            "Опасный участок"
    };

    private static class Incident {
        long id;
        String type;
        double lat;
        double lng;
        Marker marker;
        Circle circle;

        Incident(long id, String type, double lat, double lng) {
            this.id = id;
            this.type = type;
            this.lat = lat;
            this.lng = lng;
        }

        String toStorageString() {
            return id + "|" + type + "|" + lat + "|" + lng;
        }

        static Incident fromStorageString(String value) {
            try {
                String[] parts = value.split("\\|");

                return new Incident(
                        Long.parseLong(parts[0]),
                        parts[1],
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3])
                );
            } catch (Exception e) {
                return null;
            }
        }

        String getTitle() {
            return type + " — " + String.format(Locale.getDefault(), "%.5f, %.5f", lat, lng);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        searchEditText = findViewById(R.id.searchEditText);
        searchButton = findViewById(R.id.searchButton);
        listButton = findViewById(R.id.listButton);
        typeSpinner = findViewById(R.id.typeSpinner);

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                incidentTypes
        ) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(Color.WHITE);
                view.setTextSize(16);
                view.setPadding(18, 0, 18, 0);
                return view;
            }

            @Override
            public android.view.View getDropDownView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(Color.WHITE);
                view.setTextSize(16);
                view.setBackgroundColor(Color.rgb(22, 27, 34));
                view.setPadding(22, 18, 22, 18);
                return view;
            }
        };

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(adapter);

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.mapFragment);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        searchButton.setOnClickListener(v -> searchPlace());
        listButton.setOnClickListener(v -> showIncidentsWindow());

        loadIncidents();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;

        setDarkMapStyle();

        LatLng defaultPoint = new LatLng(55.7558, 37.6173);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultPoint, 11));

        requestLocationPermission();
        drawSavedIncidents();

        map.setOnMapClickListener(latLng -> {
            String selectedType = typeSpinner.getSelectedItem().toString();

            Incident incident = new Incident(
                    System.currentTimeMillis(),
                    selectedType,
                    latLng.latitude,
                    latLng.longitude
            );

            incidents.add(incident);
            saveIncidents();
            addIncidentToMap(incident);

            Toast.makeText(
                    this,
                    "Добавлено: " + selectedType,
                    Toast.LENGTH_SHORT
            ).show();
        });

        map.setOnMarkerClickListener(marker -> {
            Object tag = marker.getTag();

            if (tag instanceof Incident) {
                Incident incident = (Incident) tag;
                showIncidentActions(incident);
                return true;
            }

            return false;
        });
    }

    private void setDarkMapStyle() {
        if (map == null) return;

        String darkStyle = "[" +
                "{\"elementType\":\"geometry\",\"stylers\":[{\"color\":\"#1d2c4d\"}]}," +
                "{\"elementType\":\"labels.text.fill\",\"stylers\":[{\"color\":\"#8ec3b9\"}]}," +
                "{\"elementType\":\"labels.text.stroke\",\"stylers\":[{\"color\":\"#1a3646\"}]}," +
                "{\"featureType\":\"administrative.country\",\"elementType\":\"geometry.stroke\",\"stylers\":[{\"color\":\"#4b6878\"}]}," +
                "{\"featureType\":\"landscape.natural\",\"elementType\":\"geometry\",\"stylers\":[{\"color\":\"#023e58\"}]}," +
                "{\"featureType\":\"poi\",\"elementType\":\"geometry\",\"stylers\":[{\"color\":\"#283d6a\"}]}," +
                "{\"featureType\":\"poi\",\"elementType\":\"labels.text.fill\",\"stylers\":[{\"color\":\"#6f9ba5\"}]}," +
                "{\"featureType\":\"road\",\"elementType\":\"geometry\",\"stylers\":[{\"color\":\"#304a7d\"}]}," +
                "{\"featureType\":\"road\",\"elementType\":\"labels.text.fill\",\"stylers\":[{\"color\":\"#98a5be\"}]}," +
                "{\"featureType\":\"road\",\"elementType\":\"labels.text.stroke\",\"stylers\":[{\"color\":\"#1d2c4d\"}]}," +
                "{\"featureType\":\"road.highway\",\"elementType\":\"geometry\",\"stylers\":[{\"color\":\"#2c6675\"}]}," +
                "{\"featureType\":\"road.highway\",\"elementType\":\"geometry.stroke\",\"stylers\":[{\"color\":\"#255763\"}]}," +
                "{\"featureType\":\"transit\",\"elementType\":\"labels.text.fill\",\"stylers\":[{\"color\":\"#98a5be\"}]}," +
                "{\"featureType\":\"water\",\"elementType\":\"geometry\",\"stylers\":[{\"color\":\"#0e1626\"}]}," +
                "{\"featureType\":\"water\",\"elementType\":\"labels.text.fill\",\"stylers\":[{\"color\":\"#4e6d70\"}]}" +
                "]";

        map.setMapStyle(new MapStyleOptions(darkStyle));
    }

    private void requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            if (map != null) {
                map.setMyLocationEnabled(true);
                map.getUiSettings().setMyLocationButtonEnabled(true);
            }

        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestLocationPermission();
            } else {
                Toast.makeText(
                        this,
                        "Разрешение на геолокацию не выдано",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    private void searchPlace() {
        String query = searchEditText.getText().toString().trim();

        if (query.isEmpty()) {
            Toast.makeText(this, "Введите место для поиска", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocationName(query, 1);

            if (addresses == null || addresses.isEmpty()) {
                Toast.makeText(this, "Место не найдено", Toast.LENGTH_SHORT).show();
                return;
            }

            Address address = addresses.get(0);

            LatLng point = new LatLng(
                    address.getLatitude(),
                    address.getLongitude()
            );

            map.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 15));

            map.addMarker(new MarkerOptions()
                    .position(point)
                    .title("Результат поиска")
                    .snippet(query)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));

        } catch (Exception e) {
            Toast.makeText(this, "Ошибка поиска", Toast.LENGTH_SHORT).show();
        }
    }

    private void addIncidentToMap(Incident incident) {
        if (map == null) return;

        LatLng point = new LatLng(incident.lat, incident.lng);

        Marker marker = map.addMarker(new MarkerOptions()
                .position(point)
                .title(incident.type)
                .snippet("Нажмите для действий")
                .icon(BitmapDescriptorFactory.defaultMarker(getMarkerColor(incident.type))));

        Circle circle = map.addCircle(new CircleOptions()
                .center(point)
                .radius(40)
                .strokeColor(getIncidentColor(incident.type))
                .fillColor(getIncidentFillColor(incident.type))
                .strokeWidth(3));

        incident.marker = marker;
        incident.circle = circle;

        if (marker != null) {
            marker.setTag(incident);
        }
    }

    private int getIncidentColor(String type) {
        if (type.equals("ДТП")) {
            return Color.RED;
        } else if (type.equals("Ремонт дороги")) {
            return Color.rgb(255, 152, 0);
        } else if (type.equals("Пробка")) {
            return Color.YELLOW;
        } else if (type.equals("Перекрытие")) {
            return Color.MAGENTA;
        } else {
            return Color.CYAN;
        }
    }

    private int getIncidentFillColor(String type) {
        if (type.equals("ДТП")) {
            return 0x33FF0000;
        } else if (type.equals("Ремонт дороги")) {
            return 0x33FF9800;
        } else if (type.equals("Пробка")) {
            return 0x33FFFF00;
        } else if (type.equals("Перекрытие")) {
            return 0x33FF00FF;
        } else {
            return 0x3300FFFF;
        }
    }

    private float getMarkerColor(String type) {
        if (type.equals("ДТП")) {
            return BitmapDescriptorFactory.HUE_RED;
        } else if (type.equals("Ремонт дороги")) {
            return BitmapDescriptorFactory.HUE_ORANGE;
        } else if (type.equals("Пробка")) {
            return BitmapDescriptorFactory.HUE_YELLOW;
        } else if (type.equals("Перекрытие")) {
            return BitmapDescriptorFactory.HUE_VIOLET;
        } else {
            return BitmapDescriptorFactory.HUE_CYAN;
        }
    }

    private void showIncidentActions(Incident incident) {
        new AlertDialog.Builder(this)
                .setTitle(incident.type)
                .setMessage("Координаты:\n" + incident.lat + ", " + incident.lng)
                .setPositiveButton("Открыть на карте", (dialog, which) -> {
                    LatLng point = new LatLng(incident.lat, incident.lng);
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 17));

                    if (incident.marker != null) {
                        incident.marker.showInfoWindow();
                    }
                })
                .setNegativeButton("Удалить", (dialog, which) -> deleteIncident(incident))
                .setNeutralButton("Отмена", null)
                .show();
    }

    private void showIncidentsWindow() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 36, 24, 24);
        root.setBackgroundColor(Color.rgb(13, 17, 23));

        TextView title = new TextView(this);
        title.setText("Список происшествий");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, 20);

        Button backButton = new Button(this);
        backButton.setText("Назад");
        backButton.setTextColor(Color.WHITE);
        backButton.setBackgroundColor(Color.rgb(48, 54, 61));
        backButton.setOnClickListener(v -> dialog.dismiss());

        ScrollView scrollView = new ScrollView(this);

        LinearLayout listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);

        if (incidents.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("Происшествий пока нет");
            emptyText.setTextColor(Color.rgb(139, 148, 158));
            emptyText.setTextSize(18);
            emptyText.setGravity(Gravity.CENTER);
            emptyText.setPadding(0, 40, 0, 40);

            listLayout.addView(emptyText);
        } else {
            for (Incident incident : incidents) {
                LinearLayout card = createIncidentCard(incident, dialog);
                listLayout.addView(card);
            }
        }

        scrollView.addView(listLayout);

        root.addView(title);
        root.addView(backButton);

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        );

        scrollParams.setMargins(0, 20, 0, 0);
        root.addView(scrollView, scrollParams);

        dialog.setContentView(root);

        Window window = dialog.getWindow();

        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
            );
        }

        dialog.setOnShowListener(d -> {
            Window dialogWindow = dialog.getWindow();

            if (dialogWindow != null) {
                dialogWindow.setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT
                );
            }
        });

        dialog.show();
    }

    private LinearLayout createIncidentCard(Incident incident, Dialog dialog) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(22, 18, 22, 18);
        card.setBackgroundColor(Color.rgb(22, 27, 34));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        cardParams.setMargins(0, 0, 0, 18);
        card.setLayoutParams(cardParams);

        TextView typeText = new TextView(this);
        typeText.setText(incident.type);
        typeText.setTextColor(Color.WHITE);
        typeText.setTextSize(20);
        typeText.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView coordsText = new TextView(this);
        coordsText.setText(String.format(
                Locale.getDefault(),
                "%.5f, %.5f",
                incident.lat,
                incident.lng
        ));
        coordsText.setTextColor(Color.rgb(139, 148, 158));
        coordsText.setTextSize(15);
        coordsText.setPadding(0, 6, 0, 12);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        Button openButton = new Button(this);
        openButton.setText("Открыть");
        openButton.setTextColor(Color.WHITE);
        openButton.setBackgroundColor(Color.rgb(31, 111, 235));

        Button deleteButton = new Button(this);
        deleteButton.setText("Удалить");
        deleteButton.setTextColor(Color.WHITE);
        deleteButton.setBackgroundColor(Color.rgb(218, 54, 51));

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        );

        buttonParams.setMargins(4, 0, 4, 0);

        openButton.setLayoutParams(buttonParams);
        deleteButton.setLayoutParams(buttonParams);

        openButton.setOnClickListener(v -> {
            LatLng point = new LatLng(incident.lat, incident.lng);

            map.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 17));

            if (incident.marker != null) {
                incident.marker.showInfoWindow();
            }

            dialog.dismiss();
        });

        deleteButton.setOnClickListener(v -> {
            deleteIncident(incident);
            dialog.dismiss();
            showIncidentsWindow();
        });

        buttons.addView(openButton);
        buttons.addView(deleteButton);

        card.addView(typeText);
        card.addView(coordsText);
        card.addView(buttons);

        return card;
    }

    private void deleteIncident(Incident incident) {
        if (incident.marker != null) {
            incident.marker.remove();
        }

        if (incident.circle != null) {
            incident.circle.remove();
        }

        incidents.remove(incident);
        saveIncidents();

        Toast.makeText(this, "Происшествие удалено", Toast.LENGTH_SHORT).show();
    }

    private void saveIncidents() {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < incidents.size(); i++) {
            builder.append(incidents.get(i).toStorageString());

            if (i < incidents.size() - 1) {
                builder.append(";");
            }
        }

        preferences.edit()
                .putString(INCIDENTS_KEY, builder.toString())
                .apply();
    }

    private void loadIncidents() {
        incidents.clear();

        String saved = preferences.getString(INCIDENTS_KEY, "");

        if (saved.isEmpty()) return;

        String[] parts = saved.split(";");

        for (String part : parts) {
            Incident incident = Incident.fromStorageString(part);

            if (incident != null) {
                incidents.add(incident);
            }
        }
    }

    private void drawSavedIncidents() {
        for (Incident incident : incidents) {
            addIncidentToMap(incident);
        }
    }
}