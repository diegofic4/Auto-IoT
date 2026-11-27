idf_component_register(
    SRCS "main.c" "../src/application.c" "../src/vehicle.c" "../src/display.c" "../src/network.c" "../src/es3.c"
    INCLUDE_DIRS "../src"
    PRIV_REQUIRES
        esp_driver_gpio esp_driver_i2c esp_driver_ledc esp_driver_uart
        esp_event esp_http_server esp_netif esp_rom esp_timer esp_wifi esp_hw_support lwip nvs_flash
)
