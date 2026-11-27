#include "vehicle.h"

#include <stdint.h>

#include "driver/gpio.h"
#include "esp_log.h"
#include "esp_rom_sys.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"

#include "hardware_config.h"

#if AUTO_IOT_USE_SERVOS
#include "driver/ledc.h"
#endif

#define PIN_TRIG GPIO_NUM_5
#define PIN_ECHO GPIO_NUM_18
#define PIN_FLASH GPIO_NUM_4
#define STOP_DISTANCE_CM 15.0f
#define SAMPLE_PERIOD_MS 70
#define ECHO_TIMEOUT_US 25000
#define MAX_SAMPLE_AGE_US 250000
#define REMOTE_TIMEOUT_US 3000000

static const char *TAG = "vehicle";
static const char *const motor_states[] = {
    [VEHICLE_FORWARD] = "AVANZANDO",
    [VEHICLE_REVERSE] = "RETROCEDIENDO",
    [VEHICLE_LEFT] = "GIRANDO_IZQUIERDA",
    [VEHICLE_RIGHT] = "GIRANDO_DERECHA",
    [VEHICLE_STOP] = "DETENIDO",
};

static SemaphoreHandle_t vehicle_mutex;
static vehicle_command_t motion = VEHICLE_STOP;
static float distance_cm;
static bool distance_valid;
static int64_t sample_time_us;
static bool flash_on;
static bool remote_motion;
static int64_t last_remote_activity_us;
static const char *stop_reason = "INICIO";

static void configure_output(gpio_num_t pin)
{
    ESP_ERROR_CHECK(gpio_reset_pin(pin));
    ESP_ERROR_CHECK(gpio_set_level(pin, 0));
    ESP_ERROR_CHECK(gpio_set_direction(pin, GPIO_MODE_OUTPUT));
}

#if AUTO_IOT_USE_SERVOS
#define PIN_SERVO_LEFT GPIO_NUM_12
#define PIN_SERVO_RIGHT GPIO_NUM_14
#define SERVO_MIN_DUTY 410
#define SERVO_NEUTRAL_DUTY 1188
#define SERVO_MAX_DUTY 1966

static void configure_drive(void)
{
    const ledc_timer_config_t timer = {
        .speed_mode = LEDC_LOW_SPEED_MODE,
        .duty_resolution = LEDC_TIMER_14_BIT,
        .timer_num = LEDC_TIMER_0,
        .freq_hz = 50,
        .clk_cfg = LEDC_AUTO_CLK,
    };
    ESP_ERROR_CHECK(ledc_timer_config(&timer));

    ledc_channel_config_t channel = {
        .gpio_num = PIN_SERVO_LEFT,
        .speed_mode = LEDC_LOW_SPEED_MODE,
        .channel = LEDC_CHANNEL_0,
        .timer_sel = LEDC_TIMER_0,
        .duty = SERVO_NEUTRAL_DUTY,
    };
    ESP_ERROR_CHECK(ledc_channel_config(&channel));
    channel.gpio_num = PIN_SERVO_RIGHT;
    channel.channel = LEDC_CHANNEL_1;
    ESP_ERROR_CHECK(ledc_channel_config(&channel));
}

static void drive(vehicle_command_t command)
{
    // Mismos pulsos que el programa original a 50 Hz. Verificar neutro con las ruedas levantadas.
    static const uint32_t duty[][2] = {
        [VEHICLE_FORWARD] = {SERVO_MAX_DUTY, SERVO_MIN_DUTY},
        [VEHICLE_REVERSE] = {SERVO_MIN_DUTY, SERVO_MAX_DUTY},
        [VEHICLE_LEFT] = {SERVO_MIN_DUTY, SERVO_MIN_DUTY},
        [VEHICLE_RIGHT] = {SERVO_MAX_DUTY, SERVO_MAX_DUTY},
        [VEHICLE_STOP] = {SERVO_NEUTRAL_DUTY, SERVO_NEUTRAL_DUTY},
    };
    ESP_ERROR_CHECK(ledc_set_duty(LEDC_LOW_SPEED_MODE, LEDC_CHANNEL_0, duty[command][0]));
    ESP_ERROR_CHECK(ledc_update_duty(LEDC_LOW_SPEED_MODE, LEDC_CHANNEL_0));
    ESP_ERROR_CHECK(ledc_set_duty(LEDC_LOW_SPEED_MODE, LEDC_CHANNEL_1, duty[command][1]));
    ESP_ERROR_CHECK(ledc_update_duty(LEDC_LOW_SPEED_MODE, LEDC_CHANNEL_1));
}
#else
#define PIN_MOTOR_IN1 GPIO_NUM_25
#define PIN_MOTOR_IN2 GPIO_NUM_26
#define PIN_MOTOR_ENA GPIO_NUM_27

