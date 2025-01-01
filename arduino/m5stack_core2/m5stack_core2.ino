// Setup Arduino IDE (board manager, libraries):
// https://docs.m5stack.com/en/arduino/arduino_ide

// Compile / Upload:
// -> select Board=M5Core2 (and correct USB port)

// documentation:
// https://github.com/m5stack/m5-docs/blob/master/docs/en/core/core2.md
// https://shop.m5stack.com/products/m5stack-core2-esp32-iot-development-kit
// - LDO1 -> RTC_VDD: real-time clock BM8563
// - LDO2 -> PERI_VDD: sd card, lcd
// - LOD3 -> VIB_MOTOR
// - LDOs are for low power (but low ripple), DCDC are for ESP, LCD light, ...

// ema measurements (DCDC3 off / on) [12h]:
// 10,10,100 - 100 mA / 115 mA [1.4 Ah]
// 100,1,10  - 90 mA / 90 mA

// ms between measurements
#define DELAY 10
// how often to send measurements
#define SEND_EVERY 10
// cycles to update fast display (IMU)
#define EVERY1 10
// cycles to update slow display (I, bat)
#define EVERY2 100

// https://chatgpt.com/c/674b1d21-9f74-8000-918c-23676efe547f

// Note that you'll find frameworks files here
// $HOME/Library/Arduino15/packages/m5stack/hardware/esp32/2.1.2/libraries/WiFi/src/WiFi.h
#include <M5Core2.h>
#include <WiFi.h>
#include <WiFiUdp.h>

#include "wifi_credentials.h"
int wifi_i = 0;
char udpAddress[17];
const int udpPort = 9001;
#define UDP_BROADCAST_PORT 9002
#define PROTOCOL_IDENTIFIER "PANTONE1"
#define PACKET_BUFFER_LEN 32
char packetBuffer[PACKET_BUFFER_LEN];

float imuData[9] = {0, 0, 0, 0, 0, 0, 0, 0, 0};

#define G 9.81
#define GYROFACT 0.01

// isACIN=1, isCharging=0, isVBUS=1
// GetBatState=1
// GetBatteryLevel=0.000000
// GetBatVoltage=0.000000
// GetBatCurrent=0.000000
// GetVinVoltage=5.203700
// GetVinCurrent=59.375000
// GetVBusVoltage=5.045600
// GetVBusCurrent=0.000000
// GetTempInAXP192=32.500004
// GetBatPower=0.000000
// GetBatChargeCurrent=0.000000
// GetAPSVoltage=4.970000
// GetBatCoulombInput=0.000000
// GetBatCoulombOut=0.000000

WiFiUDP udp;

void setup() {
  M5.begin();
  M5.IMU.Init();
  Serial.begin(115200);

  while (!Serial) {
    delay(10);
  }
  Serial.println("M5Core2 IMU serial streamer");

  // https://claude.ai/chat/92d3490b-2681-4549-9b15-2526b761ccf4
  M5.Axp.begin();
  // M5.Axp.SetCHGCurrent(AXP192::kCHG_190mA); // that's the default
  M5.Axp.SetBusPowerMode(1);  // 0=use inernal boost, 1=powered externally

  M5.Axp.ScreenBreath(1); // no difference
  // M5.Axp.SetLed(false); // disables green status light
  // M5.Axp.SetDCDC3(false); // turns off screen ... maybe touch still enabled?

  delay(100);

  Serial.printf("isACIN=%d, isCharging=%d, isVBUS=%d\n", M5.Axp.isACIN(), M5.Axp.isCharging(), M5.Axp.isVBUS());
  Serial.printf("GetBatState=%d\n", M5.Axp.GetBatState());
  Serial.printf("GetBatteryLevel=%f\n", M5.Axp.GetBatteryLevel());
  Serial.printf("GetBatVoltage=%f\n", M5.Axp.GetBatVoltage());
  Serial.printf("GetBatCurrent=%f\n", M5.Axp.GetBatCurrent());
  Serial.printf("GetVinVoltage=%f\n", M5.Axp.GetVinVoltage());
  Serial.printf("GetVinCurrent=%f\n", M5.Axp.GetVinCurrent());
  Serial.printf("GetVBusVoltage=%f\n", M5.Axp.GetVBusVoltage());
  Serial.printf("GetVBusCurrent=%f\n", M5.Axp.GetVBusCurrent());
  Serial.printf("GetTempInAXP192=%f\n", M5.Axp.GetTempInAXP192());
  Serial.printf("GetBatPower=%f\n", M5.Axp.GetBatPower());
  Serial.printf("GetBatChargeCurrent=%f\n", M5.Axp.GetBatChargeCurrent());
  Serial.printf("GetAPSVoltage=%f\n", M5.Axp.GetAPSVoltage());
  Serial.printf("GetBatCoulombInput=%f\n", M5.Axp.GetBatCoulombInput());
  Serial.printf("GetBatCoulombOut=%f\n", M5.Axp.GetBatCoulombOut());

  // M5.Axp.SetLDOEnable(3, true); // start vibrate
  // M5.Axp.SetLDOEnable(3, false); // stop vibrate

  displayOn();

  wifi_connect();

  M5.Lcd.clear();
  Serial.printf("\nConnected to %s", ssids[wifi_i]);
  Serial.printf("Device IP: %s\n", WiFi.localIP().toString().c_str());
  showWifi();
  strcpy(udpAddress, "");

  udp.begin(UDP_BROADCAST_PORT);
}

