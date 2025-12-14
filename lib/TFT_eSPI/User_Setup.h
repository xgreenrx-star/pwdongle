// User_Setup.h for ESP32-S3-LCD-1.47 with ST7789 driver
// Configured for standard pin mapping on this board

#define ST7789_DRIVER       // ST7789 controller
#define TFT_WIDTH  172      // ST7789 pixel width (1.47" typical)
#define TFT_HEIGHT 320      // ST7789 pixel height (1.47" typical)
#define TFT_ROTATION 0      // Portrait mode

// SPI pins (HSPI on ESP32-S3)
#define TFT_MOSI  45  // GPIO 45 (OSI / SDA on some boards)
#define TFT_SCLK  40  // GPIO 40 (SCLK)
#define TFT_CS    42  // GPIO 42 (Chip Select)
#define TFT_DC    41  // GPIO 41 (Data/Command)
#define TFT_RST   -1  // Reset not used (or define if your board has it)

// Optional backlight control
#define TFT_BL    46  // GPIO 46 (backlight enable, if available)
#define TFT_BACKLIGHT_ON HIGH

// SPI clock speed (MHz) - ST7789 typically supports up to 80 MHz
#define SPI_FREQUENCY  40000000

// Disable touch for now (can add I2C touch later if needed)
#define TOUCH_CS -1

// Use HSPI (SPI2) for display, leaving VSPI (SPI3) free if needed
#define USE_HSPI_PORT

// Display color mode
#define TFT_BL_ON HIGH

// Minimal config - add other defines as needed
