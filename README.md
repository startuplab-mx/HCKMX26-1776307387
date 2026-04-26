# Sistema de Monitoreo Multimodal para Deteccion de Riesgos Digitales en Menores

Este prototipo de investigacion aplicada explora el uso de monitoreo en dispositivo, OCR, modelos NLP y servicios moviles para identificar senales tempranas de riesgo digital en menores. Integra una app Android para monitoreo, una app iOS de seguimiento, modelos locales de inteligencia artificial y un backend base para almacenamiento.

El objetivo del prototipo es demostrar como una solucion multimodal puede analizar texto visible, conversaciones y eventos relevantes para apoyar la prevencion de grooming, reclutamiento, coercion o contacto sospechoso en entornos digitales.

## Problema que resuelve

Menores de edad pueden estar expuestos a situaciones de riesgo dentro de aplicaciones de mensajeria, redes sociales y plataformas digitales. Muchas senales aparecen primero como patrones de conversacion, palabras clave, insistencia de contacto, peticiones sospechosas o contenido visual antes de que exista una alerta formal.

El sistema busca resolver este problema mediante:

- Monitoreo de apps objetivo en Android usando servicios de accesibilidad.
- Extraccion y normalizacion de texto visible en pantalla.
- Clasificacion de posibles riesgos con un modelo NLP en dispositivo.
- Registro de eventos de seguridad para analisis posterior.
- Base tecnica para conectar una app de seguimiento o command center.

## Tecnologias y herramientas utilizadas

### App movil Android

- Flutter
- Dart
- Material 3
- Kotlin
- Android Accessibility Service
- Android Foreground Service
- MethodChannel para comunicacion Flutter/Kotlin
- Android SDK y Android Studio

### Inteligencia artificial y procesamiento

- TensorFlow Lite
- Google ML Kit Text Recognition
- Modelo NLP TFLite empaquetado en Android
- Pipeline OCR, Vision y NLP
- Python para entrenamiento y validacion del modelo NLP
- Dataset local para entrenamiento y pruebas

### Backend y datos

- Docker Compose
- PostgreSQL 16
- Persistencia local/mock para eventos de seguridad

### App complementaria iOS

- Swift
- SwiftUI
- Xcode

### Desarrollo y control de versiones

- Git
- GitHub
- Logcat y ADB para depuracion Android

## Estructura del repositorio

```text
.
├── backend/             # Docker Compose con PostgreSQL
├── dataset/             # Datos de entrenamiento NLP
├── minor_app_android/   # App Flutter + servicios nativos Android
├── model_nlp/           # Modelo NLP TFLite, vocabulario y labels
├── model_vision/        # Notebook de entrenamiento de vision
└── police_app_ios/      # App iOS SwiftUI para command center
```

## Instrucciones para ejecutar el prototipo

### 1. Levantar backend local

Desde la raiz del repositorio:

```bash
cd backend
docker-compose up -d
```

Validar que el contenedor este activo:

```bash
docker ps
docker-compose config
```

### 2. Ejecutar la app Android

Desde la raiz del repositorio:

```bash
cd minor_app_android
flutter pub get
flutter run
```

Para generar un APK debug:

```bash
flutter build apk --debug
```

El APK se genera en:

```text
minor_app_android/build/app/outputs/flutter-apk/app-debug.apk
```

### 3. Activar monitoreo en Android

1. Instalar y abrir la app en un emulador o dispositivo Android.
2. Ir a ajustes de accesibilidad.
3. Activar el servicio `Monitor de seguridad`.
4. Abrir una app objetivo, por ejemplo WhatsApp, Telegram, Instagram o TikTok.
5. Revisar logs con:

```bash
adb logcat -s MinorMainActivity MinorMonitor MinorAccessibility flutter
```

### 4. Ejecutar la app iOS complementaria

1. Abrir `police_app_ios/police_app_ios.xcodeproj` en Xcode.
2. Seleccionar un simulador iOS.
3. Ejecutar el proyecto desde Xcode.

## Demo del prototipo

- Repositorio publico: [https://github.com/startuplab-mx/HCKMX26-1776307387](https://github.com/startuplab-mx/HCKMX26-1776307387)
- Demo publico del prototipo: pendiente de agregar enlace de video o despliegue.

## Herramientas de IA utilizadas

Durante el desarrollo se utilizaron herramientas de IA como apoyo para ideacion, programacion, depuracion y documentacion. El equipo mantuvo la responsabilidad sobre la integracion, decisiones tecnicas, validacion y presentacion final.

### Antigravity

- Para que se uso: exploracion de ideas, organizacion del flujo del prototipo, apoyo conceptual y asistencia durante tareas de desarrollo.
- En que medida: uso frecuente como copiloto para acelerar el armado del proyecto y revisar alternativas de implementacion.
- Resultado esperado: mejor definicion del flujo general, componentes del prototipo y posibles escenarios de uso.

### Codex

- Para que se uso: analisis del codigo, ajustes de documentacion, diagnostico de errores, apoyo en integracion Flutter/Kotlin y revision de configuraciones del repositorio.
- En que medida: uso intensivo para depuracion, documentacion tecnica y cambios puntuales de codigo.
- Resultado esperado: mayor velocidad para corregir problemas, organizar README y mantener consistencia tecnica entre modulos.

### GitHub Copilot

- Para que se uso: autocompletado de codigo, generacion de fragmentos repetitivos, sugerencias dentro del editor y apoyo en estructuras basicas.
- En que medida: uso moderado durante la programacion diaria.
- Resultado esperado: reduccion de tiempo en escritura de codigo repetitivo y apoyo en implementaciones puntuales.

## Integrantes del equipo

- Diego Obed Lopez Casimiro
- Axel
- Luis Mario
- Mauricio
