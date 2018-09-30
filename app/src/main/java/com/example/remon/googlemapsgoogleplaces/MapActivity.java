package com.example.remon.googlemapsgoogleplaces;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.remon.googlemapsgoogleplaces.models.PlaceInfo;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.AutocompletePrediction;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;


public class MapActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleApiClient.OnConnectionFailedListener {


    @Override
    public void onMapReady(GoogleMap googleMap) {
        Toast.makeText ( this , "Map is Ready" , Toast.LENGTH_SHORT ).show ( );
        Log.d ( TAG , "onMapReady: map is ready" );
        mMap = googleMap;

        if ( mLocationPermissionsGranted ) {
            getDeviceLocation ( );

            if ( ActivityCompat.checkSelfPermission ( this , Manifest.permission.ACCESS_FINE_LOCATION )
                    != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission ( this ,
                    Manifest.permission.ACCESS_COARSE_LOCATION ) != PackageManager.PERMISSION_GRANTED ) {
                return;
            }
            mMap.setMyLocationEnabled ( true );
            mMap.getUiSettings ( ).setMyLocationButtonEnabled ( false );

            init ( );
        }
    }

    private static final String TAG = "MapActivity";

    private static final String FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
    private static final String COURSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1234;
    private static final float DEFAULT_ZOOM = 15f;
    private static final LatLngBounds LAT_LNG_BOUNDS = new LatLngBounds (
            new LatLng ( -40 , -168 ) , new LatLng ( 71 , 136 ) );
    private static final int PLACE_PICKER_REQUEST = 1;

    // Bind
    @BindView(R.id.input_search)
    AutoCompleteTextView mSearchText;
    @BindView(R.id.ic_gps)
    ImageView mGps;
    @BindView(R.id.place_info)
    ImageView mInfo;
    @BindView(R.id.place_picker)
    ImageView mPlacePicker;

    //vars
    private Boolean mLocationPermissionsGranted = false;
    private GoogleMap mMap;
    private FusedLocationProviderClient mFusedLocationProviderClient;
    private PlaceAutocompleteAdapter mPlaceAutocompleteAdapter;
    private GoogleApiClient mGoogleApiClient;
    private PlaceInfo mPlace;
    private Marker mMarker;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate ( savedInstanceState );
        setContentView ( R.layout.activity_map );
        ButterKnife.bind ( this );

