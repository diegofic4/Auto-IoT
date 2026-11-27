#include <stdio.h>
#include <string.h>

#include "esp_mock.h"
#include "../../src/network.h"
#include "../../src/vehicle.c"

#ifdef _WIN32
#define BENCH_EXPORT __declspec(dllexport)
#else
#define BENCH_EXPORT __attribute__((visibility("default")))
#endif

static int64_t clock_us;
static int64_t echo_start;
static int64_t echo_end;
static int echo_width;
static unsigned levels[40];
static esp_netif_t ap;
static bool wifi_started;
static bool station_present;
static esp_event_handler_t wifi_event;
static esp_event_handler_t ip_event;
static httpd_uri_t all_routes[12];
static size_t route_count;

BENCH_EXPORT int bench_start(void)
{
    int result = vehicle_start();
    return result == ESP_OK ? auto_iot_network_start("fixture-only") : result;
}

BENCH_EXPORT void bench_station(bool connected)
{
    if (connected == station_present) return;
    station_present = connected;
    wifi_event(NULL, WIFI_EVENT, connected ? WIFI_EVENT_AP_STACONNECTED : WIFI_EVENT_AP_STADISCONNECTED, NULL);
    if (connected) {
        ip_event_ap_staipassigned_t assignment = {.ip.address = {192, 168, 4, 2}};
        ip_event(NULL, IP_EVENT, IP_EVENT_AP_STAIPASSIGNED, &assignment);
    }
}

BENCH_EXPORT void bench_sample(uint32_t elapsed_us, float cm)
{
    clock_us += elapsed_us;
    echo_width = cm >= 0 && cm <= 500 ? (int)(cm * 58) : 0;
    sample_sensor();
}

BENCH_EXPORT unsigned bench_pins(void)
{
    return levels[GPIO_NUM_25] << 2 | levels[GPIO_NUM_26] << 1 | levels[GPIO_NUM_27];
}

BENCH_EXPORT int bench_http(const char *path, size_t content_len, char *body, size_t capacity)
{
    const char *query = strchr(path, '?');
    size_t path_length = query ? (size_t)(query - path) : strlen(path);
    for (size_t index = 0; index < route_count; ++index) {
        const httpd_uri_t *route = &all_routes[index];
        if (strlen(route->uri) == path_length && !strncmp(route->uri, path, path_length)) {
            httpd_req_t request = {
                .user_ctx = route->user_ctx,
                .content_len = content_len,
                .query_len = query ? strlen(query + 1) : 0,
                .status = 200,
                .body = body,
                .body_capacity = capacity,
            };
            return route->handler(&request) == ESP_OK ? request.status : 500;
        }
    }
    snprintf(body, capacity, "{\"resultado\":\"ERR\",\"error\":\"RUTA_NO_EXISTE\"}");
    return 404;
}

esp_err_t gpio_reset_pin(gpio_num_t pin) { levels[pin] = 0; return ESP_OK; }
esp_err_t gpio_set_direction(gpio_num_t pin, int mode) { (void)pin; (void)mode; return ESP_OK; }
esp_err_t gpio_set_level(gpio_num_t pin, unsigned level)
{
    if (pin == GPIO_NUM_5 && levels[pin] && !level) {
        echo_start = clock_us + 200;
        echo_end = echo_start + echo_width;
    }
    levels[pin] = level;
    return ESP_OK;
}
int gpio_get_level(gpio_num_t pin)
{
    return pin == GPIO_NUM_18 ? clock_us >= echo_start && clock_us < echo_end : (int)levels[pin];
}
int64_t esp_timer_get_time(void) { return clock_us++; }
void esp_rom_delay_us(uint32_t us) { clock_us += us; }

SemaphoreHandle_t xSemaphoreCreateMutex(void) { return calloc(1, sizeof(*((SemaphoreHandle_t)0))); }
void xSemaphoreTake(SemaphoreHandle_t mutex, TickType_t wait)
{
    (void)wait;
    assert(mutex && !mutex->held);
    mutex->held = true;
}
void xSemaphoreGive(SemaphoreHandle_t mutex)
{
    assert(mutex && mutex->held);
    mutex->held = false;
}
void vSemaphoreDelete(SemaphoreHandle_t mutex) { free(mutex); }
int xTaskCreate(void (*task)(void *), const char *name, unsigned stack, void *args,
                unsigned priority, void *task_handle)
{
    // The harness advances the same sensor step explicitly on the simulated clock.
    (void)task; (void)name; (void)stack; (void)args; (void)priority; (void)task_handle;
    return pdPASS;
}
TickType_t xTaskGetTickCount(void) { return (TickType_t)(clock_us / 1000); }
void vTaskDelayUntil(TickType_t *wake, TickType_t period) { *wake += period; }

