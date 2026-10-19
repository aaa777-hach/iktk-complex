# iktk-start.ps1 — запуск всей инфраструктуры + 4 сервисов
Write-Host "Starting iktk containers..." -ForegroundColor Cyan
docker start iktk-postgres iktk-rabbitmq iktk-redis iktk-minio 2>$null

Write-Host "Waiting for postgres healthy..." -ForegroundColor Cyan
for ($i = 0; $i -lt 30; $i++) {
    $h = docker inspect --format='{{.State.Health.Status}}' iktk-postgres 2>$null
    if ($h -eq 'healthy') { break }
    Start-Sleep 2
}
docker ps --filter "name=iktk-" --format "table {{.Names}}\t{{.Status}}"

$base = "C:\Users\Antipovich\Desktop\Оркестр\iktk-complex"

Write-Host "`nStarting 4 services in separate windows..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$Host.UI.RawUI.WindowTitle = 'iktk: INTEGRATION (8095)'; cd '$base\modules\integration'; java -jar target\integration.jar" -WindowStyle Minimized
Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$Host.UI.RawUI.WindowTitle = 'iktk: EQUIPMENT (8094)'; cd '$base\services\equipment-service'; java -jar target\equipment-service.jar" -WindowStyle Minimized
Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$Host.UI.RawUI.WindowTitle = 'iktk: CASTING (8090)'; cd '$base\modules\casting'; java -jar target\casting.jar" -WindowStyle Minimized
Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$Host.UI.RawUI.WindowTitle = 'iktk: ORCHESTRATOR (8080)'; cd '$base\orchestrator'; java -jar target\orchestrator.jar" -WindowStyle Minimized

Write-Host "`nWaiting 25s for services to start..." -ForegroundColor Yellow
Start-Sleep 25
Get-NetTCPConnection -LocalPort 8080,8090,8094,8095 -State Listen -ErrorAction SilentlyContinue |
    Select-Object LocalPort, OwningProcess | Sort-Object LocalPort