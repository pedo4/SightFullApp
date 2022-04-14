package com.example.sightful;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.Rot90Op;

import pl.droidsonroids.gif.GifImageView;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    //private static final String serverName = "192.168.1.11";
    private static final String serverName = "192.168.1.237";
    private static final int serverPort = 65432;
    private static final int rotateDegrees = 270;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private Socket socket;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private Button bCapture;
    private Button bPlay;
    private GifImageView LoadingGif;
    private ImageView Logo;
    private String ImagePath;
    private TextToSpeech t1;
    private String predicted;
    private boolean gifOn;

    public void showToast(final String toast)
    {
        runOnUiThread(() -> Toast.makeText(getApplicationContext(), toast, Toast.LENGTH_SHORT).show());
    }

    public void switchVisibility(){
        if (!gifOn) {
            runOnUiThread(() -> Logo.setVisibility(View.GONE));
            runOnUiThread(() -> LoadingGif.setVisibility(View.VISIBLE));
            gifOn = true;
        } else {
            runOnUiThread(() -> Logo.setVisibility(View.VISIBLE));
            runOnUiThread(() -> LoadingGif.setVisibility(View.GONE));
            gifOn = false;
        }
    }

    private void speakTest(String text) {
        t1.speak(text, TextToSpeech.QUEUE_FLUSH, null, "1");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.relativelayout);

        previewView = findViewById(R.id.previewView);
        bCapture = findViewById(R.id.bCapture2);
        bPlay = findViewById(R.id.playRecord2);
        LoadingGif=findViewById(R.id.loading_gif);
        Logo=findViewById(R.id.imageView2);
        LoadingGif.setVisibility(View.GONE);
        gifOn = false;
        bCapture.setOnClickListener(this);
        bPlay.setOnClickListener(this);

        if (!checkCameraPerm()) //check and request for all perms --> camera, read/write on the storage, internet
            requestCameraPerm();
        if (!checkInternetPerm())
            requestInternetPerm();
        if(!CheckReadPerm())
            requestReadPerm();
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                startCameraX(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, getExecutor());
        t1 = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR)
                    t1.setLanguage(Locale.ITALY);
            }
        }); // setting TTS object for the app
    }

    private void requestReadPerm() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},1);
    }

    private boolean CheckReadPerm() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestInternetPerm() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET},1);
    }

    private boolean checkInternetPerm() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPerm() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},1);
    }

    private boolean checkCameraPerm() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;

    }
    Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    @SuppressLint("RestrictedApi")
    private void startCameraX(ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();
        Preview preview = new Preview.Builder()
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Image capture use case
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        //bind to lifecycle:
        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @SuppressLint("RestrictedApi")
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.bCapture2:
                capturePhoto();
                switchVisibility();

                    class Thread1 implements Runnable {
                        @RequiresApi(api = Build.VERSION_CODES.S)
                        public void run() {
                            try {
                                socket = new Socket(serverName, serverPort); // hostname or IP in a configuration file
                                //System.out.print("connected to server...");

                                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                                BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                                if (ImagePath != null) {
                                    Bitmap bitmap = BitmapFactory.decodeFile(ImagePath);
                                    ImageProcessor imageProcessor = new ImageProcessor.Builder() // transfer this in a Factory
                                             .add(new Rot90Op(rotateDegrees / 90))
                                    //         .add(new TransformToGrayscaleOp())
                                    //        .add(new ResizeOp(200, 200, ResizeOp.ResizeMethod.BILINEAR))
                                             .build();
                                    TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
                                    tensorImage.load(bitmap);
                                    tensorImage = imageProcessor.process(tensorImage);
                                    bitmap = tensorImage.getBitmap();
                                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                                    byte[] byteArray = stream.toByteArray();
                                    output.writeInt(byteArray.length); // send the size of bitmap
                                    output.write(byteArray,0,byteArray.length);  //send the byte stream of bitmap
                                    //System.out.println("ready to receive data");
                                    stream.close();


                                    predicted = input.readLine();
                                    if (predicted != null) {
                                        showToast("Prediction made.");
                                        speakTest("Testo trovato.");


                                    }

                                    else {

                                        showToast("Impossible to see text in this photo.");
                                        speakTest("Impossibile trovare il testo nella foto.");
                                    }
                                }
                                else {
                                    // imagePath is null
                                    showToast("Photo not taken.");
                                    speakTest("Foto non ancora scattata.");
                                }
                                socket.close();
                                switchVisibility();
                                return;

                            }  catch (UnknownHostException e) {

                                showToast("Impossible to connect to server.");
                                speakTest("Impossibile connettersi al server.");
                                switchVisibility();
                                return;
                            } catch (IOException e) {

                                showToast("Communication problem with server.");
                                speakTest("Problemi di comunicazione con il server.");
                                switchVisibility();
                                return;
                            }
                        }
                    }

                    Thread thread = new Thread(new Thread1());
                    thread.start();


                    break;
            case R.id.playRecord2:
                if (predicted != null)
                    speakTest(predicted);
                else {
                    speakTest("la foto scattata non contiene del testo riconoscibile");
                }
                break;

      }
    }

    private void capturePhoto() {

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME,"photo");
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");

        ImagePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath();
        ImagePath += "/photo.jpg";

        File imageFile = new File(ImagePath);
        try {
            if (imageFile.delete())
                showToast("Photo deleted.");
        } catch (Exception e){
            e.printStackTrace();
        }
        imageCapture.takePicture(
                new ImageCapture.OutputFileOptions.Builder(
                        getContentResolver(),
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                ).build(),
                getExecutor(),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        showToast("Photo has been saved successfully.");
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                       showToast("Error saving photo: " + exception.getMessage());
                    }
                }
        );

    }
}