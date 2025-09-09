package co.za.letsibogo.potholefinder;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import co.za.letsibogo.potholefinder.tflite.PotholeDetector;

public class MainActivity extends AppCompatActivity {

    private VideoView videoView;
    private ProgressBar progressBar;
    private TextView detectionStatus;
    private TextView currentDetections;
    private TextView totalDetections;
    private LinearLayout recentDetectionsContainer;
    private MapView mapView;
    private GoogleMap googleMap;

    private final Handler handler = new Handler();
    private int totalDetectionCount = 0;
    private final Random random = new Random();
    private final List<String> recentDetections = new ArrayList<>();
    private boolean isRunning = false; // Start as false until video is ready
    private boolean isVideoPrepared = false;
    private Uri videoUri;
    private PotholeDetector potholeDetector;
    private MediaMetadataRetriever retriever;
    private java.util.concurrent.ExecutorService exec = java.util.concurrent.Executors.newSingleThreadExecutor();
    private volatile boolean busy = false;  // prevent overlapping inferences


    private final String MAPVIEW_BUNDLE_KEY = "MapViewBundleKey";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- Bind views ---
        videoView = findViewById(R.id.videoView);
        progressBar = findViewById(R.id.video_progress);
        detectionStatus = findViewById(R.id.detection_status);
        currentDetections = findViewById(R.id.current_detections);
        totalDetections = findViewById(R.id.total_detections);
        recentDetectionsContainer = findViewById(R.id.recent_detections_container);
        Button playButton = findViewById(R.id.play_button);
        Button stopButton = findViewById(R.id.stop_button);
        Button restartButton = findViewById(R.id.restart_button);
        Button clearButton = findViewById(R.id.clear_button);
        mapView = findViewById(R.id.map_container);

        // Set initial status
        detectionStatus.setText("Loading video...");

