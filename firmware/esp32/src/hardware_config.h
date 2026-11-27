#pragma once

#ifdef ARDUINO
#include "../opciones.h"
#else
#include "sdkconfig.h"

#ifdef CONFIG_AUTO_IOT_DRIVE_SERVOS
#define AUTO_IOT_USE_SERVOS 1
#else
#define AUTO_IOT_USE_SERVOS 0
#endif

#ifdef CONFIG_AUTO_IOT_OLED
#define AUTO_IOT_USE_OLED 1
#else
#define AUTO_IOT_USE_OLED 0
#endif
#endif
