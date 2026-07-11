---
description: Conectar el telefono para desarrollo (ADB inalambrico o USB), incl. redes con subredes distintas / aislamiento de clientes
---

Objetivo: dejar el telefono visible en `adb devices` como `device`, para instalar el APK, correr
tests instrumentados y tomar capturas. Cubre 3 escenarios: Wi-Fi misma subred, Wi-Fi con subredes
distintas/aislamiento, y USB (fallback mas robusto).

## Parametros del entorno
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { Write-Host "[X] No hay adb; instala Android SDK platform-tools" -ForegroundColor Red }
```
- Para *compilar* ademas hace falta `JAVA_HOME` (JBR) y Gradle 9.4.1 cacheado (ver `validar-niko`).
- El script `scripts/validar-niko.ps1` recibe `-Device <ip:puerto>`; el default esta hardcodeado y
  hay que sobreescribirlo cuando cambie la IP/puerto.

## Requisitos en el telefono
1. **Ajustes -> Opciones de desarrollador -> Depuracion inalambrica: ON** (o "Depuracion USB" si usas cable).
2. La pantalla de Depuracion inalambrica muestra **"Direccion IP y puerto"** (puerto de CONEXION) y la
   opcion **"Vincular dispositivo con codigo de vinculacion"** (puerto de VINCULACION, distinto).

## Escenario A - Wi-Fi, PC y telefono en la misma subred
// turbo
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb connect 192.168.x.x:PUERTO_CONNECT
& $adb devices
```
Si pide autorizacion (host nuevo), vincular primero:
```powershell
# IP:PUERTO_PAIR y codigo salen de "Vincular dispositivo con codigo de vinculacion"
& $adb pair 192.168.x.x:PUERTO_PAIR
```

## Escenario B - Subredes distintas bajo el mismo SSID (p. ej. .78.x vs .8.x)
Sintoma: PC y telefono muestran IPs de subredes diferentes aunque el SSID sea el mismo
(aislamiento de clientes / VLANs, comun en redes corporativas).

1. **Diagnostico de alcance** (ICMP):
// turbo
```powershell
ping -n 3 192.168.8.147   # IP del telefono
```
2. **Si el ping RESPONDE** (hay ruta): intenta ADB Wi-Fi directo igual que el Escenario A usando la
   IP real del telefono. Si el `connect` falla pese al ping, es que el puerto TCP de ADB esta
   bloqueado por firewall/VLAN aunque ICMP pase -> usa el Escenario C o D.
3. **Si el ping NO responde**: no hay ruta directa -> usa Escenario C (USB) o D (hotspot).

## Escenario C - USB (fallback mas simple y robusto)
Con el telefono fisicamente junto a la PC, evita toda la red:
1. Conecta el cable, acepta **"Permitir depuracion USB"** en el telefono.
// turbo
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l
```
Esperado: una linea con `device`. Ese serial se pasa a los comandos con `-s <serial>`.

## Escenario D - Hotspot para forzar la misma subred
Si no hay cable y las subredes estan aisladas:
1. Enciende el **hotspot del telefono** y conecta la **PC** a ese hotspot (o al reves: PC comparte y
   el telefono se une). Asi ambos quedan en la misma subred sin aislamiento.
2. Sigue el Escenario A con la nueva IP del telefono (mira Depuracion inalambrica).

## Verificacion final
// turbo
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices
& $adb -s <serial-o-ip:puerto> get-state   # debe imprimir: device
```

## Notas / troubleshooting
- **IP y puertos cambian** (DHCP + puertos aleatorios): toma siempre los valores actuales de la
  pantalla de Depuracion inalambrica; el `PUERTO_PAIR` != `PUERTO_CONNECT`.
- **`failed to authenticate`**: host no vinculado -> `adb pair`.
- **`connection refused` / timeout con ping OK**: puerto TCP de ADB bloqueado por VLAN/firewall -> USB o hotspot.
- **Latencia alta en ping local**: el trafico se enruta por el gateway (subredes distintas); suele
  funcionar pero mas lento para install/capturas.
- Reiniciar el daemon si algo queda raro:
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb kill-server; & $adb start-server
```
