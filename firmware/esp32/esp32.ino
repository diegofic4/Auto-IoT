#include <Arduino.h>
#include <esp_netif.h>

#include "src/application.h"
#include "src/network.h"

#if __has_include("wifi_secrets.h")
#include "wifi_secrets.h"
#endif

#ifndef AUTO_IOT_WIFI_PASSWORD
#define AUTO_IOT_WIFI_PASSWORD ""
#endif

void setup()
{
    Serial.begin(115200);
    esp_err_t network_result = auto_iot_start(AUTO_IOT_WIFI_PASSWORD);

    if (network_result == ESP_OK) {
        Serial.printf("Wi-Fi: %s (WPA2-CCMP, hasta 4 conexiones, sin ahorro de energia)\n", AUTO_IOT_AP_SSID);
        esp_netif_t *access_point = esp_netif_get_handle_from_ifkey("WIFI_AP_DEF");
        esp_netif_ip_info_t ip;
        if (access_point != nullptr && esp_netif_get_ip_info(access_point, &ip) == ESP_OK) {
            Serial.printf("Telemetria: http://" IPSTR "/telemetria\n", IP2STR(&ip.ip));
        }
    } else if (network_result == ESP_ERR_INVALID_ARG) {
        Serial.println("Wi-Fi desactivado. Escribe tu clave de 8 a 63 caracteres ASCII en wifi_secrets.h y vuelve a subir.");
    } else {
        Serial.printf("Error al iniciar Wi-Fi: %s\n", esp_err_to_name(network_result));
    }
    Serial.println("UART: W avance, S retroceso, A/D giro con dos servos, X parar, F luz.");
}

void loop()
{
    if (Serial.available() > 0) {
        auto_iot_handle_serial(Serial.read());
    } else {
        delay(10);
    }
}
