#include <BluetoothSerial.h>

#define ENA 5
#define IN1 18
#define IN2 19
#define IN3 23
#define IN4 32
#define ENB 33
#define IN5 2

BluetoothSerial SerialBT;
bool motorsEnabled = false;

void setup() {
  Serial.begin(115200);
  SerialBT.begin("Wheelchair");
  Serial.println("Bluetooth Started. Waiting for commands…");

  pinMode(ENA, OUTPUT);
  pinMode(IN1, OUTPUT);
  pinMode(IN2, OUTPUT);
  pinMode(IN3, OUTPUT);
  pinMode(IN4, OUTPUT);
  pinMode(ENB, OUTPUT);
  pinMode(IN5, OUTPUT);

}

void loop() {
  if (SerialBT.available()) {
    String command = SerialBT.readStringUntil('\n');
    processCommand(command);
  }
}

void processCommand(String command) {
  if (command == "ENABLE") {
    motorsEnabled = true;
    Serial.println("Motors Enabled");
  } else if (command == "DISABLE") {
    motorsEnabled = false;
    stopMotors();
    Serial.println("Motors Disabled");
  } 

  if (motorsEnabled) {
    if (command == "FORWARD") {
      moveForward();
    } else if (command == "BACKWARD") {
      moveBackward();
    } else if (command == "LEFT") {
      turnLeft();
    } else if (command == "RIGHT") {
      turnRight();
    } else if (command == "STOP") {
      stopMotors();
    }
  }
}

void moveForward() {
  analogWrite(ENA, 150);
  analogWrite(ENB, 150);
  digitalWrite(IN1, HIGH);
  digitalWrite(IN2, LOW);
  digitalWrite(IN3, LOW);
  digitalWrite(IN4, HIGH);
  digitalWrite(IN5, HIGH);
  Serial.println("Moving Forward");
}

void moveBackward() {
  digitalWrite(IN1, LOW);
  digitalWrite(IN2, HIGH);
  digitalWrite(IN3, HIGH);
  digitalWrite(IN4, LOW);
  digitalWrite(IN5, HIGH);  
  analogWrite(ENA, 150);
  analogWrite(ENB, 150);
  Serial.println("Moving Backward");
}

void turnLeft() {
  analogWrite(ENA, 130);
  analogWrite(ENB, 130);
  digitalWrite(IN1, HIGH);
  digitalWrite(IN2, LOW);
  digitalWrite(IN3, HIGH);
  digitalWrite(IN4, LOW);
  digitalWrite(IN5, HIGH);
  Serial.println("Turning Left");
}

void turnRight() {
  analogWrite(ENA, 130);
  analogWrite(ENB, 130);
  digitalWrite(IN1, LOW);
  digitalWrite(IN2, HIGH);
  digitalWrite(IN3, LOW);
  digitalWrite(IN4, HIGH);
  digitalWrite(IN5, HIGH);
  Serial.println("Turning Right");
}

void stopMotors() {
  analogWrite(ENA, 0);
  analogWrite(ENB, 0);
  digitalWrite(IN1, LOW);
  digitalWrite(IN2, LOW);
  digitalWrite(IN3, LOW);
  digitalWrite(IN4, LOW);
  digitalWrite(IN5, HIGH);
  Serial.println("Stopping");
}