        // --- Setup MapView ---
        Bundle mapViewBundle = null;
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(MAPVIEW_BUNDLE_KEY);
        }
        mapView.onCreate(mapViewBundle);
        mapView.getMapAsync(map -> {
            googleMap = map;
            googleMap.getUiSettings().setZoomControlsEnabled(true);
            googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            LatLng soweto = new LatLng(-26.041317, 28.958024);
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(soweto, 13));
        });

        // Initialize TFLite detector
        potholeDetector = new PotholeDetector(this);

        // --- Setup video ---
        setupVideo();

        // --- Start the update loop ---
        handler.postDelayed(videoRunnable, 500);

        // --- Buttons ---
        playButton.setOnClickListener(v -> playVideo());
        stopButton.setOnClickListener(v -> stopDetection());
        restartButton.setOnClickListener(v -> restartDetection());
        clearButton.setOnClickListener(v -> clearDetections());
    }

    private void setupVideo() {
        videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.demo);
        videoView.setVideoURI(videoUri);

        retriever = new MediaMetadataRetriever();
        retriever.setDataSource(this, videoUri);

        // Hide default media controls
        videoView.setMediaController(null);

        // Set up listeners
        videoView.setOnPreparedListener(mp -> {
            isVideoPrepared = true;
            detectionStatus.setText("Video feed ready - Press Play to start");
            videoView.seekTo(1);
            // Don't auto-start the video, wait for user to press play
        });

        videoView.setOnErrorListener((mp, what, extra) -> {
            detectionStatus.setText("Error loading video");
            Toast.makeText(this, "Error loading video: " + what + ", " + extra, Toast.LENGTH_LONG).show();
            return true;
        });

        // Request focus and make sure it's visible
        videoView.requestFocus();
        videoView.setVisibility(View.VISIBLE);
    }

    private final Runnable videoRunnable = new Runnable() {
        @SuppressLint("SetTextI18n")
        @Override
        public void run() {
            if (isRunning && isVideoPrepared && videoView.isPlaying()) {
                // Update progress bar
                if (videoView.getDuration() > 0) {
                    int progress = (int) (((float) videoView.getCurrentPosition() / videoView.getDuration()) * 100);
                    progressBar.setProgress(progress);
                }

                // Throttle: only start a new inference when previous is done
                if (!busy) {
                    busy = true;
                    final long tsUs = videoView.getCurrentPosition() * 1000L;

                    exec.submit(() -> {
                        Bitmap frameBitmap = null;
                        try {
                            frameBitmap = retriever.getFrameAtTime(tsUs, MediaMetadataRetriever.OPTION_CLOSEST);
                        } catch (Exception ignore) {}

                        boolean potholeDetected = false;
                        if (frameBitmap != null && potholeDetector != null) {
                            // Fast path: boolean
                            potholeDetected = potholeDetector.detect(frameBitmap);
                            // Or, if you want to inspect boxes:
                            // List<PotholeDetector.Detection> dets = potholeDetector.detectWithBoxes(frameBitmap);
                            // potholeDetected = !dets.isEmpty() && any pothole class etc.
                        }

                        boolean finalDetected = potholeDetected;
                        runOnUiThread(() -> {
                            if (finalDetected) {
                                totalDetectionCount++;
                                currentDetections.setText("Current: 1");
                                totalDetections.setText("Total: " + totalDetectionCount);
                                String det = "Pothole #" + totalDetectionCount;
                                recentDetections.add(det);
                                addRecentDetectionView(det);

                                if (googleMap != null) {
                                    double lat = -26.041317 + (random.nextDouble() - 0.5) * 0.01;
                                    double lon = 28.958024 + (random.nextDouble() - 0.5) * 0.01;
                                    googleMap.addMarker(new com.google.android.gms.maps.model.MarkerOptions()
                                            .position(new com.google.android.gms.maps.model.LatLng(lat, lon))
                                            .title(det));
                                }
                                detectionStatus.setText("⚠️ Pothole Detected!");
                            } else {
                                currentDetections.setText("Current: 0");
                                detectionStatus.setText("✅ Road is clear...");
                            }
                            busy = false;
                        });
                    });
                }


            } else if (!isVideoPrepared) {
                detectionStatus.setText("Loading video...");
            }

            // Schedule next frame check every 0.5s
            handler.postDelayed(this, 500);
        }
    };

    private void addRecentDetectionView(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setPadding(8, 8, 8, 8);
        recentDetectionsContainer.addView(tv, 0); // add at top
        if (recentDetectionsContainer.getChildCount() > 5) {
            recentDetectionsContainer.removeViewAt(recentDetectionsContainer.getChildCount() - 1);
        }
    }

    private void playVideo() {
        if (isVideoPrepared) {
            isRunning = true;
            videoView.start();
            detectionStatus.setText("Detection started!");
            Toast.makeText(this, "Detection started!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Video is still loading...", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopDetection() {
        isRunning = false;
        videoView.pause();
        progressBar.setProgress(0); // reset progress bar
        detectionStatus.setText("Detection stopped");
        Toast.makeText(this, "Detection stopped!", Toast.LENGTH_SHORT).show();
    }

    private void restartDetection() {
        if (!isVideoPrepared) {
            // If video isn't prepared yet, set it up again
            setupVideo();
            Toast.makeText(this, "Preparing video...", Toast.LENGTH_SHORT).show();
        } else {
            isRunning = true;
            videoView.seekTo(0);
            videoView.start();
            progressBar.setProgress(0);
            detectionStatus.setText("Detection restarted");
            Toast.makeText(this, "Detection restarted!", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("SetTextI18n")
    private void clearDetections() {
        recentDetectionsContainer.removeAllViews();
        recentDetections.clear();
        totalDetectionCount = 0;
        currentDetections.setText("Current: 0");
        totalDetections.setText("Total: 0");
        progressBar.setProgress(0);
        videoView.seekTo(0);
        Toast.makeText(this, "Detections cleared!", Toast.LENGTH_SHORT).show();
        if (googleMap != null) {
            googleMap.clear();
        }
    }

    // --- MapView lifecycle ---
    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();

        // Resume video if it was playing
        if (isRunning && isVideoPrepared) {
            videoView.start();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    protected void onPause() {
        mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (retriever != null) {
            try {
                retriever.release();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        mapView.onDestroy();
        handler.removeCallbacks(videoRunnable);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Bundle mapViewBundle = outState.getBundle(MAPVIEW_BUNDLE_KEY);
        if (mapViewBundle == null) {
            mapViewBundle = new Bundle();
            outState.putBundle(MAPVIEW_BUNDLE_KEY, mapViewBundle);
        }
        mapView.onSaveInstanceState(mapViewBundle);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}