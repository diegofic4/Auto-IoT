#include "display.h"

#include "hardware_config.h"

#if AUTO_IOT_USE_OLED
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include "driver/i2c_master.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include "vehicle.h"

#define OLED_ADDR 0x3C
#define OLED_WIDTH 128
#define OLED_TIMEOUT_MS 30

static i2c_master_dev_handle_t oled;

static const uint8_t font5x8[][5] = {
    [' '] = {0x00, 0x00, 0x00, 0x00, 0x00},
    ['!'] = {0x00, 0x00, 0x5F, 0x00, 0x00},
    ['%'] = {0x23, 0x13, 0x08, 0x64, 0x62},
    ['+'] = {0x08, 0x08, 0x3E, 0x08, 0x08},
    ['-'] = {0x08, 0x08, 0x08, 0x08, 0x08},
    ['.'] = {0x00, 0x60, 0x60, 0x00, 0x00},
    [':'] = {0x00, 0x36, 0x36, 0x00, 0x00},
    ['_'] = {0x40, 0x40, 0x40, 0x40, 0x40},
    ['='] = {0x14, 0x14, 0x14, 0x14, 0x14},
    ['0'] = {0x3E, 0x51, 0x49, 0x45, 0x3E},
    ['1'] = {0x00, 0x42, 0x7F, 0x40, 0x00},
    ['2'] = {0x42, 0x61, 0x51, 0x49, 0x46},
    ['3'] = {0x21, 0x41, 0x45, 0x4B, 0x31},
    ['4'] = {0x18, 0x14, 0x12, 0x7F, 0x10},
    ['5'] = {0x27, 0x45, 0x45, 0x45, 0x39},
    ['6'] = {0x3C, 0x4A, 0x49, 0x49, 0x30},
    ['7'] = {0x01, 0x71, 0x09, 0x05, 0x03},
    ['8'] = {0x36, 0x49, 0x49, 0x49, 0x36},
    ['9'] = {0x06, 0x49, 0x49, 0x29, 0x1E},
    ['A'] = {0x7E, 0x11, 0x11, 0x11, 0x7E},
    ['B'] = {0x7F, 0x49, 0x49, 0x49, 0x36},
    ['C'] = {0x3E, 0x41, 0x41, 0x41, 0x22},
    ['D'] = {0x7F, 0x41, 0x41, 0x22, 0x1C},
    ['E'] = {0x7F, 0x49, 0x49, 0x49, 0x41},
    ['F'] = {0x7F, 0x09, 0x09, 0x09, 0x01},
    ['G'] = {0x3E, 0x41, 0x49, 0x49, 0x7A},
    ['H'] = {0x7F, 0x08, 0x08, 0x08, 0x7F},
    ['I'] = {0x00, 0x41, 0x7F, 0x41, 0x00},
    ['J'] = {0x20, 0x40, 0x41, 0x3F, 0x01},
    ['K'] = {0x7F, 0x08, 0x14, 0x22, 0x41},
    ['L'] = {0x7F, 0x40, 0x40, 0x40, 0x40},
    ['M'] = {0x7F, 0x02, 0x04, 0x02, 0x7F},
    ['N'] = {0x7F, 0x04, 0x08, 0x10, 0x7F},
    ['O'] = {0x3E, 0x41, 0x41, 0x41, 0x3E},
    ['P'] = {0x7F, 0x09, 0x09, 0x09, 0x06},
    ['Q'] = {0x3E, 0x41, 0x51, 0x21, 0x5E},
    ['R'] = {0x7F, 0x09, 0x19, 0x29, 0x46},
    ['S'] = {0x46, 0x49, 0x49, 0x49, 0x31},
    ['T'] = {0x01, 0x01, 0x7F, 0x01, 0x01},
    ['U'] = {0x3F, 0x40, 0x40, 0x40, 0x3F},
    ['V'] = {0x1F, 0x20, 0x40, 0x20, 0x1F},
    ['W'] = {0x7F, 0x20, 0x18, 0x20, 0x7F},
    ['X'] = {0x63, 0x14, 0x08, 0x14, 0x63},
    ['Y'] = {0x07, 0x08, 0x70, 0x08, 0x07},
    ['Z'] = {0x61, 0x51, 0x49, 0x45, 0x43},
    ['c'] = {0x00, 0x38, 0x44, 0x44, 0x20},
    ['m'] = {0x7C, 0x04, 0x18, 0x04, 0x78}
};