        getLocationPermission ( );

    }

    // initialises search view
    private void init() {
        Log.d ( TAG , "init: initializing" );

        mGoogleApiClient = new GoogleApiClient
                .Builder ( this )
                .addApi ( Places.GEO_DATA_API )
                .addApi ( Places.PLACE_DETECTION_API )
                .enableAutoManage ( this , this )
                .build ( );

        mSearchText.setOnItemClickListener ( mAutocompleteClickListener );

        mPlaceAutocompleteAdapter = new PlaceAutocompleteAdapter ( this , mGoogleApiClient ,
                LAT_LNG_BOUNDS , null );
        mSearchText.setAdapter ( mPlaceAutocompleteAdapter );

        mSearchText.setOnEditorActionListener ( new TextView.OnEditorActionListener ( ) {
            @Override
            public boolean onEditorAction(TextView textView , int actionId , KeyEvent keyEvent) {
                if ( actionId == EditorInfo.IME_ACTION_SEARCH
                        || actionId == EditorInfo.IME_ACTION_DONE
                        || keyEvent.getAction ( ) == KeyEvent.ACTION_DOWN
                        || keyEvent.getAction ( ) == KeyEvent.KEYCODE_ENTER ) {

                    //execute our method for searching
                    geoLocate ( );
                }

                return false;
            }
        } );
        hideSoftKeyboard ( );

    }

    // onClick for place Picker
    @OnClick(R.id.place_picker)
    public void setPlacePicker() {

        PlacePicker.IntentBuilder builder = new PlacePicker.IntentBuilder ( );

        try {
            startActivityForResult ( builder.build ( MapActivity.this ) , PLACE_PICKER_REQUEST );
        } catch (GooglePlayServicesRepairableException e) {
            Log.e ( TAG , "setPlacePicker: GooglePlayServicesRepairableException:" + e.getMessage ( ) );
        } catch (GooglePlayServicesNotAvailableException e) {
            Log.e ( TAG , "setPlacePicker: GooglePlayServicesNotAvailableException:" + e.getMessage ( ) );
        }
    }

    // onClick for Place Info
    @OnClick(R.id.place_info)
    public void setPlaceInfo() {
        Log.d ( TAG , "setPlaceInfo: cliced place" );
        try {
            if ( mMarker.isInfoWindowShown ( ) ) {
                mMarker.hideInfoWindow ( );
            } else {
                Log.d ( TAG , "setPlaceInfo: Place info :" + mPlace.toString ( ) );
                mMarker.showInfoWindow ( );
            }
        } catch (NullPointerException e) {
            Log.e ( TAG , "setPlaceInfo: NullPointerException:" + e.getMessage ( ) );
        }
    }

    // onClick for return to device location
    @OnClick(R.id.ic_gps)
    public void onClickGps() {
        Log.d ( TAG , "onClickGps: clicked gps icon" );
        getDeviceLocation ( );
    }

    //  initialises Place Picker
    protected void onActivityResult(int requestCode , int resultCode , Intent data) {
        if ( requestCode == PLACE_PICKER_REQUEST ) {
            if ( resultCode == RESULT_OK ) {
                Place place = PlacePicker.getPlace ( this , data );

                PendingResult <PlaceBuffer> placeResult = Places.GeoDataApi.getPlaceById (
                        mGoogleApiClient , place.getId ( ) );
                placeResult.setResultCallback ( mUpdatePlaceDetailsCallback );
            }
        }
    }

    // initialises get location
    private void geoLocate() {
        Log.d ( TAG , "geoLocate: geolocating" );

        String searchString = mSearchText.getText ( ).toString ( );

        Geocoder geocoder = new Geocoder ( MapActivity.this );
        List <Address> list = new ArrayList <> ( );
        try {
            list = geocoder.getFromLocationName ( searchString , 1 );
        } catch (IOException e) {
            Log.e ( TAG , "geoLocate: IOException: " + e.getMessage ( ) );
        }

        if ( list.size ( ) > 0 ) {
            Address address = list.get ( 0 );

            Log.d ( TAG , "geoLocate: found a location: " + address.toString ( ) );
            //Toast.makeText(this, address.toString(), Toast.LENGTH_SHORT).show();
            moveCamera ( new LatLng ( address.getLatitude ( ) , address.getLongitude ( ) ) , DEFAULT_ZOOM ,
                    address.getAddressLine ( 0 ) );

        }
    }

    //initialises Device Location
    private void getDeviceLocation() {
        Log.d ( TAG , "getDeviceLocation: getting the devices current location" );

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient ( this );

        try {
            if ( mLocationPermissionsGranted ) {

                final Task location = mFusedLocationProviderClient.getLastLocation ( );
                location.addOnCompleteListener ( new OnCompleteListener ( ) {
                    @Override
                    public void onComplete(@NonNull Task task) {
                        if ( task.isSuccessful ( ) ) {
                            Log.d ( TAG , "onComplete: found location!" );
                            Location currentLocation = ( Location ) task.getResult ( );

                            moveCamera ( new LatLng ( currentLocation.getLatitude ( ) , currentLocation.getLongitude ( ) ) ,
                                    DEFAULT_ZOOM , "My Location" );

                        } else {
                            Log.d ( TAG , "onComplete: current location is null" );
                            Toast.makeText ( MapActivity.this , "unable to get current location" , Toast.LENGTH_SHORT ).show ( );
                        }
                    }
                } );
            }
        } catch (SecurityException e) {
            Log.e ( TAG , "getDeviceLocation: SecurityException: " + e.getMessage ( ) );
        }
    }

    // initialises Camera for place info
    private void moveCamera(LatLng latLng , float zoom , PlaceInfo placeInfo) {
        Log.d ( TAG , "moveCamera: moving the camera to: lat: " + latLng.latitude + ", lng: " + latLng.longitude );
        mMap.moveCamera ( CameraUpdateFactory.newLatLngZoom ( latLng , zoom ) );

        mMap.clear ( );
        mMap.setInfoWindowAdapter ( new CustomInfoAdapter ( MapActivity.this ) );

        if ( placeInfo != null ) {
            try {
                String snippet = "Address: " + placeInfo.getAddress ( ) + "\n" +
                        "Phone Number: " + placeInfo.getNumber ( ) + "\n" +
                        "WebSite: " + placeInfo.getWebSiteUri ( ) + "\n" +
                        "Rating: " + placeInfo.getRating ( ) + "\n";

                MarkerOptions options = new MarkerOptions ( )
                        .position ( latLng )
                        .title ( placeInfo.getName ( ) )
                        .snippet ( snippet );

                mMarker = mMap.addMarker ( options );

            } catch (NullPointerException e) {
                Log.e ( TAG , "moveCamera: NullPointerException " + e.getMessage ( ) );
            }
        } else {
            mMap.addMarker ( new MarkerOptions ( ).position ( latLng ) );
        }

        hideSoftKeyboard ( );
    }

    // initialises Camera for Marker
    private void moveCamera(LatLng latLng , float zoom , String title) {
        Log.d ( TAG , "moveCamera: moving the camera to: lat: " + latLng.latitude + ", lng: " + latLng.longitude );
        mMap.moveCamera ( CameraUpdateFactory.newLatLngZoom ( latLng , zoom ) );

        if ( !title.equals ( "My Location" ) ) {
            MarkerOptions options = new MarkerOptions ( )
                    .position ( latLng )
                    .title ( title );
            mMap.addMarker ( options );
        }
        hideSoftKeyboard ( );
    }

    private void hideSoftKeyboard() {
        this.getWindow ( ).setSoftInputMode ( WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN );
    }

    // initialises Map
    private void initMap() {
        Log.d ( TAG , "initMap: initializing map" );
        SupportMapFragment mapFragment = ( SupportMapFragment ) getSupportFragmentManager ( ).findFragmentById ( R.id.map );

        mapFragment.getMapAsync ( MapActivity.this );
    }

    // get permission
    private void getLocationPermission() {
        Log.d ( TAG , "getLocationPermission: getting location permissions" );
        String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION ,
                Manifest.permission.ACCESS_COARSE_LOCATION};

        if ( ContextCompat.checkSelfPermission ( this.getApplicationContext ( ) ,
                FINE_LOCATION ) == PackageManager.PERMISSION_GRANTED ) {
            if ( ContextCompat.checkSelfPermission ( this.getApplicationContext ( ) ,
                    COURSE_LOCATION ) == PackageManager.PERMISSION_GRANTED ) {
                mLocationPermissionsGranted = true;
                initMap ( );
            } else {
                ActivityCompat.requestPermissions ( this ,
                        permissions ,
                        LOCATION_PERMISSION_REQUEST_CODE );
            }
        } else {
            ActivityCompat.requestPermissions ( this ,
                    permissions ,
                    LOCATION_PERMISSION_REQUEST_CODE );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode , @NonNull String[] permissions , @NonNull int[] grantResults) {
        Log.d ( TAG , "onRequestPermissionsResult: called." );
        mLocationPermissionsGranted = false;

        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST_CODE: {
                if ( grantResults.length > 0 ) {
                    for (int i = 0; i < grantResults.length; i++) {
                        if ( grantResults[i] != PackageManager.PERMISSION_GRANTED ) {
                            mLocationPermissionsGranted = false;
                            Log.d ( TAG , "onRequestPermissionsResult: permission failed" );
                            return;
                        }
                    }
                    Log.d ( TAG , "onRequestPermissionsResult: permission granted" );
                    mLocationPermissionsGranted = true;
                    //initialize our map
                    initMap ( );
                }
            }
        }
    }


    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

     /*
        --------------------------- google places API autocomplete suggestions -----------------
     */

    private AdapterView.OnItemClickListener mAutocompleteClickListener = new AdapterView.OnItemClickListener ( ) {
        @Override
        public void onItemClick(AdapterView <?> parent , View view , int position , long id) {

            hideSoftKeyboard ( );

            final AutocompletePrediction item = mPlaceAutocompleteAdapter.getItem ( position );
            final String placeId = item.getPlaceId ( );

            PendingResult <PlaceBuffer> placeResult = Places.GeoDataApi.getPlaceById (
                    mGoogleApiClient , placeId );
            placeResult.setResultCallback ( mUpdatePlaceDetailsCallback );

        }
    };
    private ResultCallback <PlaceBuffer> mUpdatePlaceDetailsCallback = new ResultCallback <PlaceBuffer> ( ) {
        @Override
        public void onResult(@NonNull PlaceBuffer places) {
            if ( !places.getStatus ( ).isSuccess ( ) ) {
                Log.d ( TAG , "onResult: Place query did not complete successfully: " + places.getStatus ( ).toString ( ) );
                places.release ( );
                return;
            }
            final Place place = places.get ( 0 );

            try {
                mPlace = new PlaceInfo ( );
                mPlace.setName ( place.getName ( ).toString ( ) );
                mPlace.setAddress ( place.getAddress ( ).toString ( ) );
                // mPlace.setAttributions ( place.getAttributions ( ).toString ( ) );
                mPlace.setId ( place.getId ( ) );
                mPlace.setLatLng ( place.getLatLng ( ) );
                mPlace.setNumber ( place.getPhoneNumber ( ).toString ( ) );
                mPlace.setWebSiteUri ( place.getWebsiteUri ( ) );
                mPlace.setRating ( place.getRating ( ) );

                Log.d ( TAG , "onResult: place" + mPlace.toString ( ) );

            } catch (NullPointerException e) {

                Log.e ( TAG , "onResult:NullPointerException " + e.getMessage ( ) );
            }

            moveCamera ( new LatLng ( place.getViewport ( ).getCenter ( ).latitude ,
                    place.getViewport ( ).getCenter ( ).longitude ) , DEFAULT_ZOOM , mPlace );

            places.release ( );
        }
    };

}
