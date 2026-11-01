# Launcher estilo Apple TV para Mi TV MSSP3

Launcher nativo (vistas Android, sin dependencias), pensado para Android 9 y poca RAM.

## Conseguir el APK (sin instalar nada en el PC)
1. Crea un repositorio vacio en github.com (privado vale).
2. Sube TODO el contenido de esta carpeta, incluida la carpeta oculta `.github`.
   Comprueba que en GitHub aparece `.github/workflows/build.yml`.
3. Pestana **Actions** > "Build APK" > espera a que termine (unos 3-5 min).
   Si no arranca solo: "Run workflow".
4. Al final de la ejecucion, descarga el artefacto `tv-launcher-apk` (un zip con `app-debug.apk`).

## Instalar
Copia `app-debug.apk` e `instalar.bat` a la carpeta `platform-tools` y ejecuta `instalar.bat`.

## Cambiar el dock, las columnas o el fondo
Edita `app/src/main/res/values/config.xml`:
- `columns`: huecos del dock y columnas de la cuadricula (4 a 7).
- `dock_apps`: paquetes del dock, en orden.
- `hidden_apps`: paquetes que no quieres en la cuadricula.
El fondo es `app/src/main/res/values/colors.xml` (`bg`, negro por defecto).
Tras editar, sube el cambio a GitHub y descarga el nuevo APK.

## Deshacer
`restaurar.bat` reactiva los paquetes desactivados. Para quitar el launcher:
`adb shell pm uninstall com.rafa.tvlauncher`
