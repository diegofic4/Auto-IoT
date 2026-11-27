#include "application.h"

#include <stdio.h>

#include "display.h"
#include "network.h"
#include "vehicle.h"

esp_err_t auto_iot_start(const char *password)
{
    // Primero se inicializan las salidas de tracción con el motor detenido.
    ESP_ERROR_CHECK(vehicle_start());

    esp_err_t display_result = auto_iot_display_start();
    if (display_result != ESP_OK) {
        printf("OLED no disponible: %s\n", esp_err_to_name(display_result));
    }
    return auto_iot_network_start(password);
}

void auto_iot_handle_serial(uint8_t input)
{
    vehicle_command_t command;
    switch (input) {
        case 'W': case 'w': command = VEHICLE_FORWARD; break;
        case 'S': case 's': command = VEHICLE_REVERSE; break;
        case 'A': case 'a': command = VEHICLE_LEFT; break;
        case 'D': case 'd': command = VEHICLE_RIGHT; break;
        case 'X': case 'x': command = VEHICLE_STOP; break;
        case 'F': case 'f': command = VEHICLE_FLASH; break;
        default: return;
    }

    vehicle_snapshot_t snapshot;
    vehicle_command_result_t result = vehicle_apply_command(command, false, &snapshot);
    if (result == VEHICLE_COMMAND_OK) {
        printf("ACK %c | %s | distancia %.1f cm | eco %s\n", input,
               snapshot.motor_state, snapshot.distance_cm, snapshot.distance_valid ? "valido" : "invalido");
    } else if (result == VEHICLE_COMMAND_BLOCKED) {
        printf("ERR %c | %s\n", input, snapshot.stop_reason);
    } else {
        printf("ERR %c | comando no disponible para el montaje\n", input);
    }
}
