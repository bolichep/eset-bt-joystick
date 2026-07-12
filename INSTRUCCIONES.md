# BT Joystick — Instrucciones para compilar el APK

## Qué es este proyecto

Una app Android nativa que envuelve la interfaz HTML/JS del joystick en un WebView,
y conecta al HC-06 por **Bluetooth Clásico SPP** — que es lo que el HC-06 realmente
usa y que los browsers no soportan directamente.

---

## Estructura de archivos

```
btjoystick/
├── app/
│   ├── build.gradle
│   ├── release/app-release.apk     ← signed release apk (cuando se genere)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   └── index.html          ← toda la UI (HTML + CSS + JS)
│       └── java/com/btjoystick/
│           └── MainActivity.java   ← bridge BT ↔ WebView
├── build.gradle
├── settings.gradle
└── INSTRUCCIONES.md
```

---
## Opción A — Android Studio (recomendado)

### Requisitos
- [Android Studio](https://developer.android.com/studio) (gratis)
- JDK 17 (viene incluido con Android Studio)

### Pasos

1. **Abrí Android Studio** → *Open* → seleccioná la carpeta `btjoystick/`

2. Esperá que **Gradle sincronice** (barra de progreso abajo). La primera vez
   descarga dependencias, puede tardar 2-5 minutos.

3. Conectá tu celular Android por USB con **Depuración USB activada**
   (Ajustes → Acerca del teléfono → toca "Número de compilación" 7 veces →
   Opciones de desarrollador → Depuración USB).

4. Seleccioná tu dispositivo en el menú desplegable de la barra de herramientas
   y presioná ▶ **Run**.

5. La app se instala y abre automáticamente.

### Para generar el APK para compartir
- *Build* → *Build Bundle(s) / APK(s)* → *Build APK(s)*
- El APK queda en `app/build/outputs/apk/debug/app-debug.apk`

---

### Generar APK release signed para publicar e instalar facilmente
- *Build* → *Generate Signed APP Bundle or APK...* → *APK* → _Fill modal_ → *Select release* → *Create*
- El release signed se encuentra en  `app/release/app-release.apk`

## Opción B — Compilar por línea de comandos

```bash
# Debug build 
# En la carpeta btjoystick/
./gradlew assembleDebug

# APK generado en:
# app/build/outputs/apk/debug/app-debug.apk
```

```bash
# Release build 
# En la carpeta btjoystick/
./gradlew assembleRelease

# APK generado en:
# /app/build/outputs/apk/release/app-release-unsigned.apk

```

---

## Uso de la app

1. **Primero pareá el HC-06** desde Ajustes → Bluetooth de Android.
   PIN por defecto: `1234` (o `0000`).

2. Abrí la app → tocá **Escanear**.

3. Aparece la lista de dispositivos BT pareados → tocá **Conectar** junto al HC-06.

4. Al conectar, el indicador se pone verde y ya podés usar el joystick.

5. Cada botón del joystick envía un byte al Arduino.
   Tocá ⚙ **Config** para cambiar qué carácter envía cada botón.

---

## Cómo funciona el bridge

```
HTML (index.html)               MainActivity.java
──────────────────              ──────────────────────────────
window.Android.getPairedDevices()  → lista dispositivos pareados
window.Android.connect(address)    → abre socket SPP al HC-06
window.Android.sendString("W")     → escribe bytes en el stream
window.Android.disconnect()        → cierra el socket

window.btCallback("connected", name)    ← notificación al JS
window.btCallback("sent", char)         ← confirmación de envío
window.btCallback("error", msg)         ← error
window.btCallback("disconnected", "")   ← desconexión
```

El HC-06 recibe los bytes exactamente como los envía el Arduino Serial.read().

---

## Ejemplo Arduino

```cpp
char cmd;

void loop() {
  if (Serial.available()) {
    cmd = Serial.read();
    switch(cmd) {
      case 'W': adelante();  break;
      case 'X': atras();     break;
      case 'A': izquierda(); break;
      case 'D': derecha();   break;
      case 'S': parar();     break;
    }
  }
}
```
