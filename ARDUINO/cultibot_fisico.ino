
// Habilitacion de debug para la impresion por el puerto serial ...
//----------------------------------------------
#define SERIAL_DEBUG_ENABLED 1

#if SERIAL_DEBUG_ENABLED
#define DebugPrint(str)  \
  {                      \
    Serial.println(str); \
  }
#else
#define DebugPrint(str)
#endif

#define DebugPrintEstado(state, event)                          \
  {                                                             \
    String est = state;                                         \
    String evt = event;                                         \
    String str;                                                 \
    str = "STATE-> [" + est + "]: " + "EVENT-> [" + evt + "]."; \
    DebugPrint(str);                                            \
  }
//----------------------------------------------

// <-------------------------LIBRERIAS------------------------->

#include <DHT.h>            // Manejo de sensor de humedad y temperatura
#include <SoftwareSerial.h> // Comunicacion serial con modulo Blueetoth

// <-------------------------CONSTANTES------------------------->

/********************************** SENSORES **********************************/
//#define TEMPERATURE_SENSOR_PIN A2
//#define HUMIDITY_SENSOR_PIN A2
#define DHT11_PIN 7 // Temperatura y humedad
#define LIGHT_SENSOR_PIN A1

#define DHTTYPE DHT11 // Indico tipo de sensor DHT usado

/********************************* ACTUADORES *********************************/
#define WATER_LED_PIN 2
#define COOLER_TRANSISTOR_PIN 9
#define LIGHTBULB_TRANSISTOR_PIN 3

/***************************** UMBRALES GENERALES *****************************/
#define MIN_VALUE 0
#define MAX_VALUE 100

#define LIGHT_MIN_VALUE 0
#define LIGHT_MAX_VALUE 420

#define LIGHTBULB_MIN_VALUE 0
#define LIGHTBULB_MAX_VALUE 255

#define COOLER_OFF 0
#define COOLER_25 64
#define COOLER_50 128
#define COOLER_75 192
#define COOLER_100 255

//#define TEMP_SENSOR_PIN_CONST 0.48828125
//#define TEMP_SENSOR_PIN_CONST2 50

/*************************** UMBRALES PARTICULARES ****************************/
#define CRITIC_HIGH_TEMP_100 100
#define CRITIC_HIGH_TEMP_75 75
#define CRITIC_HIGH_TEMP_50 50
#define CRITIC_HIGH_TEMP_25 25
#define CRITIC_LOW_TEMP 20

#define CRITIC_LOW_LIGHT 25

#define CRITIC_LOW_HUMIDITY 80

#define INITIAL_TEMP -1500
#define INITIAL_HUMIDITY -1500
#define INITIAL_LIGHT -1

/*************************** OTRAS CONSTANTES ****************************/
// TEMPORIZADOR
#define REGISTRO_TCCR1A 0b00000000
#define REGISTRO_TCCR1B 0b00001101
#define REGISTRO_OCR1A 3905.25   // Interrupcion cada 0.25 segundo
#define LIMIT_SOFTWARE_TEMP = 20 // 20*0.25=5s

// MAQUINA DE ESTADOS
#define STATES 5
#define EVENTS 5

// COMUNICACION SERIAL
#define PIN_SERIAL_BLUETOOTH_RX 10
#define PIN_SERIAL_BLUETOOTH_TX 11
#define BAUDRATE 9600

/******************** DECLARACION MAQUINA DE ESTADOS *********************/
enum enum_states
{
  INITIAL,
  IDLE,
  MONITORING,
  EVALUATING,
  ADECUATING
};
String states[] = {"INITIAL", "IDLE", "MONITORING", "EVALUATING", "ADECUATING"};

enum enum_events
{
  CONTINUE,
  MONITOR_NEEDED,
  MONITORED,
  EVALUATED,
  ADECUATED
};
String events[] = {"CONTINUE", "MONITOR_NEEDED", "MONITORED", "EVALUATED", "ADECUATED"};

void ini();
void check_if_monitor_is_needed();
bool is_valid_transition(int, int);
void finite_state_machine();
void init_();
void none();
void wait();
void monitor_plant();
void monitored_plant();
void adecuate();
void idle_again();
void error();

typedef void (*transition)();

transition transitions[STATES][EVENTS] = {
    {init_, error, error, error, error},          // state INITIAL
    {wait, monitor_plant, error, error, wait},    // state IDLE
    {none, error, monitored_plant, error, error}, // state MONITORING
    {none, error, error, adecuate, error},        // state EVALUATING
    {none, error, error, error, idle_again}       // state ADECUATING
                                                  // CONTINUE, MONITOR_NEEDED, MONITORED, EVALUATED, ADECUATED
};