void wifi_connect() {
  WiFi.mode(WIFI_STA);
  WiFi.disconnect(true);
  delay(100);

  char name[128];
  bool connected = false;

  Serial.printf("MAC Address: %s\n", WiFi.macAddress().c_str());

  while (!connected) {
    M5.Lcd.clear();
    M5.Lcd.setCursor(10, 10);
    wifi_i = -1;

    Serial.printf("Scanning networks...\n");
    M5.Lcd.printf("Scanning networks...\n");
    int numNetworks = WiFi.scanNetworks();

    Serial.printf("Found %d networks\n", numNetworks);
    M5.Lcd.printf("Found %d networks\n", numNetworks);
    for (int i = 0; i < numNetworks; i++) {
      strncpy(name, WiFi.SSID(i).c_str(), 128);
      bool known = false;
      for (int j = 0; !known && j < WIFIS_N; j++) {
        if (!strcmp(name, ssids[j])) {
          known = true;
          if (wifi_i == -1) wifi_i = j;
        }
      }
      if (known) {
        Serial.printf("%2d) %s %d dBm\n", i + 1, name, WiFi.RSSI(i));
        M5.Lcd.printf("%2d) %s %d dBm\n", i + 1, name, WiFi.RSSI(i));
      }
    }
    delay(1000);

    if (wifi_i != -1) {
      WiFi.disconnect(true, true);
      delay(1000);
      Serial.printf("Connecting to %s/%s\n", ssids[wifi_i], passwords[wifi_i]);
      M5.Lcd.printf("Connecting to %s: ", ssids[wifi_i]);
      WiFi.begin(ssids[wifi_i], passwords[wifi_i]);
      for (int i = 0; i < 60 && !connected; i++) {
        delay(500);
        connected |= WiFi.status() == WL_CONNECTED;
        Serial.printf("WiFi.status()=%d\n", WiFi.status());
        M5.Lcd.print(".");
      }
    }
    if (!connected) {
      WiFi.disconnect(true);
      WiFi.scanDelete();
      delay(1000);
    }
  }
}

float accX, accY, accZ;
float gyroX, gyroY, gyroZ;
unsigned long i = 0;
float iinEma = 0;
float ibatEma = 0;
#define EMA_ALPHA 0.1

float swp(float value) {
  uint8_t* bytePointer = (uint8_t*)&value;
  std::swap(bytePointer[0], bytePointer[3]);
  std::swap(bytePointer[1], bytePointer[2]);
  return value;
}

int displayState = 0;

void displayOff() {
  M5.Axp.SetLed(false);
  M5.Axp.SetDCDC3(false);
  // M5.Axp.SetLDOEnable(2, false);
  displayState = 0;
}

void showWifi() {
  if (WiFi.status() == WL_CONNECTED) {
    M5.Lcd.setCursor(10, 40);
    M5.Lcd.printf("Wifi: %s", ssids[wifi_i]);
    M5.Lcd.setCursor(10, 60);
    M5.Lcd.printf("Device: %s", WiFi.localIP().toString().c_str());
    M5.Lcd.setCursor(10, 80);
    M5.Lcd.printf("Server: %s         ", udpAddress);
  }
}

