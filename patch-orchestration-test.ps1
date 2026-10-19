# patch-orchestration-test.ps1
$ErrorActionPreference = "Stop"

$file = "C:\Users\Antipovich\Desktop\Оркестр\iktk-complex\orchestrator\src\test\java\ru\iktk\orchestrator\service\OrchestrationServiceTest.java"

if (-not (Test-Path -LiteralPath $file)) {
    Write-Host "Файл не найден: $file" -ForegroundColor Red
    exit 1
}

# --- Бэкап ---
$backup = "$file.bak-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
Copy-Item -LiteralPath $file -Destination $backup
Write-Host "Бэкап: $backup" -ForegroundColor Green

# --- Чтение с сохранением BOM ---
$bytes = [System.IO.File]::ReadAllBytes($file)
$hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
$content = [System.IO.File]::ReadAllText($file, [System.Text.Encoding]::UTF8)

if ($content -match "PATCHED_STORE") {
    Write-Host "Файл уже пропатчен (маркер PATCHED_STORE найден). Правки не вносятся." -ForegroundColor Yellow
    exit 0
}

if ($content -notmatch "\bprojectStateRepository\b") {
    Write-Host "В тесте не найдено поле 'projectStateRepository'. Проверь имя мока вручную." -ForegroundColor Red
    exit 1
}

# --- 1. Импорты ---
$imports = @{
    "java.util.HashMap"                          = "import java.util.HashMap;"
    "java.util.Map"                              = "import java.util.Map;"
    "java.util.Optional"                         = "import java.util.Optional;"
    "ru.iktk.orchestrator.model.ProjectRuntime"  = "import ru.iktk.orchestrator.model.ProjectRuntime;"
    "static-any"        = "import static org.mockito.ArgumentMatchers.any;"
    "static-anyString"  = "import static org.mockito.ArgumentMatchers.anyString;"
    "static-when"       = "import static org.mockito.Mockito.when;"
}

function Test-ImportExists($text, $key, $line) {
    $simple = $line -replace '^import\s+','' -replace ';$',''
    # прямой импорт
    if ($text -match ("(?m)^" + [regex]::Escape($line) + "\s*$")) { return $true }
    # wildcard для статических
    if ($line -like 'import static *') {
        $parts = $simple -split '\.'
        $cls = $parts[-2]
        if ($text -match ("(?m)^import\s+static\s+" + [regex]::Escape(($parts[0..($parts.Length-2)] -join '.')) + "\.\*\s*;")) { return $true }
    }
    return $false
}

$newImports = @()
foreach ($k in $imports.Keys) {
    if (-not (Test-ImportExists $content $k $imports[$k])) {
        $newImports += $imports[$k]
    }
}

if ($newImports.Count -gt 0) {
    $importMatches = [regex]::Matches($content, "(?m)^import\s+[^\r\n]+;")
    if ($importMatches.Count -eq 0) {
        Write-Host "Не найдена секция import. Правка невозможна автоматически." -ForegroundColor Red
        exit 1
    }
    $lastImport = $importMatches[$importMatches.Count - 1]
    $insertPos = $lastImport.Index + $lastImport.Length
    $insertText = "`r`n" + ($newImports -join "`r`n")
    $content = $content.Substring(0, $insertPos) + $insertText + $content.Substring($insertPos)
    Write-Host "Добавлены импорты:" -ForegroundColor Green
    $newImports | ForEach-Object { Write-Host "  $_" -ForegroundColor Green }
}

# --- 2. Поле store в классе ---
$classMatch = [regex]::Match($content, "class\s+OrchestrationServiceTest\b")
if (-not $classMatch.Success) {
    Write-Host "Не найдено объявление 'class OrchestrationServiceTest'. Правка невозможна." -ForegroundColor Red
    exit 1
}
$classBrace = $content.IndexOf("{", $classMatch.Index)
if ($classBrace -lt 0) {
    Write-Host "Не найдена '{' после объявления класса." -ForegroundColor Red
    exit 1
}

$fieldCode = "`r`n`r`n    // === PATCHED_STORE FIELD ===`r`n    private final Map<String, ProjectRuntime> store = new HashMap<>();`r`n    // === PATCHED_STORE FIELD END ===`r`n"
$content = $content.Substring(0, $classBrace + 1) + $fieldCode + $content.Substring($classBrace + 1)
Write-Host "Поле 'store' добавлено в класс." -ForegroundColor Green

# --- 3. Заглушки в @BeforeEach ---
$stubCode = @'

        // === PATCHED_STORE START ===
        when(projectStateRepository.save(any(ProjectRuntime.class)))
            .thenAnswer(inv -> {
                ProjectRuntime r = inv.getArgument(0);
                store.put(r.getProjectId(), r);
                return r;
            });
        when(projectStateRepository.findByProjectId(anyString()))
            .thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0))));
        when(projectStateRepository.findByProjectIdAndVersion(anyString(), anyString()))
            .thenAnswer(inv -> {
                String pid = inv.getArgument(0);
                ProjectRuntime r = store.get(pid);
                if (r == null) return Optional.empty();
                return Optional.of(r);
            });
        // === PATCHED_STORE END ===
'@

$beforeEach = [regex]::Match($content, "@BeforeEach\b")
if (-not $beforeEach.Success) {
    Write-Host "Не найден @BeforeEach. Поле 'store' добавлено, но заглушки нужно вставить вручную:" -ForegroundColor Yellow
    Write-Host $stubCode -ForegroundColor Yellow
    $enc = [System.Text.UTF8Encoding]::new($hasBom)
    [System.IO.File]::WriteAllText($file, $content, $enc)
    Write-Host "Файл сохранён с полем 'store'. Добавь заглушки вручную." -ForegroundColor Yellow
    exit 1
}

$openBrace = $content.IndexOf("{", $beforeEach.Index)
if ($openBrace -lt 0) {
    Write-Host "Не найдена '{' после @BeforeEach." -ForegroundColor Red
    exit 1
}

$depth = 1
$i = $openBrace + 1
while ($i -lt $content.Length -and $depth -gt 0) {
    $ch = [string]$content[$i]
    if ($ch -eq '{') { $depth++ }
    elseif ($ch -eq '}') { $depth-- }
    $i++
}
if ($depth -ne 0) {
    Write-Host "Не удалось найти конец @BeforeEach." -ForegroundColor Red
    exit 1
}
$closeBrace = $i - 1

$content = $content.Substring(0, $closeBrace) + $stubCode + $content.Substring($closeBrace)
Write-Host "Заглушки добавлены в @BeforeEach." -ForegroundColor Green

# --- Запись ---
$enc = [System.Text.UTF8Encoding]::new($hasBom)
[System.IO.File]::WriteAllText($file, $content, $enc)

Write-Host ""
Write-Host "Патч применён: $file" -ForegroundColor Green
Write-Host "Бэкап: $backup" -ForegroundColor Green
Write-Host ""
Write-Host "Запусти:" -ForegroundColor Yellow
Write-Host "mvn -pl orchestrator -am test '-Dtest=OrchestrationServiceTest`$StartProjectTests#startProject_success' '-Dsurefire.failIfNoSpecifiedTests=false'" -ForegroundColor Cyan