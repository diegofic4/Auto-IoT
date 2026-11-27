#include "network.h"

#include <stdbool.h>
#include <stdio.h>
#include <string.h>

#include "esp_check.h"
#include "esp_event.h"
#include "esp_http_server.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "esp_wifi.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "nvs_flash.h"
#include "sdkconfig.h"

#include "vehicle.h"
#ifndef AUTO_IOT_HOST_SIM
#include "es3.h"
#endif

static const char *TAG = "auto_iot_wifi";
static const char AP_SSID[] = AUTO_IOT_AP_SSID;

static SemaphoreHandle_t session_mutex;
static size_t connected_stations;
static bool telemetry_logged;

typedef struct {
    const char *path;
    vehicle_command_t command;
} command_route_t;

static const command_route_t COMMAND_ROUTES[] = {
    {"/adelante", VEHICLE_FORWARD},
    {"/atras", VEHICLE_REVERSE},
    {"/izquierda", VEHICLE_LEFT},
    {"/derecha", VEHICLE_RIGHT},
    {"/parar", VEHICLE_STOP},
    {"/flash", VEHICLE_FLASH},
};

static esp_err_t send_snapshot(httpd_req_t *request, const vehicle_snapshot_t *snapshot,
                               const char *status, const char *result, const char *error)
{
    char json[256];
    float distance = snapshot->distance_valid ? snapshot->distance_cm : 0.0f;
    // Motor and stop labels are firmware constants, not arbitrary request strings.
    int size = snprintf(json, sizeof(json),
                        "{\"distancia\":%.2f,\"distanciaValida\":%s,\"estadoMotor\":\"%s\","
                        "\"frenoActivo\":%s,\"motivoFreno\":\"%s\",\"resultado\":\"%s\",\"error\":\"%s\"}",
                        (double)distance, snapshot->distance_valid ? "true" : "false",
                        snapshot->motor_state, snapshot->brake_active ? "true" : "false",
                        snapshot->stop_reason ? snapshot->stop_reason : "", result, error);
    if (size < 0 || (size_t)size >= sizeof(json)) {
        return httpd_resp_send_err(request, HTTPD_500_INTERNAL_SERVER_ERROR, "Respuesta demasiado larga");
    }

    ESP_RETURN_ON_ERROR(httpd_resp_set_status(request, status), TAG, "Estado HTTP");
    ESP_RETURN_ON_ERROR(httpd_resp_set_type(request, "application/json"), TAG, "Tipo HTTP");
    ESP_RETURN_ON_ERROR(httpd_resp_set_hdr(request, "Cache-Control", "no-store"), TAG, "Cache HTTP");
    ESP_RETURN_ON_ERROR(httpd_resp_set_hdr(request, "Access-Control-Allow-Origin", "*"), TAG, "CORS HTTP");
    return httpd_resp_send(request, json, size);
}

static esp_err_t telemetry_handler(httpd_req_t *request)
{
    vehicle_snapshot_t snapshot;
    xSemaphoreTake(session_mutex, portMAX_DELAY);
    vehicle_read_snapshot(connected_stations > 0, &snapshot);
    bool first_request = !telemetry_logged;
    telemetry_logged = true;
    xSemaphoreGive(session_mutex);

    if (first_request) {
        printf("HTTP GET /telemetria recibido\n");
    }
    return send_snapshot(request, &snapshot, "200 OK", "ACK", "");
}

static esp_err_t command_handler(httpd_req_t *request)
{
    const command_route_t *route = request->user_ctx;
    vehicle_snapshot_t snapshot;
    if (request->content_len != 0 || httpd_req_get_url_query_len(request) != 0) {
        vehicle_read_snapshot(false, &snapshot);
        return send_snapshot(request, &snapshot, "400 Bad Request", "ERR", "COMANDO_CON_PARAMETROS");
    }

    xSemaphoreTake(session_mutex, portMAX_DELAY);
    vehicle_command_result_t result = VEHICLE_COMMAND_UNAVAILABLE;
    if (connected_stations > 0) {
        result = vehicle_apply_command(route->command, true, &snapshot);
    } else {
        vehicle_read_snapshot(false, &snapshot);
    }
    xSemaphoreGive(session_mutex);

    switch (result) {
        case VEHICLE_COMMAND_OK:
            return send_snapshot(request, &snapshot, "200 OK", "ACK", "");
        case VEHICLE_COMMAND_BLOCKED:
            return send_snapshot(request, &snapshot, "409 Conflict", "ERR", "FRENO_ACTIVO");
        case VEHICLE_COMMAND_UNSUPPORTED:
            return send_snapshot(request, &snapshot, "422 Unprocessable Content", "ERR", "COMANDO_NO_DISPONIBLE");
        case VEHICLE_COMMAND_UNAVAILABLE:
        default:
            return send_snapshot(request, &snapshot, "503 Service Unavailable", "ERR", "CONTROL_NO_DISPONIBLE");
    }
}