void displayOn() {
  // M5.Axp.SetLDOEnable(2, true);
  M5.Axp.SetDCDC3(true);
  M5.Axp.SetLed(true);
  delay(100);
  // 320x240
  M5.Lcd.begin();
  M5.Lcd.setTextSize(2);
  M5.Lcd.fillScreen(BLACK);
  M5.Lcd.setTextColor(WHITE, BLACK);
  showWifi();
  displayState = 1;
}

void loop() {

  if (!strcmp(udpAddress, "")) {
    Serial.println("Waiting for UDP broadcast...");
    delay(1000);
    int packetSize = udp.parsePacket();
    if (!packetSize) return;
    int len = udp.read(packetBuffer, PACKET_BUFFER_LEN - 1);
    if (len > 0) {
      packetBuffer[len] = 0;
    }
    Serial.printf("Broadcast: %s\n", packetBuffer);
    if (strcmp(packetBuffer, PROTOCOL_IDENTIFIER)) {
      Serial.printf("Invalid PROTOCOL_IDENTIFIER: %s\n", packetBuffer);
      M5.Lcd.setCursor(10, 80);
      M5.Lcd.printf("?!: %s", packetBuffer);
      return;
    }
    strcpy(udpAddress, udp.remoteIP().toString().c_str());
    Serial.printf("Server: %s\n", udpAddress);
    M5.Lcd.setCursor(10, 80);
    M5.Lcd.printf("Server: %s         ", udpAddress);
  }

  M5.IMU.getAccelData(&accX, &accY, &accZ);
  M5.IMU.getGyroData(&gyroX, &gyroY, &gyroZ);

// #if 1
//   Serial.print(accX, 3); Serial.print(", ");
//   Serial.print(accY, 3); Serial.print(", ");
//   Serial.print(accZ, 3); Serial.println();
// #else
//   Serial.print(gyroX, 3); Serial.print(", ");
//   Serial.print(gyroY, 3); Serial.print(", ");
//   Serial.print(gyroZ, 3); Serial.println();
// #endif

  imuData[0] += accX * G;
  imuData[1] += accY * G;
  imuData[2] += accZ * G;
  imuData[6] += gyroX * GYROFACT;
  imuData[7] += gyroY * GYROFACT;
  imuData[8] += gyroZ * GYROFACT;
  if ((i + 1) % SEND_EVERY == 0) {
    float imuPacket[9] = {0, 0, 0, 0, 0, 0, 0, 0, 0};
    for (int j = 0; j < 9; j++) {
      imuPacket[j] = swp(imuData[j] / SEND_EVERY);
      imuData[j] = 0;
    }
    udp.beginPacket(udpAddress, udpPort);
    udp.write((uint8_t*)imuPacket, sizeof(imuPacket));
    udp.endPacket();
  }

  if (i % EVERY1 == 0) {
    // M5.Lcd.fillRect(0, 100, 320, 180, BLACK);
    M5.Lcd.setCursor(10, 120);
    M5.Lcd.printf("Acc %+.2f %+.2f %+.2f    ", accX, accY, accZ);
    M5.Lcd.setCursor(10, 140);
    M5.Lcd.printf("Gyr %+5.0f %+5.0f %+5.0f    ", gyroX, gyroY, gyroZ);
  }

  if (i % EVERY2 == 0) {
    M5.Lcd.setCursor(10, 10);
    M5.Lcd.printf("%1d", i / EVERY2);
    M5.Lcd.setCursor(10, 190);
    M5.Lcd.printf("Bat/In=%.1fV/%.1fV", M5.Axp.GetBatVoltage(), M5.Axp.GetVinVoltage());
    float iin = M5.Axp.GetVinCurrent();
    iinEma = EMA_ALPHA * iin + (1- EMA_ALPHA) * iinEma;
    float ibat = M5.Axp.GetBatCurrent();
    ibatEma = EMA_ALPHA * ibat + (1- EMA_ALPHA) * ibatEma;
    M5.Lcd.setCursor(10, 210);
    M5.Lcd.printf("%3.0f/%3.0fmA (%3.0f/%3.0f)  ", iin, ibat, iinEma, ibatEma);
  }

  M5.update();
  if (M5.BtnA.wasPressed() && !displayState) displayOn();
  if (M5.BtnB.wasPressed() &&  displayState) displayOff();

  i++;

  delay(DELAY);
}
