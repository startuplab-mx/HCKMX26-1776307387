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

- **Flutter / Dart** — App movil Android con servicios nativos de accesibilidad y monitoreo en primer plano.
- **Node.js** — Backend y servidor de la API.
- **Prisma** — ORM para manejo del esquema y consultas a la base de datos.
- **PostgreSQL** — Base de datos relacional para persistencia de eventos de seguridad.
- **TensorFlow / TensorFlow Lite** — Entrenamiento y ejecucion en dispositivo del modelo NLP de clasificacion de texto.
- **Python** — Entrenamiento, validacion y exportacion de los modelos de inteligencia artificial.
- **YOLO (YOLOv8)** — Deteccion de objetos para el modelo de vision, entrenado en Google Colab y exportado a TFLite.
- **Xcode / Swift / SwiftUI** — App iOS complementaria para el command center.

## Estructura del repositorio

```text
.
├── backend/             # Node.js con Prisma y PostgreSQL
├── dataset/             # Datos de entrenamiento NLP
├── minor_app_android/   # App Flutter + servicios nativos Android
├── model_nlp/           # Modelo NLP TFLite, vocabulario y labels
├── model_vision/        # Notebook de entrenamiento de vision con YOLOv8
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

### 5. Entrenar el modelo NLP

Desde la raiz del repositorio:

```bash
cd model_nlp
python trainer.py
```

El script toma el dataset en `dataset/`, entrena el modelo de clasificacion de texto y exporta los archivos resultantes (`recruitment_detector.tflite`, `vocab.txt`, `labels.txt`) dentro de `model_nlp/`.

### 6. Entrenar el modelo de vision

El entrenamiento del modelo de vision se realiza en Google Colab usando el notebook incluido en `model_vision/`.

1. Abrir el notebook en Google Colab.
2. Subir el dataset de imagenes a `/content/vision_dataset/` con la estructura de carpetas esperada.
3. Ejecutar las celdas en orden para preparar el dataset, entrenar con YOLOv8 y exportar el modelo a formato TFLite.
4. Descargar el archivo `modelo_tactical_v1.zip` generado al final del notebook, que contiene los pesos `.pt` y el modelo `.tflite` listo para integrarse en la app Android.

---

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

### ChatGPT

- Para que se uso: consultas tecnicas, resolucion de dudas de implementacion, generacion de fragmentos de codigo y apoyo en documentacion.
- En que medida: uso moderado como referencia tecnica y apoyo durante el desarrollo.
- Resultado esperado: respuestas rapidas a preguntas puntuales y alternativas de implementacion para distintos modulos del prototipo.

### Claude

- Para que se uso: redaccion y mejora de documentacion, revision de logica de codigo, apoyo en integracion de componentes y consultas sobre arquitectura del sistema.
- En que medida: uso moderado como asistente de documentacion y revision tecnica.
- Resultado esperado: documentacion mas clara y consistente, y apoyo en decisiones de diseno tecnico del prototipo.

## Integrantes del equipo

- Diego Obed Lopez Casimiro
- Axel Eduardo Urbina Secundino
- Luis Mario Albino Merino
- Mauricio Carreola Cuevas
- Ali Gael Lopez Casimiro

# Link de video de presentación 
https://youtu.be/q_8tgb6BPv4
