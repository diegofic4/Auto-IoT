#pragma once

#include <stdbool.h>

#include "esp_err.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    VEHICLE_FORWARD,
    VEHICLE_REVERSE,
    VEHICLE_LEFT,
    VEHICLE_RIGHT,
    VEHICLE_STOP,
    VEHICLE_FLASH,
} vehicle_command_t;

typedef enum {
    VEHICLE_COMMAND_OK,
    VEHICLE_COMMAND_BLOCKED,
    VEHICLE_COMMAND_UNSUPPORTED,
    VEHICLE_COMMAND_UNAVAILABLE,
} vehicle_command_result_t;

typedef struct {
    float distance_cm;
    bool distance_valid;
    const char *motor_state;
    bool brake_active;
    const char *stop_reason;
    bool flash_on;
} vehicle_snapshot_t;

esp_err_t vehicle_start(void);
vehicle_command_result_t vehicle_apply_command(vehicle_command_t command, bool remote,
                                               vehicle_snapshot_t *snapshot);
void vehicle_read_snapshot(bool remote, vehicle_snapshot_t *snapshot);
void vehicle_set_light(bool on);
void vehicle_on_remote_disconnect(void);

#ifdef __cplusplus
}
#endif
