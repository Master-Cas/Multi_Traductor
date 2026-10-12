# Multi_Traductor

Navegador Android con futura traducción local de voz, diseñado para **Android 14 y teléfonos de gama media**.

## Fase 1 — validación de captura de audio (NO es todavía un traductor)

- Kotlin + WebView: navegación y página de prueba local.
- Franja inferior reservada para traducción en vertical y horizontal.
- Permiso explícito de Android para audio interno: RECORD_AUDIO + MediaProjection + foreground service mediaProjection.
- Medición real de PCM capturable con dBFS (sin guardar audio ni transmitirlo).
- Filtro por UID propio para no recopilar audio de otras aplicaciones.
- Compilación y test automatizados en GitHub Actions.

**Pendiente:** validar en un teléfono Android 14 que el audio de WebView sea capturable (su reproductor puede usar otro UID o una política restrictiva). **NO hay Whisper, reconocimiento de voz, traducción ni subtítulos verdaderos** todavía. La barra inferior distingue estas limitaciones.

### Prueba en un teléfono
1. Abrir con Android Studio (JDK 17, Gradle 8.9, SDK 35), o descargar la APK debug desde Actions una vez que CI pase.
2. En «Test», pulsar «♫ Escuchar», autorizar grabación de audio y captura Android.
3. Pulsar «Emitir tono de prueba» y mirar el medidor dBFS. Si muestra −90 dBFS, investigar compatibilidad de WebView/UID; no afirmar que funciona.
4. Pulsar «■ Detener» para finalizar el servicio y su notificación.

### Próximos pasos
1. Captura de voz real y paquetes PCM de 3–5 s, con VAD.
2. Whisper.cpp Tiny cuantizado, ejecutándose localmente en el dispositivo.
3. Traducción de texto offline al español mediante modelos locales.
4. Sincronización progresiva y optimización de RAM/batería.
5. Compatibilidad sitio por sitio (no se evitan DRM ni restricciones de YouTube).

El navegador usa internet al visitar páginas, pero **no requiere Nitro ni VPS para su motor**. Esta fase no envía audio a servidores. AudioPlaybackCapture solo recibe audio permitido por el emisor y por Android.

Documentación: https://developer.android.com/media/platform/av-capture

Compilar con Gradle 8.9: `gradle :app:testDebugUnitTest :app:assembleDebug`. El proyecto no incluye aún Gradle Wrapper; GitHub Actions instala Gradle durante CI.