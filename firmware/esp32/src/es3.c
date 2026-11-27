#include "es3.h"

#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "esp_log.h"
#include "esp_random.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "lwip/sockets.h"
#include "nvs.h"

#include "vehicle.h"

#define ARCHIVE_CAPACITY 32
#define ARCHIVE_PERIOD_MS 15000
#define OFFLINE_CHECK_MS 250
#define EVENT_PERIOD_MS 650
#define HEARTBEAT_TIMEOUT_US 3000000

static const char *TAG = "es3";
static const char *ARCHIVE_KEY = "lecturas_v1";

typedef struct {
    uint64_t boot;
    uint64_t uptime_ms;
    float distance_cm;
    bool valid;
} reading_t;

typedef struct {
    uint32_t count;
    reading_t readings[ARCHIVE_CAPACITY];
} archive_t;

static nvs_handle_t storage;
static archive_t archive;
static uint64_t boot_id;
static int64_t last_heartbeat_us;
static SemaphoreHandle_t archive_lock;
static SemaphoreHandle_t stream_slot;

static bool persist_archive(void)
{
    esp_err_t result = nvs_set_blob(storage, ARCHIVE_KEY, &archive, sizeof(archive));
    if (result == ESP_OK) {
        result = nvs_commit(storage);
    }
    if (result != ESP_OK) {
        ESP_LOGE(TAG, "No se pudo guardar el historial: %s", esp_err_to_name(result));
    }
    return result == ESP_OK;
}

static void archive_task(void *unused)
{
    (void)unused;
    int64_t next_capture_us = 0;

    for (;;) {
        vTaskDelay(pdMS_TO_TICKS(OFFLINE_CHECK_MS));
        xSemaphoreTake(archive_lock, portMAX_DELAY);
        int64_t now = esp_timer_get_time();
        bool online = now - last_heartbeat_us <= HEARTBEAT_TIMEOUT_US;
        if (online) {
            next_capture_us = 0;
        } else if (now >= next_capture_us && archive.count < ARCHIVE_CAPACITY) {
            vehicle_snapshot_t snapshot;
            vehicle_read_snapshot(false, &snapshot);
            archive.readings[archive.count++] = (reading_t) {
                .boot = boot_id,
                .uptime_ms = (uint64_t)now / 1000,
                .distance_cm = snapshot.distance_cm,
                .valid = snapshot.distance_valid,
            };
            if (!persist_archive()) {
                --archive.count;
            }
            next_capture_us = now + ARCHIVE_PERIOD_MS * 1000LL;
        }
        xSemaphoreGive(archive_lock);
    }
}

static esp_err_t json_reply(httpd_req_t *request, const char *status, const char *body)
{
    httpd_resp_set_status(request, status);
    httpd_resp_set_type(request, "application/json");
    httpd_resp_set_hdr(request, "Cache-Control", "no-store");
    return httpd_resp_sendstr(request, body);
}

static esp_err_t send_reading(httpd_req_t *request, const reading_t *reading,
                              const vehicle_snapshot_t *live)
{
    int64_t age_ms = -1;
    if (reading->boot == boot_id) {
        age_ms = esp_timer_get_time() / 1000 - (int64_t)reading->uptime_ms;
    }
    uint64_t reading_id = reading->uptime_ms;
    if (live) {
        reading_id |= UINT64_C(1) << 63;
    }

    char line[336];
    int length = snprintf(line, sizeof(line),
        "{\"idLectura\":\"%016" PRIx64 "-%016" PRIx64 "\",\"capturaMs\":%" PRIu64
        ",\"edadMs\":%" PRId64 ",\"historial\":%s,\"distancia\":%.2f,"
        "\"distanciaValida\":%s,\"estadoMotor\":\"%s\",\"frenoActivo\":%s,\"motivoFreno\":\"%s\"}\n",
        reading->boot, reading_id, reading->uptime_ms, age_ms, live ? "false" : "true",
        (double)reading->distance_cm, reading->valid ? "true" : "false",
        live ? live->motor_state : "DETENIDO",
        (live ? live->brake_active : !reading->valid || reading->distance_cm <= 15.0f) ? "true" : "false",
        live ? live->stop_reason : "");
    if (length < 0 || (size_t)length >= sizeof(line)) {
        return ESP_FAIL;
    }
    return httpd_resp_send_chunk(request, line, length);
}