const char *esp_err_to_name(esp_err_t error) { (void)error; return "MOCK_ERROR"; }
esp_err_t esp_netif_init(void) { return ESP_OK; }
esp_err_t esp_event_loop_create_default(void) { return ESP_OK; }
void esp_event_loop_delete_default(void) {}
esp_netif_t *esp_netif_create_default_wifi_ap(void) { return &ap; }
void esp_netif_destroy_default_wifi(esp_netif_t *netif) { netif->enabled = false; }
esp_err_t esp_netif_get_ip_info(esp_netif_t *netif, esp_netif_ip_info_t *ip)
{
    if (!netif->enabled) return ESP_ERR_INVALID_STATE;
    *ip = (esp_netif_ip_info_t){.ip.address = {192, 168, 4, 1}};
    return ESP_OK;
}
esp_err_t esp_event_handler_register(esp_event_base_t base, int32_t event,
                                     esp_event_handler_t handler, void *arg)
{
    (void)event; (void)arg;
    if (!strcmp(base, WIFI_EVENT)) wifi_event = handler;
    else ip_event = handler;
    return ESP_OK;
}
esp_err_t esp_event_handler_unregister(esp_event_base_t base, int32_t event,
                                       esp_event_handler_t handler)
{
    (void)event; (void)handler;
    if (!strcmp(base, WIFI_EVENT)) wifi_event = NULL;
    else ip_event = NULL;
    return ESP_OK;
}

esp_err_t nvs_flash_init(void) { return ESP_OK; }
esp_err_t nvs_flash_erase(void) { return ESP_OK; }
esp_err_t esp_wifi_init(const wifi_init_config_t *config) { return config->nvs_enable ? ESP_ERR_INVALID_ARG : ESP_OK; }
esp_err_t esp_wifi_set_storage(int mode) { return mode == WIFI_STORAGE_RAM ? ESP_OK : ESP_ERR_INVALID_ARG; }
esp_err_t esp_wifi_set_mode(int mode) { return mode == WIFI_MODE_AP ? ESP_OK : ESP_ERR_INVALID_ARG; }
esp_err_t esp_wifi_set_config(int interface, const wifi_config_t *config)
{
    return interface == WIFI_IF_AP && config->ap.authmode == WIFI_AUTH_WPA2_PSK &&
           config->ap.pairwise_cipher == WIFI_CIPHER_TYPE_CCMP &&
           config->ap.max_connection == 4 && !config->ap.pmf_cfg.required &&
           !strcmp((const char *)config->ap.ssid, AUTO_IOT_AP_SSID)
           ? ESP_OK : ESP_ERR_INVALID_ARG;
}
esp_err_t esp_wifi_start(void) { wifi_started = ap.enabled = true; return ESP_OK; }
esp_err_t esp_wifi_set_ps(int type) { return type == WIFI_PS_NONE ? ESP_OK : ESP_ERR_INVALID_ARG; }
void esp_wifi_stop(void) { wifi_started = ap.enabled = false; }
void esp_wifi_deinit(void) {}

esp_err_t httpd_start(httpd_handle_t *server, const httpd_config_t *config)
{
    if (!wifi_started || config->server_port != 80) return ESP_ERR_INVALID_STATE;
    *server = &route_count;
    return ESP_OK;
}
void httpd_stop(httpd_handle_t server) { (void)server; route_count = 0; }
esp_err_t httpd_register_uri_handler(httpd_handle_t server, const httpd_uri_t *route)
{
    (void)server;
    if (route_count == sizeof(all_routes) / sizeof(all_routes[0])) return ESP_ERR_NO_MEM;
    all_routes[route_count++] = *route;
    return ESP_OK;
}
size_t httpd_req_get_url_query_len(httpd_req_t *request) { return request->query_len; }
esp_err_t httpd_resp_set_status(httpd_req_t *request, const char *status) { request->status = atoi(status); return ESP_OK; }
esp_err_t httpd_resp_set_type(httpd_req_t *request, const char *type) { (void)request; (void)type; return ESP_OK; }
esp_err_t httpd_resp_set_hdr(httpd_req_t *request, const char *name, const char *value)
{
    (void)request; (void)name; (void)value; return ESP_OK;
}
esp_err_t httpd_resp_send(httpd_req_t *request, const char *body, size_t length)
{
    if (length >= request->body_capacity) return ESP_ERR_NO_MEM;
    memcpy(request->body, body, length);
    request->body[length] = '\0';
    return ESP_OK;
}
esp_err_t httpd_resp_send_err(httpd_req_t *request, int status, const char *body)
{
    request->status = status;
    return httpd_resp_send(request, body, strlen(body));
}
