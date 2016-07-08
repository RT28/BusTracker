package com.example.riddhi.bustracker;

import android.graphics.Color;
import android.os.AsyncTask;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.GeoApiContext;
import com.google.maps.RoadsApi;
import com.google.maps.model.SnappedPoint;


import java.io.IOException;
import java.io.InputStream;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    GeoApiContext mContext;
    ProgressBar mProgressBar;

    LatLng[] mCapturedLocations;
    List<SnappedPoint> mSnappedPoints;

    AsyncTask<Void, Void, List<SnappedPoint>> mTaskSnapToRoads = new AsyncTask<Void, Void, List<SnappedPoint>>() {

        @Override
        protected void onPreExecute() {
            mProgressBar.setVisibility(View.VISIBLE);
            mProgressBar.setIndeterminate(true);
        }

        @Override
        protected List<SnappedPoint> doInBackground(Void... params) {
            try {
                return snapToRoads(mContext);
            }
            catch (Exception e) {
                toastException(e);
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<SnappedPoint> snappedPoints) {
            mSnappedPoints = snappedPoints;
            mProgressBar.setVisibility(View.INVISIBLE);

            com.google.android.gms.maps.model.LatLng[] mapPoints = new com.google.android.gms.maps.model.LatLng[mSnappedPoints.size()];

            int i = 0;
            LatLngBounds.Builder bounds = new LatLngBounds.Builder();
            for(SnappedPoint point: mSnappedPoints) {
                mapPoints[i] = new com.google.android.gms.maps.model.LatLng(point.location.lat, point.location.lng);
                bounds.include(mapPoints[i]);
                i += 1;
            }

            mMap.addPolyline(new PolylineOptions().add(mapPoints).color(Color.BLUE));
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 0));
        }
    };

    private List<SnappedPoint> snapToRoads(GeoApiContext context) throws Exception {
        List<SnappedPoint> snappedPoints = new ArrayList<>();
        SnappedPoint[] points = RoadsApi.snapToRoads(context, true, mCapturedLocations).await();

        for(SnappedPoint point: points) {
            snappedPoints.add(point);
        }
        return snappedPoints;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
        bindAutoCompleteListToSearchTextView();
        mContext = new GeoApiContext().setApiKey(getString(R.string.google_maps_key));
    }

    private void bindAutoCompleteListToSearchTextView() {
        String[] busRoutes = {"393 - Tilak Nagar to Ghatkopar Station E", "393 - Ghatkopar Station E to Tilak Nagar"};
        AutoCompleteTextView autoCompleteTextView = (AutoCompleteTextView) findViewById(R.id.txtAutoCompleteBusSearch);

        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, busRoutes);
        autoCompleteTextView.setThreshold(2);
        autoCompleteTextView.setAdapter(arrayAdapter);

        autoCompleteTextView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                try {
                    mMap.clear();
                    String routeContent = loadFileFromAssets();
                    JSONArray routes = new JSONArray(routeContent);
                    addBusStopMarkers(routes);

                    /*JSONObject start = routes.getJSONObject(0);
                    com.google.android.gms.maps.model.LatLng startMarker = new com.google.android.gms.maps.model.LatLng(start.getDouble("latitude"), start.getDouble("longitude"));
                    mMap.addMarker(new MarkerOptions().position(startMarker).title(start.getString("name")));
                    //Move camera to start marker

                    CameraPosition cameraPosition = new CameraPosition.Builder()
                            .target(startMarker)
                            .zoom(16)
                            .build();
                    mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                    mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);*/


                    mTaskSnapToRoads.execute();
                }
                catch (Exception e){
                    e.printStackTrace();
                    Log.e("onItemClick", e.toString());
                }
            }
        });
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        try {
            mMap = googleMap;
            mMap.clear();
        }
        catch (Exception e) {
            e.printStackTrace();
            Log.e("onMapReady", e.toString());
        }
    }

    private void addBusStopMarkers(JSONArray routes) {
        mCapturedLocations = new LatLng[routes.length()];
        try {
            for (byte count = 0; count < routes.length(); count++) {
                JSONObject markerDetails = routes.getJSONObject(count);
                mCapturedLocations[count] = new LatLng(markerDetails.getDouble("latitude"), markerDetails.getDouble(("longitude")));
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            Log.e("addBusStopMarkers", e.toString());
        }
    }

    private String loadFileFromAssets() {
        String json = null;

        try {
            InputStream is = getAssets().open("393BusRouteTilakNagarToGhatkopar.json");
            int size =  is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        }
        catch (IOException ex) {
            ex.printStackTrace();
            Log.e("loadFileNameAssets", ex.toString());
        }

        return json;
    }

    private void toastException(final Exception ex){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
            Toast.makeText(getApplicationContext(), ex.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("ERROR", ex.getMessage());
            }
        });
    }

}