static void stream_task(void *argument)
{
    httpd_req_t *request = argument;
    int socket_fd = httpd_req_to_sockfd(request);
    httpd_resp_set_type(request, "application/x-ndjson");
    httpd_resp_set_hdr(request, "Cache-Control", "no-store");

    archive_t pending;
    xSemaphoreTake(archive_lock, portMAX_DELAY);
    pending = archive;
    last_heartbeat_us = esp_timer_get_time();
    xSemaphoreGive(archive_lock);

    esp_err_t result = ESP_OK;
    for (uint32_t i = 0; result == ESP_OK && i < pending.count; ++i) {
        result = send_reading(request, &pending.readings[i], NULL);
    }

    while (result == ESP_OK) {
        xSemaphoreTake(archive_lock, portMAX_DELAY);
        bool online = esp_timer_get_time() - last_heartbeat_us <= HEARTBEAT_TIMEOUT_US;
        xSemaphoreGive(archive_lock);
        if (!online) {
            break;
        }
        vehicle_snapshot_t snapshot;
        vehicle_read_snapshot(false, &snapshot);
        reading_t current = {
            .boot = boot_id,
            .uptime_ms = (uint64_t)esp_timer_get_time() / 1000,
            .distance_cm = snapshot.distance_cm,
            .valid = snapshot.distance_valid,
        };
        result = send_reading(request, &current, &snapshot);
        if (result == ESP_OK) {
            vTaskDelay(pdMS_TO_TICKS(EVENT_PERIOD_MS));
        }
    }

    shutdown(socket_fd, SHUT_RDWR);
    httpd_req_async_handler_complete(request);
    xSemaphoreGive(stream_slot);
    vTaskDelete(NULL);
}

static esp_err_t stream_handler(httpd_req_t *request)
{
    if (request->content_len || httpd_req_get_url_query_len(request)) {
        return json_reply(request, "400 Bad Request", "{\"resultado\":\"ERR\",\"error\":\"TRAMA_INVALIDA\"}");
    }
    if (xSemaphoreTake(stream_slot, 0) != pdTRUE) {
        return json_reply(request, "503 Service Unavailable", "{\"resultado\":\"ERR\",\"error\":\"CANAL_OCUPADO\"}");
    }

    httpd_req_t *copy;
    esp_err_t result = httpd_req_async_handler_begin(request, &copy);
    if (result == ESP_OK) {
        if (xTaskCreate(stream_task, "telemetria", 5120, copy, 4, NULL) == pdPASS) {
            return ESP_OK;
        }
        httpd_req_async_handler_complete(copy);
    }
    xSemaphoreGive(stream_slot);
    return json_reply(request, "503 Service Unavailable", "{\"resultado\":\"ERR\",\"error\":\"CANAL_NO_DISPONIBLE\"}");
}

static bool valid_reading_id(const char *id)
{
    return strlen(id) == 33 && id[16] == '-' && strspn(id, "0123456789abcdef") == 16 &&
           strspn(id + 17, "0123456789abcdef") == 16;
}

static bool acknowledge_reading(const char *id)
{
    uint64_t boot = strtoull(id, NULL, 16);
    uint64_t uptime = strtoull(id + 17, NULL, 16);
    for (uint32_t i = 0; i < archive.count; ++i) {
        const reading_t *record = &archive.readings[i];
        if (record->boot == boot && record->uptime_ms == uptime) {
            archive_t before = archive;
            uint32_t removed = i + 1;
            archive.count -= removed;
            memmove(archive.readings, archive.readings + removed, archive.count * sizeof(reading_t));
            if (!persist_archive()) {
                archive = before;
                return false;
            }
            break;
        }
    }
    return true;
}

