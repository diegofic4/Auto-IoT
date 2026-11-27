#pragma once

#include <stdint.h>

#include "esp_err.h"

#ifdef __cplusplus
extern "C" {
#endif

esp_err_t auto_iot_start(const char *password);
void auto_iot_handle_serial(uint8_t input);

#ifdef __cplusplus
}
#endif
