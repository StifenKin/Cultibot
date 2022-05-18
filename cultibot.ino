
// Habilitacion de debug para la impresion por el puerto serial ...
//----------------------------------------------
#define SERIAL_DEBUG_ENABLED 0

#if SERIAL_DEBUG_ENABLED
#define DebugPrint(str)      \
    {                        \
        Serial.println(str); \
    }
#else
#define DebugPrint(str)
#endif

#define DebugPrintEstado(state, event)                                 \
    {                                                                  \
        String est = state;                                            \
        String evt = event;                                            \
        String str;                                                    \
        str = "-----------------------------------------------------"; \
        DebugPrint(str);                                               \
        str = "EST-> [" + est + "]: " + "EVT-> [" + evt + "].";        \
        DebugPrint(str);                                               \
        str = "-----------------------------------------------------"; \
        DebugPrint(str);                                               \
    }
//----------------------------------------------

// <-------------------------CONSTANTES------------------------->

/********************************** SENSORES **********************************/
#define TEMPERATURE_SENSOR_PIN A2
#define HUMIDITY_SENSOR_PIN 7
#define LIGHT_SENSOR_PIN A1

/********************************* ACTUADORES *********************************/
#define WATER_LED_PIN 2
#define COOLER_TRANSISTOR_PIN 9
#define LIGHTBULB_TRANSISTOR_PIN 10

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

#define TEMP_SENSOR_PIN_CONST 0.48828125
#define TEMP_SENSOR_PIN_CONST2 50

/*************************** UMBRALES PARTICULARES ****************************/
#define CRITIC_HIGH_TEMP 26
#define CRITIC_LOW_TEMP 20
#define CRITIC_LOW_LIGHT 25
#define CRITIC_LOW_HUMIDITY 90
#define INITIAL_TEMP -1500
#define INITIAL_HUMIDITY -1500
#define INITIAL_LIGHT -1

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

#define STATES 5
#define EVENTS 5

typedef void (*transition)();

transition transitions[STATES][EVENTS] = {
    {init_, error, error, error, error},          // state INITIAL
    {wait, monitor_plant, error, error, wait},    // state IDLE
    {none, error, monitored_plant, error, error}, // state MONITORING
    {none, error, error, adecuate, error},        // state EVALUATING
    {none, error, error, error, idle_again}       // state ADECUATING
    // CONTINUE, MONITOR_NEEDED, MONITORED, EVALUATED, ADECUATED
};

// TEMPORIZADOR POR HARDWARE
#define REGISTRO_TCCR1A 0b00000000
#define REGISTRO_TCCR1B 0b00001101
#define REGISTRO_OCR1A 3905.25 // Interrupcion cada 0.25 segundo

// <-------------------------ESTRUCTURAS------------------------->
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

// <-------------------------VARIABLES GLOBALES------------------------->

int prev_temp;
int prev_light;
int prev_humidity;

// Para maquina de estados
int state;
struct_event event;
bool timeout;

// <-------------------------FUNCIONES ARDUINO------------------------->

void setup()
{
    Serial.begin(9600);

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
    finite_state_machine();
}

ISR(TIMER1_COMPA_vect) { timeout = true; }

// <-------------------------FUNCIONES SENSORES------------------------->

float read_temperature(int sensor)
{
    // Lectura y cálculo para obtener la temperatura del ambiente
    int temp_value = analogRead(sensor);
    return TEMP_SENSOR_PIN_CONST * temp_value - TEMP_SENSOR_PIN_CONST2;
}

float read_humidity(int sensor)
{
    // Lectura de la humedad del ambiente
    return digitalRead(sensor);
}

int read_light(int sensor)
{
    // Realizamos un mapeo para tener el porcentaje de luz recibido
    int light_value = analogRead(sensor);
    return map(light_value, LIGHT_MIN_VALUE, LIGHT_MAX_VALUE, MIN_VALUE, MAX_VALUE);
}

bool check_temperature()
{
    // Retorna falso cuando la temperatura esta en la region critica
    return event.temperature > CRITIC_LOW_TEMP && event.temperature < CRITIC_HIGH_TEMP;
}

bool check_light()
{
    // Devuelve falso cuando se tiene un nivel de luz bajo
    return event.light > CRITIC_LOW_LIGHT;
}

bool check_humidity()
{
    // Devuelve falso cuando se tiene un nivel de humedad bajo
    return event.humidity;
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

    if (event.temperature > CRITIC_HIGH_TEMP)
    {
        analogWrite(COOLER_TRANSISTOR_PIN, COOLER_100);
    }
    else
    {
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

// <------------------------- MAQUINA DE ESTADOS ------------------------->

void ini()
{
    // Inicializacion de los pines
    pinMode(LIGHT_SENSOR_PIN, INPUT);
    pinMode(HUMIDITY_SENSOR_PIN, INPUT);
    pinMode(TEMPERATURE_SENSOR_PIN, INPUT);
    pinMode(LIGHTBULB_TRANSISTOR_PIN, OUTPUT);
    pinMode(COOLER_TRANSISTOR_PIN, OUTPUT); // Pin como salida digital para PWM
    pinMode(WATER_LED_PIN, OUTPUT);

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
        DebugPrintEstado(states[event.type], events[state]);
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
    event.temperature = read_temperature(TEMPERATURE_SENSOR_PIN);
    event.humidity = read_humidity(HUMIDITY_SENSOR_PIN);

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
