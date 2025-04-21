package com.kqg.chair;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_RECORD_PERMISSION = 100;
    private static final String DEVICE_NAME = "Wheelchair";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private TextView textView, bluetoothStatusLabel;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch motorSwitch, languageSwitch;
    private RadioGroup radioGroup;
    private RadioButton radioTilt, radioSound, radioManual;
    private LinearLayout manualControls;
    private SpeechRecognizer speechRecognizer;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private Handler handler;
    private Runnable bluetoothCheckRunnable;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private boolean isArabic = false;
    private boolean isMoving = false;
    private SeekBar tiltThresholdSlider;
    private TextView tiltThresholdLabel;
    private float tiltThreshold = 3.0f;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        checkAudioPermission();
        setupBluetooth();
        setupSensors();
        setupListeners();

        handler = new Handler();
        setupBluetoothCheckRunnable();
        handler.post(bluetoothCheckRunnable);
    }

    private void initializeViews() {
        textView = findViewById(R.id.textView);
        motorSwitch = findViewById(R.id.switch_motor);
        bluetoothStatusLabel = findViewById(R.id.bluetoothStatusLabel);
        languageSwitch = findViewById(R.id.languageSwitch);
        radioGroup = findViewById(R.id.radioGroup);
        radioSound = findViewById(R.id.radioSound);
        radioTilt = findViewById(R.id.radioTilt);
        radioManual = findViewById(R.id.radioManual);
        manualControls = findViewById(R.id.manualControls);
        tiltThresholdSlider = findViewById(R.id.tiltThresholdSlider);
    }

    private void checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_PERMISSION);
        }
    }

    private void setupBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            textView.setText(isArabic ? "البلوتوث غير مدعوم" : "Bluetooth not supported");
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        connectToPairedDevice();
    }

    @SuppressLint("MissingPermission")
    private void connectToPairedDevice() {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (DEVICE_NAME.equals(device.getName())) {
                connectToDevice(device);
                break;
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice(BluetoothDevice device) {
        if (bluetoothSocket != null && bluetoothSocket.isConnected()) {
            return;
        }

        new Thread(() -> {
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                bluetoothSocket.connect();
                outputStream = bluetoothSocket.getOutputStream();
                runOnUiThread(() -> bluetoothStatusLabel.setText(isArabic ? "متصل" : "Connected"));
            } catch (IOException e) {
                Log.e(TAG, "Error connecting to device", e);
                closeSocket();
                handler.postDelayed(() -> connectToDevice(device), 3000);
            }
        }).start();
    }

    private void closeSocket() {
        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
                bluetoothSocket = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing Bluetooth socket", e);
        }
    }

    private void setupSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    private void setupListeners() {
        motorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                sendCommand("ENABLE");
                textView.setText(isArabic ? "تم تفعيل المحركات" : "Motors Enabled");
                if (radioSound.isChecked()) startSpeechRecognition();
            } else {
                sendCommand("DISABLE");
                textView.setText(isArabic ? "تم تعطيل المحركات" : "Motors Disabled");
                destroySpeechRecognizer();
            }

            updateControlsVisibility();
        });

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            updateControlsVisibility();
            if (motorSwitch.isChecked()) {
                if (checkedId == R.id.radioSound) {
                    startSpeechRecognition();
                } else {
                    destroySpeechRecognizer();
                }
            }
        });

        setupManualControlButtons();

        languageSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isArabic = isChecked;
            updateLanguageUI();
            if (isChecked && radioGroup.getCheckedRadioButtonId() == R.id.radioSound && motorSwitch.isChecked()) {
                destroySpeechRecognizer();
                startSpeechRecognition();
            }
        });

        tiltThresholdSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                tiltThreshold = 1.0f + progress;

                tiltThresholdLabel.setText(String.format(isArabic ? "الحساسية: %.1f" : "Sensitivity: %.1f", tiltThreshold));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void updateControlsVisibility() {
        boolean isMotorEnabled = motorSwitch.isChecked();
        int selectedRadioId = radioGroup.getCheckedRadioButtonId();

        LinearLayout tiltControls = findViewById(R.id.tiltControls);
        manualControls = findViewById(R.id.manualControls);
        tiltThresholdSlider = findViewById(R.id.tiltThresholdSlider);
        tiltThresholdLabel = findViewById(R.id.tiltThresholdLabel);

        if (tiltControls == null || manualControls == null || tiltThresholdSlider == null || tiltThresholdLabel == null) {
            Log.e(TAG, "Some views (tiltControls, manualControls, tiltThresholdSlider, tiltThresholdLabel) are not initialized properly.");
            return;
        }

        if (isMotorEnabled && selectedRadioId == R.id.radioTilt) {
            tiltControls.setVisibility(View.VISIBLE);
        } else {
            tiltControls.setVisibility(View.GONE);
        }

        if (isMotorEnabled && selectedRadioId == R.id.radioManual) {
            manualControls.setVisibility(View.VISIBLE);
        } else {
            manualControls.setVisibility(View.GONE);
        }
    }

    private void updateLanguageUI() {
        if (textView != null) textView.setText(isArabic ? "مرحبا" : "Hello There");
        if (motorSwitch != null) motorSwitch.setText(isArabic ? "تفعيل المحركات" : "Enable Motors");
        if (bluetoothStatusLabel != null) bluetoothStatusLabel.setText(isArabic ? "حالة البلوتوث" : "Bluetooth Status");
        if (languageSwitch != null) languageSwitch.setText(isArabic ? "عربي" : "English");
        if (radioSound != null) radioSound.setText(isArabic ? "وضع الصوت" : "Sound Mode");
        if (radioTilt != null) radioTilt.setText(isArabic ? "وضع الامالة" : "Tilt Mode");
        if (radioManual != null) radioManual.setText(isArabic ? "الوضع اليدوي" : "Manual Mode");
        if (tiltThresholdLabel != null) tiltThresholdLabel.setText(isArabic ? "الحساسية:" : "Sensitivity:");
    }

    private void setupManualControlButtons() {
        findViewById(R.id.buttonForward).setOnTouchListener(createButtonTouchListener("forward"));
        findViewById(R.id.buttonBackward).setOnTouchListener(createButtonTouchListener("backward"));
        findViewById(R.id.buttonRight).setOnTouchListener(createButtonTouchListener("right"));
        findViewById(R.id.buttonLeft).setOnTouchListener(createButtonTouchListener("left"));
    }

    @SuppressLint("ClickableViewAccessibility")
    private View.OnTouchListener createButtonTouchListener(String command) {
        return (v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    processCommand(command);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    processCommand("stop");
                    return true;
            }
            return false;
        };
    }

    private void setupBluetoothCheckRunnable() {
        bluetoothCheckRunnable = new Runnable() {
            @SuppressLint("MissingPermission")
            @Override
            public void run() {
                boolean isConnected = bluetoothSocket != null && bluetoothSocket.isConnected();

                runOnUiThread(() -> {
                    bluetoothStatusLabel.setText(isConnected ? (isArabic ? "متصل" : "Connected") : (isArabic ? "غير متصل" : "Not Connected"));
                    motorSwitch.setEnabled(isConnected);
                });

                if (!isConnected) {
                    reconnectToDevice();
                }

                handler.postDelayed(this, 3000);
            }
        };
    }

    private void reconnectToDevice() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (DEVICE_NAME.equals(device.getName())) {
                connectToDevice(device);
                return;
            }
        }
        runOnUiThread(() -> bluetoothStatusLabel.setText(isArabic ? "لم يتم العثور على الجهاز" : "Device Not Found"));
    }

    private void startSpeechRecognition() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new SpeechRecognitionListener());
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, isArabic ? "ar-AR" : "en-US");
        speechRecognizer.startListening(intent);
    }

    private void destroySpeechRecognizer() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }

    private void processCommand(String command) {
    command = command.toLowerCase(Locale.ROOT);
    String commandToSend = null;

    if (command.contains("forward") || command.contains("ahead") ||
        command.contains("امام") || command.contains("للامام")) {
        commandToSend = "FORWARD";
        textView.setText(isArabic ? "يتم التحرك للأمام" : "Moving Forward");
    } else if (command.contains("backward") || command.contains("back") ||
               command.contains("خلف") || command.contains("للخلف")) {
        commandToSend = "BACKWARD";
        textView.setText(isArabic ? "يتم التحرك للخلف" : "Moving Backward");
    } else if (command.contains("left") || command.contains("يسار") ||
               command.contains("لليسار") || command.contains("يسارا")) {
        commandToSend = "LEFT";
        textView.setText(isArabic ? "يتم التحرك لليسار" : "Turning Left");
    } else if (command.contains("right") || command.contains("يمين") ||
               command.contains("لليمين") || command.contains("يمينا")) {
        commandToSend = "RIGHT";
        textView.setText(isArabic ? "يتم التحرك لليمين" : "Turning Right");
    } else if (command.contains("stop") || command.contains("توقف")) {
        commandToSend = "STOP";
        textView.setText(isArabic ? "توقف" : "Stopped");
    } else {
        textView.setText(isArabic ? "غير متعرف به" : "Unrecognized Command");
    }

    if (commandToSend != null && isBluetoothConnected()) {
        sendCommand(commandToSend);
    } else if (!isBluetoothConnected()) {
        Log.e(TAG, "Bluetooth socket not connected");
        runOnUiThread(() -> bluetoothStatusLabel.setText(isArabic ? "حالة البلوتوث: غير متصل" : "Bluetooth Status: Not Connected"));
    }
}

    private boolean isBluetoothConnected() {
        return bluetoothSocket != null && bluetoothSocket.isConnected();
    }

    private void sendCommand(String command) {
        if (!isBluetoothConnected()) {
            Log.e(TAG, "Bluetooth socket not connected. Command ignored.");
            runOnUiThread(() -> bluetoothStatusLabel.setText(isArabic ? "حالة البلوتوث: غير متصل" : "Bluetooth Status: Not Connected"));
            return;
        }

        try {
            outputStream.write(command.getBytes());
            Log.d(TAG, "Command sent: " + command);
        } catch (IOException ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    private String lastTiltCommand = null;

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.values == null || event.values.length < 2) {
            Log.e(TAG, "Invalid sensor event");
            return;
        }

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        float acceleration = Math.abs(x) + Math.abs(y) + Math.abs(z);
        if (acceleration > 20) {
            processCommand("STOP");
            return;
        }

        if (motorSwitch.isChecked() && radioTilt.isChecked()) {
            String newTiltCommand = null;

            if (Math.abs(x) > tiltThreshold) {
                newTiltCommand = x < 0 ? "right" : "left";
            } else if (Math.abs(y) > tiltThreshold) {
                newTiltCommand = y < 0 ? "forward" : "backward";
            }

            if (newTiltCommand != null && !newTiltCommand.equals(lastTiltCommand)) {
                processCommand(newTiltCommand);
                lastTiltCommand = newTiltCommand;
                isMoving = true;
            } else if (newTiltCommand == null && isMoving) {
                processCommand("stop");
                isMoving = false;
                lastTiltCommand = null;
            }
        } else {
            if (isMoving) {
                processCommand("stop");
                isMoving = false;
                lastTiltCommand = null;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopFunctionality();
        handler.removeCallbacks(bluetoothCheckRunnable);
        closeSocket();
    }

    private void stopFunctionality() {
        sensorManager.unregisterListener(this);
        destroySpeechRecognizer();
    }

    private class SpeechRecognitionListener implements RecognitionListener {
        @Override
        public void onReadyForSpeech(Bundle params) {}

        @Override
        public void onBeginningOfSpeech() {
            textView.setText(isArabic ? "تم الكشف عن الصوت..." : "Speech detected...");
        }

        @Override
        public void onRmsChanged(float rmsdB) {}

        @Override
        public void onBufferReceived(byte[] buffer) {}

        @Override
        public void onEndOfSpeech() {}

        @Override
        public void onError(int error) {
            textView.setText(isArabic ? "خطأ: " + error : "Error: " + error);
            startSpeechRecognition();
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String command = matches.get(0).toLowerCase(Locale.ROOT);
                processCommand(command);
            } else {
                textView.setText(isArabic ? "لم يتم العثور على تطابق، يرجى المحاولة مرة أخرى." : "No match found, please try again.");
            }
            startSpeechRecognition();
        }

        @Override
        public void onPartialResults(Bundle partialResults) {}

        @Override
        public void onEvent(int eventType, Bundle params) {}
    }
}