static esp_err_t heartbeat_handler(httpd_req_t *request)
{
    char query[38];
    size_t length = httpd_req_get_url_query_len(request);
    if (request->content_len ||
        (length && (length != 37 || httpd_req_get_url_query_str(request, query, sizeof(query)) != ESP_OK ||
                    strncmp(query, "ack=", 4) != 0 || !valid_reading_id(query + 4)))) {
        return json_reply(request, "400 Bad Request", "{\"resultado\":\"ERR\",\"error\":\"TRAMA_INVALIDA\"}");
    }

    xSemaphoreTake(archive_lock, portMAX_DELAY);
    bool saved = !length || acknowledge_reading(query + 4);
    last_heartbeat_us = esp_timer_get_time();
    xSemaphoreGive(archive_lock);

    vehicle_snapshot_t ignored;
    vehicle_read_snapshot(true, &ignored);
    if (!saved) {
        return json_reply(request, "503 Service Unavailable", "{\"resultado\":\"ERR\",\"error\":\"FLASH_NO_DISPONIBLE\"}");
    }
    return json_reply(request, "200 OK", "{\"resultado\":\"ACK\"}");
}

static esp_err_t light_handler(httpd_req_t *request)
{
    char query[24];
    if (request->content_len || httpd_req_get_url_query_str(request, query, sizeof(query)) != ESP_OK ||
        strncmp(query, "valor=", 6) != 0 || query[6] == '\0') {
        return json_reply(request, "400 Bad Request", "{\"resultado\":\"ERR\",\"error\":\"TRAMA_INVALIDA\"}");
    }
    if (strcmp(query + 6, "0") != 0 && strcmp(query + 6, "1") != 0) {
        return json_reply(request, "422 Unprocessable Content", "{\"resultado\":\"ERR\",\"error\":\"VALOR_FUERA_DE_RANGO\"}");
    }
    vehicle_set_light(query[6] == '1');
    return json_reply(request, "200 OK", "{\"resultado\":\"ACK\"}");
}

static esp_err_t unknown_route(httpd_req_t *request, httpd_err_code_t error)
{
    (void)error;
    return json_reply(request, "404 Not Found", "{\"resultado\":\"ERR\",\"error\":\"COMANDO_DESCONOCIDO\"}");
}

esp_err_t es3_register(httpd_handle_t server)
{
    esp_err_t result = nvs_open("auto_iot_es3", NVS_READWRITE, &storage);
    if (result != ESP_OK) {
        return result;
    }

    size_t size = sizeof(archive);
    result = nvs_get_blob(storage, ARCHIVE_KEY, &archive, &size);
    if (result != ESP_ERR_NVS_NOT_FOUND &&
        (result != ESP_OK || size != sizeof(archive) || archive.count > ARCHIVE_CAPACITY)) {
        nvs_close(storage);
        return ESP_FAIL;
    }
    boot_id = (uint64_t)esp_random() << 32 | esp_random();
    last_heartbeat_us = esp_timer_get_time();
    archive_lock = xSemaphoreCreateMutex();
    stream_slot = xSemaphoreCreateBinary();
    if (!archive_lock || !stream_slot) {
        result = ESP_ERR_NO_MEM;
        goto fail;
    }
    xSemaphoreGive(stream_slot);

    const httpd_uri_t routes[] = {
        {.uri = "/eventos", .method = HTTP_GET, .handler = stream_handler},
        {.uri = "/latido", .method = HTTP_POST, .handler = heartbeat_handler},
        {.uri = "/luz", .method = HTTP_POST, .handler = light_handler},
    };
    for (size_t i = 0; i < sizeof(routes) / sizeof(routes[0]); ++i) {
        result = httpd_register_uri_handler(server, &routes[i]);
        if (result != ESP_OK) goto fail;
    }
    result = httpd_register_err_handler(server, HTTPD_404_NOT_FOUND, unknown_route);
    if (result != ESP_OK) goto fail;
    if (xTaskCreate(archive_task, "historial", 3072, NULL, 3, NULL) != pdPASS) {
        result = ESP_ERR_NO_MEM;
        goto fail;
    }
    return ESP_OK;

fail:
    if (archive_lock) vSemaphoreDelete(archive_lock);
    if (stream_slot) vSemaphoreDelete(stream_slot);
    nvs_close(storage);
    return result;
}