static void draw_page(uint8_t page, const char *line)
{
    const uint8_t position[] = {0x00, 0xB0 | page, 0x00, 0x10};
    if (i2c_master_transmit(oled, position, sizeof(position), OLED_TIMEOUT_MS) != ESP_OK) {
        return;
    }

    uint8_t pixels[1 + OLED_WIDTH] = {0x40};
    size_t start = 1;
    for (; *line != '\0' && start + 5 < sizeof(pixels); ++line, start += 6) {
        uint8_t glyph = (uint8_t)*line;
        if (glyph < sizeof(font5x8) / sizeof(font5x8[0])) {
            memcpy(pixels + start, font5x8[glyph], 5);
        }
    }
    i2c_master_transmit(oled, pixels, sizeof(pixels), OLED_TIMEOUT_MS);
}

static void refresh_display(void *unused)
{
    (void)unused;
    for (uint8_t page = 0; page < 8; ++page) {
        draw_page(page, "");
    }
    draw_page(0, "AUTO-IOT ESP32");

    for (;;) {
        vehicle_snapshot_t snapshot;
        vehicle_read_snapshot(false, &snapshot);

        char distance[24] = "DIST: SIN ECO";
        if (snapshot.distance_valid) {
            snprintf(distance, sizeof(distance), "DIST: %.1f cm", snapshot.distance_cm);
        }
        draw_page(2, snapshot.motor_state);
        draw_page(4, distance);
        const char *status = snapshot.flash_on ? "FLASH: ON" : "FLASH: OFF";
        if (snapshot.brake_active) {
            status = snapshot.stop_reason;
        }
        draw_page(6, status);

        vTaskDelay(pdMS_TO_TICKS(250));
    }
}

esp_err_t auto_iot_display_start(void)
{
    const i2c_master_bus_config_t bus_config = {
        .i2c_port = I2C_NUM_0,
        .sda_io_num = GPIO_NUM_21,
        .scl_io_num = GPIO_NUM_22,
        .clk_source = I2C_CLK_SRC_DEFAULT,
        .glitch_ignore_cnt = 7,
        .flags.enable_internal_pullup = true,
    };
    i2c_master_bus_handle_t bus;
    esp_err_t error = i2c_new_master_bus(&bus_config, &bus);
    if (error != ESP_OK) {
        return error;
    }

    const i2c_device_config_t device_config = {
        .dev_addr_length = I2C_ADDR_BIT_LEN_7,
        .device_address = OLED_ADDR,
        .scl_speed_hz = 100000,
    };
    error = i2c_master_probe(bus, OLED_ADDR, OLED_TIMEOUT_MS);
    if (error == ESP_OK) {
        error = i2c_master_bus_add_device(bus, &device_config, &oled);
    }
    if (error == ESP_OK) {
        const uint8_t setup[] = {
            0x00, 0xAE, 0xA8, 0x3F, 0xD3, 0x00, 0x40, 0x20, 0x02,
            0xA1, 0xC8, 0xDA, 0x12, 0x8D, 0x14, 0xAF,
        };
        error = i2c_master_transmit(oled, setup, sizeof(setup), OLED_TIMEOUT_MS);
        if (error == ESP_OK) {
            if (xTaskCreate(refresh_display, "oled", 3072, NULL, 3, NULL) == pdPASS) {
                return ESP_OK;
            }
            error = ESP_ERR_NO_MEM;
        }
        i2c_master_bus_rm_device(oled);
    }
    i2c_del_master_bus(bus);
    return error;
}
#else
esp_err_t auto_iot_display_start(void)
{
    return ESP_OK;
}
#endif
