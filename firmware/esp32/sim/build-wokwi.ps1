param([string]$ArduinoCli = 'arduino-cli')

$ErrorActionPreference = 'Stop'
$firmware = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).ProviderPath
$build = Join-Path $firmware 'build'
$output = Join-Path $build 'wokwi'
$sketch = Join-Path $build ('wokwi-source-' + [Guid]::NewGuid().ToString('N') + '/esp32')
$sourceDestination = Join-Path $sketch 'src'
[IO.Directory]::CreateDirectory($sourceDestination) | Out-Null
[IO.Directory]::CreateDirectory($output) | Out-Null

Get-ChildItem -LiteralPath (Join-Path $firmware 'src') -File | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination $sourceDestination
}

# Compila una copia descartable. La imagen virtual nunca usa la clave física.
$encoding = [Text.UTF8Encoding]::new($false)
$entry = Get-Content -LiteralPath (Join-Path $firmware 'esp32.ino') -Raw
$entry = [regex]::Replace($entry, '(?m)^([ \t]*#\s*define\s+AUTO_IOT_WIFI_PASSWORD)[^\r\n]*', '$1 ""')
[IO.File]::WriteAllText((Join-Path $sketch 'esp32.ino'), $entry, $encoding)
[IO.File]::WriteAllText((Join-Path $sketch 'wifi_secrets.h'), '#define AUTO_IOT_WIFI_PASSWORD ""', $encoding)
$options = Get-Content -LiteralPath (Join-Path $firmware 'opciones.h') -Raw
$options = [regex]::Replace($options, '(?m)^([ \t]*#\s*define\s+AUTO_IOT_USE_(?:SERVOS|OLED))[ \t]+[01]\b', '$1 0')
[IO.File]::WriteAllText((Join-Path $sketch 'opciones.h'), $options, $encoding)

& $ArduinoCli compile --fqbn esp32:esp32:esp32 --output-dir $output $sketch
if ($LASTEXITCODE -ne 0) {
    throw "Arduino CLI no pudo compilar el firmware virtual (exit $LASTEXITCODE)."
}
foreach ($artifact in @('esp32.ino.merged.bin', 'esp32.ino.elf')) {
    if (-not (Test-Path -LiteralPath (Join-Path $output $artifact) -PathType Leaf)) {
        throw "No se generó $artifact en $output."
    }
}
Write-Host "Firmware virtual generado en $output"
