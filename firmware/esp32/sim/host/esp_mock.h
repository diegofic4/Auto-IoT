#pragma once

#include <assert.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>

typedef int esp_err_t;
#define ESP_OK 0
#define ESP_ERR_INVALID_ARG 1
#define ESP_ERR_INVALID_STATE 2
#define ESP_ERR_NO_MEM 3
#define ESP_ERR_NVS_NO_FREE_PAGES 4
#define ESP_ERR_NVS_NEW_VERSION_FOUND 5
#define ESP_ERROR_CHECK(call) do { if ((call) != ESP_OK) abort(); } while (0)
#define ESP_RETURN_ON_ERROR(call, tag, message) do { \
    (void)(tag); esp_err_t result = (call); if (result != ESP_OK) return result; \
} while (0)
#define ESP_LOGI(tag, ...) ((void)(tag))
#define ESP_LOGW(tag, ...) ((void)(tag))
#define ESP_LOGE(tag, ...) ((void)(tag))
const char *esp_err_to_name(esp_err_t error);

typedef int gpio_num_t;
enum {
    GPIO_NUM_4 = 4, GPIO_NUM_5 = 5, GPIO_NUM_18 = 18,
    GPIO_NUM_25 = 25, GPIO_NUM_26 = 26, GPIO_NUM_27 = 27,
    GPIO_MODE_INPUT, GPIO_MODE_OUTPUT,
};
esp_err_t gpio_reset_pin(gpio_num_t pin);
esp_err_t gpio_set_level(gpio_num_t pin, unsigned level);
esp_err_t gpio_set_direction(gpio_num_t pin, int mode);
int gpio_get_level(gpio_num_t pin);
int64_t esp_timer_get_time(void);
void esp_rom_delay_us(uint32_t duration);

typedef struct { bool held; } *SemaphoreHandle_t;
typedef uint32_t TickType_t;
#define portMAX_DELAY UINT32_MAX
#define pdMS_TO_TICKS(ms) (ms)
#define pdPASS 1
SemaphoreHandle_t xSemaphoreCreateMutex(void);
void xSemaphoreTake(SemaphoreHandle_t mutex, TickType_t wait);
void xSemaphoreGive(SemaphoreHandle_t mutex);
void vSemaphoreDelete(SemaphoreHandle_t mutex);
int xTaskCreate(void (*task)(void *), const char *name, unsigned stack,
                void *args, unsigned priority, void *task_handle);
TickType_t xTaskGetTickCount(void);
void vTaskDelayUntil(TickType_t *wake, TickType_t period);

typedef const char *esp_event_base_t;
typedef void (*esp_event_handler_t)(void *, esp_event_base_t, int32_t, void *);
#define WIFI_EVENT "wifi"
#define IP_EVENT "ip"
enum { ESP_EVENT_ANY_ID, WIFI_EVENT_AP_STACONNECTED, WIFI_EVENT_AP_STADISCONNECTED,
       WIFI_EVENT_AP_STOP, IP_EVENT_AP_STAIPASSIGNED };
esp_err_t esp_event_loop_create_default(void);
void esp_event_loop_delete_default(void);
esp_err_t esp_event_handler_register(esp_event_base_t base, int32_t event,
                                     esp_event_handler_t handler, void *arg);
esp_err_t esp_event_handler_unregister(esp_event_base_t base, int32_t event,
                                       esp_event_handler_t handler);

typedef struct { uint8_t address[4]; } mock_ipv4_t;
typedef struct { mock_ipv4_t ip; } esp_netif_ip_info_t;
typedef struct { mock_ipv4_t ip; } ip_event_ap_staipassigned_t;
typedef struct { bool enabled; } esp_netif_t;
#define IPSTR "%u.%u.%u.%u"
#define IP2STR(p) (p)->address[0], (p)->address[1], (p)->address[2], (p)->address[3]
esp_err_t esp_netif_init(void);
esp_netif_t *esp_netif_create_default_wifi_ap(void);
void esp_netif_destroy_default_wifi(esp_netif_t *netif);
esp_err_t esp_netif_get_ip_info(esp_netif_t *netif, esp_netif_ip_info_t *ip);

typedef struct { int nvs_enable; } wifi_init_config_t;
#define WIFI_INIT_CONFIG_DEFAULT() ((wifi_init_config_t){ .nvs_enable = 1 })
typedef struct {
    struct {
        uint8_t ssid[32], password[64], ssid_len, channel, authmode, pairwise_cipher, max_connection;
        struct { bool capable, required; } pmf_cfg;
    } ap;
} wifi_config_t;
enum {
    WIFI_STORAGE_RAM,
    WIFI_MODE_AP,
    WIFI_IF_AP,
    WIFI_AUTH_WPA2_PSK,
    WIFI_CIPHER_TYPE_CCMP,
    WIFI_PS_NONE,
};
esp_err_t nvs_flash_init(void);
esp_err_t nvs_flash_erase(void);
esp_err_t esp_wifi_init(const wifi_init_config_t *config);
esp_err_t esp_wifi_set_storage(int mode);
esp_err_t esp_wifi_set_mode(int mode);
esp_err_t esp_wifi_set_config(int interface, const wifi_config_t *config);
esp_err_t esp_wifi_start(void);
esp_err_t esp_wifi_set_ps(int type);
void esp_wifi_stop(void);
void esp_wifi_deinit(void);

typedef void *httpd_handle_t;
typedef struct { unsigned server_port; bool lru_purge_enable; } httpd_config_t;
#define HTTPD_DEFAULT_CONFIG() ((httpd_config_t){ .server_port = 80 })
typedef struct {
    void *user_ctx;
    size_t content_len;
    size_t query_len;
    int status;
    char *body;
    size_t body_capacity;
} httpd_req_t;
typedef struct {
    const char *uri;
    int method;
    esp_err_t (*handler)(httpd_req_t *);
    void *user_ctx;
} httpd_uri_t;
enum { HTTP_GET, HTTPD_500_INTERNAL_SERVER_ERROR = 500 };
esp_err_t httpd_start(httpd_handle_t *server, const httpd_config_t *config);
void httpd_stop(httpd_handle_t server);
esp_err_t httpd_register_uri_handler(httpd_handle_t server, const httpd_uri_t *route);
size_t httpd_req_get_url_query_len(httpd_req_t *request);
esp_err_t httpd_resp_set_status(httpd_req_t *request, const char *status);
esp_err_t httpd_resp_set_type(httpd_req_t *request, const char *type);
esp_err_t httpd_resp_set_hdr(httpd_req_t *request, const char *name, const char *value);
esp_err_t httpd_resp_send(httpd_req_t *request, const char *body, size_t length);
esp_err_t httpd_resp_send_err(httpd_req_t *request, int status, const char *body);