static void on_wifi_event(void *arg, esp_event_base_t base, int32_t event, void *data)
{
    (void)arg;
    (void)base;
    (void)data;
    if (event == WIFI_EVENT_AP_STACONNECTED) {
        xSemaphoreTake(session_mutex, portMAX_DELAY);
        ++connected_stations;
        telemetry_logged = false;
        size_t active = connected_stations;
        xSemaphoreGive(session_mutex);
        printf("Wi-Fi: telefono asociado (%u activo(s))\n", (unsigned)active);
    } else if (event == WIFI_EVENT_AP_STADISCONNECTED) {
        xSemaphoreTake(session_mutex, portMAX_DELAY);
        if (connected_stations > 0) {
            --connected_stations;
        }
        size_t active = connected_stations;
        if (active == 0) {
            vehicle_on_remote_disconnect();
        }
        xSemaphoreGive(session_mutex);
        printf("Wi-Fi: enlace con el telefono cerrado (%u activo(s))\n", (unsigned)active);
    } else if (event == WIFI_EVENT_AP_STOP) {
        xSemaphoreTake(session_mutex, portMAX_DELAY);
        connected_stations = 0;
        vehicle_on_remote_disconnect();
        xSemaphoreGive(session_mutex);
        printf("Wi-Fi: punto de acceso detenido\n");
    }
}

static void on_ip_assigned(void *arg, esp_event_base_t base, int32_t event, void *data)
{
    (void)arg;
    (void)base;
    (void)event;
    const ip_event_ap_staipassigned_t *assignment = data;
    printf("Wi-Fi: DHCP asigno " IPSTR " al telefono\n", IP2STR(&assignment->ip));
}

static esp_err_t start_http_server(httpd_handle_t *server)
{
    httpd_config_t config = HTTPD_DEFAULT_CONFIG();
    config.server_port = 80;
    config.lru_purge_enable = true;
#ifndef AUTO_IOT_HOST_SIM
    config.max_uri_handlers = 12;
    config.send_wait_timeout = 2;
#endif

    ESP_RETURN_ON_ERROR(httpd_start(server, &config), TAG, "Iniciar servidor HTTP");
    const httpd_uri_t root = {
        .uri = "/",
        .method = HTTP_GET,
        .handler = telemetry_handler,
    };
    esp_err_t err = httpd_register_uri_handler(*server, &root);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "No se registro %s: %s", root.uri, esp_err_to_name(err));
    }
    const httpd_uri_t telemetry = {
        .uri = "/telemetria",
        .method = HTTP_GET,
        .handler = telemetry_handler,
    };
    if (err == ESP_OK) {
        err = httpd_register_uri_handler(*server, &telemetry);
    }
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "No se registro %s: %s", telemetry.uri, esp_err_to_name(err));
    }
    for (size_t i = 0; err == ESP_OK && i < sizeof(COMMAND_ROUTES) / sizeof(COMMAND_ROUTES[0]); ++i) {
        const httpd_uri_t route = {
            .uri = COMMAND_ROUTES[i].path,
            .method = HTTP_GET,
            .handler = command_handler,
            .user_ctx = (void *)&COMMAND_ROUTES[i],
        };
        err = httpd_register_uri_handler(*server, &route);
        if (err != ESP_OK) {
            ESP_LOGE(TAG, "No se registro %s: %s", route.uri, esp_err_to_name(err));
        }
    }
#ifndef AUTO_IOT_HOST_SIM
    if (err == ESP_OK) {
        err = es3_register(*server);
    }
#endif
    if (err != ESP_OK) {
        httpd_stop(*server);
    }
    return err;
}

static bool valid_password(const char *password, size_t length)
{
    if (length < 8 || length > 63) {
        return false;
    }
    for (size_t i = 0; i < length; ++i) {
        if ((unsigned char)password[i] < 0x20 || (unsigned char)password[i] > 0x7e) {
            return false;
        }
    }
    return true;
}