static void configure_drive(void)
{
    configure_output(PIN_MOTOR_ENA);
    configure_output(PIN_MOTOR_IN1);
    configure_output(PIN_MOTOR_IN2);
}

static void drive(vehicle_command_t command)
{
    // Deshabilitar el puente antes de cambiar el sentido.
    ESP_ERROR_CHECK(gpio_set_level(PIN_MOTOR_ENA, 0));
    ESP_ERROR_CHECK(gpio_set_level(PIN_MOTOR_IN1, command == VEHICLE_FORWARD));
    ESP_ERROR_CHECK(gpio_set_level(PIN_MOTOR_IN2, command == VEHICLE_REVERSE));
    if (command == VEHICLE_FORWARD || command == VEHICLE_REVERSE) {
        ESP_ERROR_CHECK(gpio_set_level(PIN_MOTOR_ENA, 1));
    }
}
#endif

static bool wait_for_echo_level(int level, int64_t deadline_us)
{
    while (esp_timer_get_time() < deadline_us) {
        if (gpio_get_level(PIN_ECHO) == level) {
            return true;
        }
    }
    return false;
}

static bool measure_distance(float *measured_cm)
{
    if (gpio_get_level(PIN_ECHO) != 0) {
        return false;
    }
    gpio_set_level(PIN_TRIG, 1);
    esp_rom_delay_us(10);
    gpio_set_level(PIN_TRIG, 0);

    int64_t deadline_us = esp_timer_get_time() + ECHO_TIMEOUT_US;
    if (!wait_for_echo_level(1, deadline_us)) {
        return false;
    }
    int64_t echo_start_us = esp_timer_get_time();
    if (!wait_for_echo_level(0, deadline_us)) {
        return false;
    }
    *measured_cm = (float)(esp_timer_get_time() - echo_start_us) / 58.0f;
    return *measured_cm >= 2.0f && *measured_cm <= 400.0f;
}

static bool has_recent_distance(int64_t now_us)
{
    return distance_valid && now_us - sample_time_us <= MAX_SAMPLE_AGE_US;
}

static const char *forward_block_reason(int64_t now_us)
{
    if (!has_recent_distance(now_us)) {
        return "SIN_ECO";
    }
    return distance_cm <= STOP_DISTANCE_CM ? "OBSTACULO" : NULL;
}

static void stop(const char *reason)
{
    if (motion != VEHICLE_STOP) {
        drive(VEHICLE_STOP);
    }
    motion = VEHICLE_STOP;
    remote_motion = false;
    stop_reason = reason;
}

static void enforce_motion_limits(int64_t now_us)
{
    if (remote_motion && now_us - last_remote_activity_us >= REMOTE_TIMEOUT_US) {
        stop("SIN_ENLACE");
    } else if (motion == VEHICLE_FORWARD) {
        const char *reason = forward_block_reason(now_us);
        if (reason != NULL) {
            stop(reason);
        }
    }
}

static void fill_snapshot(vehicle_snapshot_t *snapshot, int64_t now_us)
{
    const char *brake_reason = forward_block_reason(now_us);
    bool recent = has_recent_distance(now_us);
    *snapshot = (vehicle_snapshot_t) {
        .distance_cm = recent ? distance_cm : 0.0f,
        .distance_valid = recent,
        .motor_state = motor_states[motion],
        .brake_active = brake_reason != NULL,
        .stop_reason = brake_reason != NULL ? brake_reason : stop_reason,
        .flash_on = flash_on,
    };
}

