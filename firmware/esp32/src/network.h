#ifndef AUTO_IOT_NETWORK_H
#define AUTO_IOT_NETWORK_H

#include "esp_err.h"

#define AUTO_IOT_AP_SSID "Auto_IoT_Control"

#ifdef __cplusplus
extern "C" {
#endif

esp_err_t auto_iot_network_start(const char *password);

#ifdef __cplusplus
}
#endif

#endif
