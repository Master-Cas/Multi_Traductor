# Multi_Traductor — navegador Android con interpretación local

> **ANTES DE DESCARGAR O UTILIZAR:** licencia de uso personal y no comercial. La APK obliga a leer y aceptar los términos antes de acceder al navegador. **La distribución por GitHub Actions incluye LICENSE, TERMINOS_Y_CONDICIONES.txt y LEEME_PRIMERO.txt**. Todo uso comercial requiere convenio por escrito.
>
> **Licencia: uso personal y no comercial.** Se permite el uso privado de los componentes originales del proyecto, bajo las condiciones de [LICENSE](LICENSE). **Todo uso comercial requiere autorización previa y un convenio por escrito con Master-Cas.** Para solicitarlo, [contactar al titular por GitHub Issues](https://github.com/Master-Cas/Multi_Traductor/issues). Las dependencias de terceros mantienen sus propias licencias.


**Estado: prototipo 0.2.5, reconocimiento y traducción local comprobados en tablet; integración nativa compilada para 16 KB.** App Android en Kotlin con WebView y franja de traducción inferior. Diseñada para Android 14 / teléfonos de gama media. `minSdk 29`, `targetSdk 34`, salida nativa ARM64; un dispositivo con Android 16 también es compatible.

## Motor totalmente en el dispositivo

- `AudioPlaybackCapture`: captura el audio que Android **permite** capturar del navegador, tras permiso `RECORD_AUDIO` y consentimiento de `MediaProjection`. Limitado al UID de nuestra propia app. No captura deliberadamente audio de otras apps.
- `whisper.cpp` **v1.9.5**, fijado como submódulo Git: reconocimiento multilingüe offline sobre CPU ARM64, modelo `ggml-tiny-q5_1.bin` (~32 MB). No se aloja en el Nitro ni en nuestro VPS.
- `ML Kit Language Identification` + `ML Kit Translate`: detección de idioma y traducción local a español. Los modelos de traducción necesarios se descargan inicialmente **solo por Wi-Fi**.
- Segmentos PCM16 a 16 kHz de 5 segundos, máximo un trabajo de reconocimiento simultáneo; se descartan segmentos en vez de acumular retrasos indefinidos.
- No se graba ni almacena la voz; no hay API de transcripción externa. Navegar por internet y descargar modelos requiere conectividad.

## Instalar y probar

1. Clonar **recursivamente**: `git clone --recurse-submodules https://github.com/Master-Cas/Multi_Traductor.git` (usar rama de desarrollo del PR cuando no se haya fusionado a `main`).
2. Abrir en Android Studio con JDK 17, Android SDK 35, NDK 28.2.13676358 y CMake 3.22.1.
3. Instalar la APK debug. La app arranca en una página local de **Test** con un tono de 3 s y un archivo de voz de ejemplo `jfk.wav` de whisper.cpp.
4. Pulsar **Modelo IA** para descargar y verificar el modelo multilingüe por HTTPS (32 MB; SHA-256 fijado).
5. Pulsar **♫ Escuchar** y conceder permisos de audio y captura de pantalla de Android. Reproducir el ejemplo de voz. Esperar el reconocimiento; puede tardar más que el audio en teléfonos modestos.
6. En la primera traducción de cada idioma, conectarse a Wi-Fi para que ML Kit descargue su modelo. Después las frases de esos idiomas pueden traducirse sin internet.
7. Pulsar **■ Detener** para cortar la captura.

Modelo: https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin
SHA-256: `818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7`

## Alcance y limitaciones

- La captura de **WebAudio** ya se comprobó en una Samsung SM-P620 con Android 16. Se validó voz → texto → español en hardware con esta versión. En la Samsung SM-P620 Android 16, Whisper Tiny q5_1 compilado con `-O3`, 4 hilos y contexto 512: 3,3 s de reconocimiento para el primer fragmento de 5 s y 2,2 s para el segundo; ML Kit tradujo el primero en 0,34 s con el modelo ya instalado. Los resultados varían por dispositivo e idioma.
- Los videos protegidos, DRM, YouTube y algunos reproductores pueden impedir la captura. **No** existe una solución universal para evitar esas restricciones.
- Whisper Tiny puede producir errores o frases ficticias con música/ruido; esta versión usa solo un filtro de intensidad, aún no un VAD fiable.
- La traducción en pantalla va con retardo: Whisper necesita escuchar un fragmento completo y procesarlo antes de traducirlo. Los subtítulos todavía no están vinculados al tiempo del video.
- Para validar realmente Android 14 se necesita además un dispositivo físico Android 14; la tablet de prueba usa Android 16.

## Prueba técnica y repositorio upstream

`third_party/whisper.cpp` es submódulo de https://github.com/ggml-org/whisper.cpp fijado en v1.9.5. Respeta su licencia. El audio de prueba `jfk.wav` proviene de los ejemplos del proyecto upstream.

Compilar: `gradle :app:testDebugUnitTest :app:assembleDebug`. El workflow de GitHub Actions descarga NDK + CMake y genera una APK debug ARM64.

## Compatibilidad Android 16/16 kB

Android 16 mostró un aviso de bibliotecas nativas de ML Kit sin garantía de alineación ELF de 16 kB. La tablet probada ejecuta páginas de 4 kB (`getconf PAGE_SIZE=4096`) y pudo completar la prueba. Antes de una publicación general hay que validar bibliotecas en un dispositivo de 16 kB o actualizar las dependencias.

## Compatibilidad de 16 KB en APK 0.2.5

Whisper y GGML ahora se compilan con NDK 28 r28b y páginas flexibles de 16 KB. GitHub Actions inspecciona **todas** las bibliotecas nativas ARM64 que van empaquetadas (Whisper, ML Kit, otras), comprobando ELF `PT_LOAD p_align >= 0x4000` y `zipalign -P 16` antes de permitir publicar la APK y la licencia. La Samsung SM-P620 usa páginas de 4 KB, de modo que las comprobaciones ELF/ZIP certifican el empaquetado; para validar ejecución real con páginas de 16 KB aún hace falta ese tipo de dispositivo.