static void sample_sensor(void)
{
    float measured_cm = 0.0f;
    bool valid = measure_distance(&measured_cm);
    int64_t sampled_at_us = esp_timer_get_time();

    xSemaphoreTake(vehicle_mutex, portMAX_DELAY);
    distance_valid = valid;
    distance_cm = measured_cm;
    sample_time_us = sampled_at_us;
    enforce_motion_limits(esp_timer_get_time());
    xSemaphoreGive(vehicle_mutex);
}

static void update_sensor(void *unused)
{
    (void)unused;
    TickType_t previous_wake = xTaskGetTickCount();
    for (;;) {
        sample_sensor();
        vTaskDelayUntil(&previous_wake, pdMS_TO_TICKS(SAMPLE_PERIOD_MS));
    }
}

esp_err_t vehicle_start(void)
{
    configure_drive();
    drive(VEHICLE_STOP);
    configure_output(PIN_TRIG);
    configure_output(PIN_FLASH);
    ESP_ERROR_CHECK(gpio_reset_pin(PIN_ECHO));
    ESP_ERROR_CHECK(gpio_set_direction(PIN_ECHO, GPIO_MODE_INPUT));

    vehicle_mutex = xSemaphoreCreateMutex();
    if (vehicle_mutex == NULL) {
        return ESP_ERR_NO_MEM;
    }
    if (xTaskCreate(update_sensor, "hc_sr04", 3072, NULL, 5, NULL) != pdPASS) {
        vSemaphoreDelete(vehicle_mutex);
        return ESP_ERR_NO_MEM;
    }
#if AUTO_IOT_USE_SERVOS
    ESP_LOGI(TAG, "Traccion: dos servos en IO12 e IO14");
#else
    ESP_LOGI(TAG, "Traccion: L298N en IO25, IO26 e IO27");
#endif
    return ESP_OK;
}

vehicle_command_result_t vehicle_apply_command(vehicle_command_t command, bool remote,
                                               vehicle_snapshot_t *snapshot)
{
    xSemaphoreTake(vehicle_mutex, portMAX_DELAY);
    int64_t now_us = esp_timer_get_time();
    enforce_motion_limits(now_us);

    vehicle_command_result_t result = VEHICLE_COMMAND_OK;
    const char *reason = forward_block_reason(now_us);
    if (command < VEHICLE_FORWARD || command > VEHICLE_FLASH) {
        result = VEHICLE_COMMAND_UNSUPPORTED;
    } else if (command == VEHICLE_FLASH) {
        flash_on = !flash_on;
        ESP_ERROR_CHECK(gpio_set_level(PIN_FLASH, flash_on));
    } else if (command == VEHICLE_STOP) {
        stop("MANUAL");
#if !AUTO_IOT_USE_SERVOS
    } else if (command == VEHICLE_LEFT || command == VEHICLE_RIGHT) {
        result = VEHICLE_COMMAND_UNSUPPORTED;
#endif
    } else if (command == VEHICLE_FORWARD && reason != NULL) {
        if (motion == VEHICLE_STOP) {
            stop_reason = reason;
        }
        result = VEHICLE_COMMAND_BLOCKED;
    } else {
        if (motion != command) {
            drive(command);
            motion = command;
        }
        remote_motion = remote;
        last_remote_activity_us = now_us;
        stop_reason = "";
    }

    fill_snapshot(snapshot, now_us);
    xSemaphoreGive(vehicle_mutex);
    return result;
}

void vehicle_read_snapshot(bool remote, vehicle_snapshot_t *snapshot)
{
    xSemaphoreTake(vehicle_mutex, portMAX_DELAY);
    int64_t now_us = esp_timer_get_time();
    enforce_motion_limits(now_us);
    if (remote && remote_motion) {
        last_remote_activity_us = now_us;
    }
    fill_snapshot(snapshot, now_us);
    xSemaphoreGive(vehicle_mutex);
}

void vehicle_on_remote_disconnect(void)
{
    xSemaphoreTake(vehicle_mutex, portMAX_DELAY);
    if (remote_motion) {
        stop("SIN_ENLACE");
    }
    xSemaphoreGive(vehicle_mutex);
}

void vehicle_set_light(bool on)
{
    xSemaphoreTake(vehicle_mutex, portMAX_DELAY);
    flash_on = on;
    ESP_ERROR_CHECK(gpio_set_level(PIN_FLASH, flash_on));
    xSemaphoreGive(vehicle_mutex);
}
