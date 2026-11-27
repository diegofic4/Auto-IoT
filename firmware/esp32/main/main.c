#include <stddef.h>
#include <stdint.h>

#include "driver/uart.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "sdkconfig.h"

#include "application.h"

static const char *TAG = "auto_iot";

void app_main(void)
{
    esp_err_t network_result = auto_iot_start(CONFIG_AUTO_IOT_WIFI_PASSWORD);
    if (network_result != ESP_OK) {
        ESP_LOGW(TAG, "Wi-Fi no disponible: %s. Control serial activo.", esp_err_to_name(network_result));
    }

    const uart_config_t config = {
        .baud_rate = 115200,
        .data_bits = UART_DATA_8_BITS,
        .parity = UART_PARITY_DISABLE,
        .stop_bits = UART_STOP_BITS_1,
        .flow_ctrl = UART_HW_FLOWCTRL_DISABLE,
        .source_clk = UART_SCLK_DEFAULT,
    };
    ESP_ERROR_CHECK(uart_param_config(UART_NUM_0, &config));
    ESP_ERROR_CHECK(uart_driver_install(UART_NUM_0, 256, 0, 0, NULL, 0));
    ESP_LOGI(TAG, "UART 115200: W avance, S retroceso, A/D giro con dos servos, X parar, F luz");

    while (true) {
        uint8_t input;
        if (uart_read_bytes(UART_NUM_0, &input, 1, pdMS_TO_TICKS(100)) == 1) {
            auto_iot_handle_serial(input);
        }
    }
}