typedef struct
{
  int type;
  int light;
  float temperature;
  float humidity;
  bool light_ok;
  bool temperature_ok;
  bool humidity_ok;
} struct_event;

int state;
struct_event event;

/******************** DECLARACIONES VARIAS *********************/

DHT dht(DHT11_PIN, DHTTYPE); // Inicializamos el sensor DHT11

// Inicializamos canal serial con el modulo bluetooth
SoftwareSerial BTserial(PIN_SERIAL_BLUETOOTH_RX, PIN_SERIAL_BLUETOOTH_TX);
char serial_msg = ' ';

// Inicializacion variables para comparacion de lecturas
int prev_temp;
int prev_light;
int prev_humidity;

// Variables temporizador
int software_timer_counter = 0;
bool timeout;

/******************** FUNCIONES ARDUINO *********************/

void setup()
{
  // Inicializacion del sw
  ini();

  // Inicialización de los contadores para el timer por hw
  TCCR1A = REGISTRO_TCCR1A;
  TCCR1B = REGISTRO_TCCR1B;
  OCR1A = REGISTRO_OCR1A;
  TIMSK1 = bit(OCIE1A);
  sei();
}

void loop()
{
  if (BTserial.available())
  {
    serial_msg = BTserial.read();
    Serial.write(serial_msg);
  }
  if (Serial.available())
  {
    serial_msg = Serial.read();
    Serial.write(serial_msg);
    BTserial.write(serial_msg);
  }
  finite_state_machine();
}

ISR(TIMER1_COMPA_vect)
{
  if (software_timer_counter > 20)
  {
    timeout = true;
    software_timer_counter = 0;
  }
  else
  {
    software_timer_counter++;
  }
}

// <-------------------------FUNCIONES SENSORES------------------------->

float read_temperature()
{
  // Lectura y cálculo para obtener la temperatura del ambiente
  float current_temperature = dht.readTemperature();

  // Reviso si todo esta ok al leer
  if (isnan(current_temperature))
  {
    //    DebugPrint("Error al obtener temperatura del DHT11");
    //    return prev_temp;
    return 18;
  }
  else
  {
    // DEBUG
    DebugPrint("Temperatura leida por el DHT11: ");
    DebugPrint(current_temperature);
  }

  return current_temperature;

  /** PREVIA LECTURA DE TEMPERATURA (EN TINKERCAD)
   * int temp_value = analogRead(sensor);
   *  return TEMP_SENSOR_PIN_CONST * temp_value - TEMP_SENSOR_PIN_CONST2;
   */
}

float read_humidity()
{
  // Lectura de la humedad del ambiente
  float current_humidity = dht.readHumidity();

  // Reviso si todo esta ok al leer
  if (isnan(current_humidity))
  {
    DebugPrint("Error al obtener humedad del DHT11");
    //    return prev_humidity;
    return 95;
  }
  else
  {
    // DEBUG
    DebugPrint("Humedad leida por el DHT11: ");
    DebugPrint(current_humidity);
  }

  return current_humidity;
  /** PREVIA LECTURA DE HUMEDAD (EN TINKERCAD)
   *  return digitalRead(sensor);
   */
}

int read_light(int sensor)
{
  // Realizamos un mapeo para tener el porcentaje de luz recibido
  int current_light = analogRead(sensor);

  // Reviso si todo esta ok al leer
  if (isnan(current_light))
  {
    DebugPrint("Error al obtener luz del DHT11");
    return prev_light;
  }
  else
  {
    // DEBUG
    DebugPrint("Luz leida por el fotosensor: ");
    DebugPrint(map(current_light, LIGHT_MIN_VALUE, LIGHT_MAX_VALUE, MIN_VALUE, MAX_VALUE));
  }

  return map(current_light, LIGHT_MIN_VALUE, LIGHT_MAX_VALUE, MIN_VALUE, MAX_VALUE);
}

bool check_temperature()
{
  // Falso si la temperatura esta en la region critica
  return event.temperature > CRITIC_LOW_TEMP && event.temperature < CRITIC_HIGH_TEMP_25;
}

bool check_light()
{
  // Falso si se tiene un nivel de luz bajo
  return event.light > CRITIC_LOW_LIGHT;
}

bool check_humidity()
{
  // Falso si se tiene un nivel de humedad bajo
  return event.humidity > CRITIC_LOW_HUMIDITY;
}

void turn_off_triggers()
{
  // Para indicar que se adecuo las condiciones ambientales
  event.type = ADECUATED;
  digitalWrite(LIGHTBULB_TRANSISTOR_PIN, LOW);
  digitalWrite(WATER_LED_PIN, LOW);
  analogWrite(COOLER_TRANSISTOR_PIN, COOLER_OFF);
}