static esp_err_t configure_access_point(const char *password, size_t length)
{
    wifi_config_t access_point = {0};
    memcpy(access_point.ap.ssid, AP_SSID, sizeof(AP_SSID) - 1);
    access_point.ap.ssid_len = sizeof(AP_SSID) - 1;
    memcpy(access_point.ap.password, password, length);
    access_point.ap.channel = 1;
    access_point.ap.authmode = WIFI_AUTH_WPA2_PSK;
    access_point.ap.pairwise_cipher = WIFI_CIPHER_TYPE_CCMP;
    access_point.ap.max_connection = 4;
    access_point.ap.pmf_cfg.capable = true;
    access_point.ap.pmf_cfg.required = false;

    ESP_RETURN_ON_ERROR(esp_wifi_set_storage(WIFI_STORAGE_RAM), TAG, "Almacenamiento Wi-Fi");
    ESP_RETURN_ON_ERROR(esp_wifi_set_mode(WIFI_MODE_AP), TAG, "Modo de punto de acceso");
    return esp_wifi_set_config(WIFI_IF_AP, &access_point);
}

esp_err_t auto_iot_network_start(const char *password)
{
    const size_t length = strlen(password);
    if (!valid_password(password, length)) {
        ESP_LOGE(TAG, "Configura una clave Wi-Fi de 8 a 63 caracteres ASCII imprimibles");
        return ESP_ERR_INVALID_ARG;
    }

    // La radio requiere NVS inicializada para su calibración RF estable.
    esp_err_t calibration_storage = nvs_flash_init();
    if (calibration_storage != ESP_OK) {
        ESP_LOGW(TAG, "NVS de calibracion no disponible: %s", esp_err_to_name(calibration_storage));
    }
    ESP_RETURN_ON_ERROR(esp_netif_init(), TAG, "Inicializar interfaces de red");
    esp_err_t event_error = esp_event_loop_create_default();
    if (event_error != ESP_OK && event_error != ESP_ERR_INVALID_STATE) {
        return event_error;
    }
    esp_netif_t *netif = esp_netif_create_default_wifi_ap();

    wifi_init_config_t wifi = WIFI_INIT_CONFIG_DEFAULT();
    wifi.nvs_enable = 0;
    esp_err_t err = esp_wifi_init(&wifi);
    if (err != ESP_OK) {
        goto cleanup_netif;
    }
    err = configure_access_point(password, length);
    if (err != ESP_OK) {
        goto cleanup_wifi;
    }

    session_mutex = xSemaphoreCreateMutex();
    if (!session_mutex) {
        err = ESP_ERR_NO_MEM;
        goto cleanup_wifi;
    }
    connected_stations = 0;
    err = esp_event_handler_register(WIFI_EVENT, ESP_EVENT_ANY_ID, on_wifi_event, NULL);
    if (err != ESP_OK) {
        goto cleanup_mutex;
    }
    err = esp_event_handler_register(IP_EVENT, IP_EVENT_AP_STAIPASSIGNED, on_ip_assigned, NULL);
    if (err != ESP_OK) {
        goto cleanup_wifi_events;
    }

    err = esp_wifi_start();
    if (err != ESP_OK) {
        goto cleanup_ip_events;
    }
    // Desactiva el ahorro de energía del módem para evitar pérdidas de ARP/TCP en modo AP.
    esp_err_t ps_err = esp_wifi_set_ps(WIFI_PS_NONE);
    if (ps_err != ESP_OK) {
        ESP_LOGW(TAG, "No se pudo desactivar ahorro de energia Wi-Fi: %s", esp_err_to_name(ps_err));
    }
    httpd_handle_t server = NULL;
    err = start_http_server(&server);
    if (err != ESP_OK) {
        esp_wifi_stop();
        goto cleanup_ip_events;
    }

    esp_netif_ip_info_t ip;
    if (esp_netif_get_ip_info(netif, &ip) == ESP_OK) {
        ESP_LOGI(TAG, "Wi-Fi %s | http://" IPSTR "/telemetria", AP_SSID, IP2STR(&ip.ip));
    } else {
        ESP_LOGI(TAG, "Wi-Fi %s iniciado en el puerto HTTP 80", AP_SSID);
    }
    return ESP_OK;

cleanup_ip_events:
    esp_event_handler_unregister(IP_EVENT, IP_EVENT_AP_STAIPASSIGNED, on_ip_assigned);
cleanup_wifi_events:
    esp_event_handler_unregister(WIFI_EVENT, ESP_EVENT_ANY_ID, on_wifi_event);
cleanup_mutex:
    vSemaphoreDelete(session_mutex);
    session_mutex = NULL;
cleanup_wifi:
    esp_wifi_deinit();
cleanup_netif:
    esp_netif_destroy_default_wifi(netif);
    if (event_error == ESP_OK) {
        esp_event_loop_delete_default();
    }
    ESP_LOGE(TAG, "No se pudo iniciar Wi-Fi: %s", esp_err_to_name(err));
    return err;
}
