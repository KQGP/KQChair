# KQChair

A **Bluetooth-controlled electric wheelchair** project: an ESP32 firmware that drives two DC motors over Bluetooth Serial, plus an Android app for remote control.

## ✨ Features

- 📡 Bluetooth Serial control (device name: `Wheelchair`)
- 🎮 Commands: `ENABLE`, `DISABLE`, `FORWARD`, `BACKWARD`, `LEFT`, `RIGHT`, `STOP`
- ⚡ PWM motor speed control
- 📱 Android companion app (`app/`, package `com.kqg.chair`)

## 🧱 Hardware

- ESP32 (Bluetooth Serial)
- 2x DC motors with H-bridge (ENA/ENB + IN1..IN5)
- External 5V power supply

## 📁 Project Structure

```
KQChair.ino   # Firmware (ESP32 Arduino sketch)
app/          # Android app (Gradle, package com.kqg.chair)
```

## 🚀 Getting Started

### Firmware
1. Open `KQChair.ino` in the Arduino IDE.
2. Select an ESP32 board and the correct port.
3. Upload. Pair over Bluetooth as `Wheelchair`.

### Android App
```bash
cd app
./gradlew assembleDebug
```
Install the resulting APK on your phone, pair with the wheelchair Bluetooth device.

## 📄 License
No license specified.