void turn_on_triggers()
{
  if (!event.humidity_ok)
  {
    digitalWrite(WATER_LED_PIN, HIGH);
  }
  else
  {
    digitalWrite(WATER_LED_PIN, LOW);
  }

  if (event.temperature > CRITIC_HIGH_TEMP_25)
  {
    DebugPrint("Estoy prendiendo el ventilador");
    int cooler_intensity = get_cooler_intensity(event.temperature);
    analogWrite(COOLER_TRANSISTOR_PIN, cooler_intensity);
  }
  else
  {
    DebugPrint("Estoy apagando el ventilador");
    analogWrite(COOLER_TRANSISTOR_PIN, COOLER_OFF);

    if (event.temperature < CRITIC_LOW_TEMP)
    {
      digitalWrite(LIGHTBULB_TRANSISTOR_PIN, HIGH);
    }
    else
    {
      digitalWrite(LIGHTBULB_TRANSISTOR_PIN, LOW);
    }
  }

  // Segun el estado de los sensores se apagan o se prenden
  if (!event.light_ok)
  {
    digitalWrite(LIGHTBULB_TRANSISTOR_PIN, HIGH);
  }
  else
  {
    // Solo apago la luz si NO la prendi por temperatura previamente
    int newVal = (event.temperature < CRITIC_LOW_TEMP) ? HIGH : LOW;
    digitalWrite(LIGHTBULB_TRANSISTOR_PIN, newVal);
  }

  // Para indicar que se adecuo las condiciones ambientales
  event.type = ADECUATED;
}

int get_cooler_intensity(int temperature)
{
  // Calculo de la intensidad del cooler
  if (temperature > CRITIC_HIGH_TEMP_100)
  {
    return COOLER_100;
  }
  if (temperature > CRITIC_HIGH_TEMP_75)
  {
    return COOLER_75;
  }
  if (temperature > CRITIC_HIGH_TEMP_50)
  {
    return COOLER_50;
  }
  return COOLER_25;
}

// <------------------------- MAQUINA DE ESTADOS ------------------------->

void ini()
{
  Serial.begin(BAUDRATE);
  BTserial.begin(BAUDRATE);
  // Inicializacion de los pines
  pinMode(LIGHT_SENSOR_PIN, INPUT);
  pinMode(DHT11_PIN, INPUT);
  pinMode(LIGHTBULB_TRANSISTOR_PIN, OUTPUT);
  pinMode(COOLER_TRANSISTOR_PIN, OUTPUT); // Pin como salida digital para PWM
  pinMode(WATER_LED_PIN, OUTPUT);
  dht.begin();
  // Inicializacion de variables en valores imposibles

  // Sensores toman valores imposibles como iniciales
  prev_temp = INITIAL_TEMP;
  prev_light = INITIAL_LIGHT;
  prev_humidity = INITIAL_HUMIDITY;

  timeout = false;
  state = INITIAL;
  event.type = CONTINUE;
}

void check_if_monitor_is_needed()
{
  // Solo para verificar si ocurrio la interrupcion.
  if (timeout)
  {
    timeout = false;
    event.type = MONITOR_NEEDED;
  }
  else
  {
    event.type = CONTINUE;
  }
}

bool is_valid_transition(int event, int state)
{
  return (event >= 0) && (event < EVENTS) && (state >= 0) && (state < STATES);
}

void finite_state_machine()
{
  if (!is_valid_transition(event.type, state))
  {
    DebugPrint("Error al transicionar de estados");
    return;
  }

  if (event.type != CONTINUE)
  {
    //        DebugPrintEstado(states[event.type], events[state]);
  }
  transitions[state][event.type]();
}

void init_()
{
  state = IDLE;
}

void none()
{
}

void wait()
{
  check_if_monitor_is_needed();
}

void monitor_plant()
{
  check_if_monitor_is_needed();

  // Se dispara la lectura de los sensores
  state = MONITORING;

  event.light = read_light(LIGHT_SENSOR_PIN);
  event.temperature = read_temperature();
  event.humidity = read_humidity();

  // Para indicar que se realizo el senseo
  event.type = MONITORED;
}

void monitored_plant()
{
  // Con este evento se dispara la verificacion de los valores obtenidos
  state = EVALUATING;

  event.light_ok = check_light();
  event.temperature_ok = check_temperature();
  event.humidity_ok = check_humidity();

  // Para indicar que se realizo la verificacion de datos
  event.type = EVALUATED;
}

void adecuate()
{
  state = ADECUATING;

  // Con este evento se dispara la actualizacion de los actuadores
  if (event.temperature_ok && event.light_ok && event.humidity_ok)
  {
    turn_off_triggers();
  }
  else
  {
    turn_on_triggers();
  }
}

void idle_again()
{
  state = IDLE;
}

void error()
{
  DebugPrint("Ocurrio un error en la transicion de estados.")
}