# =====================================================================
#  verify-all.ps1 - Verification complete du projet (Windows PowerShell)
#
#  Lancement (depuis la racine du projet) :
#    powershell -ExecutionPolicy Bypass -File .\scripts\verify-all.ps1
#
#  Ce script :
#   1. verifie les outils et le fichier .env (sans afficher aucune valeur)
#   2. lance un MySQL de TEST jetable (port 3307, conteneur devops-verify-mysql)
#   3. compile et teste le backend et users-microservice (mvnw verify)
#   4. installe, compile et teste le frontend (npm ci, build, tests Chrome Headless)
#   5. construit et demarre la pile Docker Compose (projet "devops-projet")
#   6. execute des tests de bout en bout via http://localhost:8088
#   7. verifie la persistance MySQL (down SANS -v puis up)
#
#  Securite :
#   - ne touche ni a MySQL XAMPP (port 3306) ni a aucun conteneur exterieur au projet
#   - n'inspecte que les ressources du projet (devops-projet, devops-verify-mysql)
#   - n'utilise jamais le port 8080 (frontend sur 8088)
#   - ne supprime aucun volume (jamais "down -v")
#   - n'affiche aucun secret ; journal : verification-logs\derniere-verification.log
# =====================================================================

$ErrorActionPreference = 'Continue'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
$LogDir = Join-Path $Root 'verification-logs'
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$Log = Join-Path $LogDir 'derniere-verification.log'
Set-Content -Path $Log -Value "Verification du $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -Encoding UTF8
$Results = New-Object System.Collections.ArrayList

function Write-Log([string]$Text) {
    Write-Host $Text
    Add-Content -Path $Log -Value $Text -Encoding UTF8
}

function Add-Result([string]$Name, [bool]$Ok, [string]$Detail = '') {
    $status = 'ECHEC'
    if ($Ok) { $status = 'OK' }
    [void]$Results.Add([pscustomobject]@{ Etape = $Name; Statut = $status; Detail = $Detail })
    Write-Log ">>> $status : $Name $Detail"
}

# Execute une commande, journalise sa sortie et retourne $true si le code de sortie vaut 0
function Invoke-Step([string]$Name, [scriptblock]$Block) {
    Write-Log ''
    Write-Log "==================== $Name ===================="
    $global:LASTEXITCODE = 0
    & $Block 2>&1 | ForEach-Object { Write-Log ("$_") }
    $ok = ($LASTEXITCODE -eq 0)
    Add-Result $Name $ok "(code $LASTEXITCODE)"
    return $ok
}

function Get-HttpStatus([string]$Url, [hashtable]$Headers = @{}) {
    try {
        $r = Invoke-WebRequest -Uri $Url -Headers $Headers -UseBasicParsing -TimeoutSec 30
        return [int]$r.StatusCode
    } catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        return 0
    }
}

function Invoke-SmokeTests([string]$Label, [hashtable]$EnvValues) {
    $base = 'http://localhost:' + $FrontendPort
    Write-Log ''
    Write-Log "==================== Tests de bout en bout ($Label) ===================="
    Add-Result "[$Label] Frontend Angular /" ((Get-HttpStatus "$base/") -eq 200)
    Add-Result "[$Label] Routage Angular /employees" ((Get-HttpStatus "$base/employees") -eq 200)
    $s = Get-HttpStatus "$base/departement_employee/api"
    Add-Result "[$Label] API backend sans jeton refusee" ($s -eq 403) "(HTTP $s)"

    $token = $null
    try {
        $body = @{ username = $EnvValues['ADMIN_USERNAME']; password = $EnvValues['ADMIN_PASSWORD'] } | ConvertTo-Json
        $resp = Invoke-WebRequest -Uri "$base/users/login" -Method Post -Body $body -ContentType 'application/json' -UseBasicParsing -TimeoutSec 30
        $token = $resp.Headers['Authorization']
    } catch { $token = $null }
    Add-Result "[$Label] Login admin (JWT recu)" (-not [string]::IsNullOrEmpty($token))
    if ($token) {
        $h = @{ Authorization = $token }
        $s = Get-HttpStatus "$base/departement_employee/api" $h
        Add-Result "[$Label] Liste employes avec jeton" ($s -eq 200) "(HTTP $s)"
        $s = Get-HttpStatus "$base/departement_employee/dep" $h
        Add-Result "[$Label] Liste departements avec jeton" ($s -eq 200) "(HTTP $s)"
        $s = Get-HttpStatus "$base/users/all" $h
        Add-Result "[$Label] Liste utilisateurs (ADMIN)" ($s -eq 200) "(HTTP $s)"
    }
}

function Get-ContainerState([string]$Name) {
    $state = docker ps -a --filter "name=^/$Name$" --format '{{.Status}}' 2>$null
    if ([string]::IsNullOrEmpty($state)) { return 'absent' }
    return $state
}

# ---------------------------------------------------------------- 1. Pre-requis
Write-Log '==================== 1. Pre-requis ===================='
docker version --format 'Docker client {{.Client.Version}} / serveur {{.Server.Version}}' 2>&1 | ForEach-Object { Write-Log "$_" }
docker compose version 2>&1 | ForEach-Object { Write-Log "$_" }
node --version 2>&1 | ForEach-Object { Write-Log "Node $_" }
java -version 2>&1 | Select-Object -First 1 | ForEach-Object { Write-Log "$_" }
Write-Log 'Conteneurs du projet avant la verification :'
docker ps -a --filter 'label=com.docker.compose.project=devops-projet' --format '  {{.Names}} | {{.Status}} | {{.Ports}}' 2>&1 | ForEach-Object { Write-Log "$_" }

if (-not (Test-Path (Join-Path $Root '.env'))) {
    Add-Result 'Fichier .env present' $false '(copier .env.example en .env)'
    $Results | Format-Table -AutoSize | Out-String | ForEach-Object { Write-Log $_ }
    exit 1
}
$EnvValues = @{}
Get-Content (Join-Path $Root '.env') | Where-Object { $_ -match '^\s*[A-Z_]+=' } | ForEach-Object {
    $k, $v = $_ -split '=', 2
    $EnvValues[$k.Trim()] = $v.Trim()
}
$required = 'MYSQL_ROOT_PASSWORD', 'MYSQL_APP_USER', 'MYSQL_APP_PASSWORD', 'JWT_SECRET', 'ADMIN_USERNAME', 'ADMIN_PASSWORD'
$missing = @($required | Where-Object { [string]::IsNullOrEmpty($EnvValues[$_]) })
Add-Result 'Variables obligatoires du .env renseignees' ($missing.Count -eq 0) ("manquantes: " + ($missing -join ', '))
$optional = @('MAIL_USERNAME', 'MAIL_PASSWORD', 'ADMIN_EMAIL' | Where-Object { [string]::IsNullOrEmpty($EnvValues[$_]) })
if ($optional.Count -gt 0) { Write-Log ("Info : variables optionnelles vides (inscription par email inactive) : " + ($optional -join ', ')) }
$FrontendPort = '8088'
if ($EnvValues['FRONTEND_PORT']) { $FrontendPort = $EnvValues['FRONTEND_PORT'] }

# ---------------------------------------------------------------- 2. MySQL de test
Write-Log ''
Write-Log '==================== 2. MySQL de TEST jetable (port 3307) ===================='
# Securite : on n'arrete jamais un conteneur existant et on ne reutilise pas un port occupe
$ready = $false
$canStart = $true
if ((Get-ContainerState 'devops-verify-mysql') -ne 'absent') {
    Write-Log 'Un conteneur devops-verify-mysql existe deja : il n est pas modifie. Tests Java ignores.'
    $canStart = $false
}
if (Get-NetTCPConnection -LocalPort 3307 -State Listen -ErrorAction SilentlyContinue) {
    Write-Log 'Le port 3307 est deja utilise : MySQL de test non demarre. Tests Java ignores.'
    $canStart = $false
}
if ($canStart) {
    docker run -d --rm --name devops-verify-mysql -p 127.0.0.1:3307:3306 -e MYSQL_ROOT_PASSWORD=testroot mysql:8.4 2>&1 | ForEach-Object { Write-Log "$_" }
}
for ($i = 0; $i -lt 60 -and $canStart -and -not $ready; $i++) {
    Start-Sleep 3
    if (docker logs devops-verify-mysql 2>&1 | Select-String 'port: 3306') { $ready = $true }
}
Add-Result 'MySQL de test pret' $ready

# ---------------------------------------------------------------- 3. Maven
if ($ready) {
    Push-Location (Join-Path $Root 'backend')
    Invoke-Step '3a. Backend : mvnw clean verify (tests inclus)' { .\mvnw.cmd -B '-Dstyle.color=never' clean verify } | Out-Null
    Pop-Location
    Push-Location (Join-Path $Root 'users-microservice')
    Invoke-Step '3b. users-microservice : mvnw clean verify (tests inclus)' { .\mvnw.cmd -B '-Dstyle.color=never' clean verify } | Out-Null
    Pop-Location
    # Arret du seul conteneur cree par ce script (--rm : supprime automatiquement)
    docker stop devops-verify-mysql 2>&1 | Out-Null
    Write-Log 'MySQL de test arrete.'
} else {
    Add-Result 'Tests Java (MySQL de test indisponible)' $false
}

# ---------------------------------------------------------------- 4. Frontend
if (-not $env:CHROME_BIN) {
    $candidates = @(
        "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
        "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
        "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe",
        "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
        "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe")
    $found = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if ($found) { $env:CHROME_BIN = $found; Write-Log "Navigateur pour les tests Angular : $found" }
}
Push-Location (Join-Path $Root 'frontend')
Invoke-Step '4a. Frontend : npm ci' { npm ci --no-audit --no-fund } | Out-Null
Invoke-Step '4b. Frontend : npm run build' { npm run build } | Out-Null
Invoke-Step '4c. Frontend : tests (Chrome Headless)' { npm run test:ci } | Out-Null
Pop-Location

# ---------------------------------------------------------------- 5. Docker Compose
$busy = Get-NetTCPConnection -LocalPort ([int]$FrontendPort) -State Listen -ErrorAction SilentlyContinue
$ourFrontend = docker compose ps -q frontend 2>$null
if ($busy -and -not $ourFrontend) {
    Add-Result "Port $FrontendPort libre" $false "(port occupe : choisir un port libre superieur, ex. FRONTEND_PORT=8089 dans .env)"
} else {
    Invoke-Step '5a. docker compose config' { docker compose config --quiet } | Out-Null
    $built = Invoke-Step '5b. docker compose build' { docker compose build }
    if (-not $built) {
        Write-Log 'Construction des images en echec : demarrage et tests de bout en bout non lances (voir la premiere erreur ci-dessus).'
    } else {
        $up = Invoke-Step '5c. docker compose up -d --wait' { docker compose up -d --wait --wait-timeout 300 }
        Write-Log ''
        docker compose ps 2>&1 | ForEach-Object { Write-Log "$_" }
        if (-not $up) { docker compose logs --no-color --tail 80 2>&1 | ForEach-Object { Write-Log "$_" } }

        Invoke-SmokeTests 'premier demarrage' $EnvValues

        # ------------------------------------------------------------ 6. Persistance
        Invoke-Step '6a. Persistance : docker compose down (volume conserve)' { docker compose down } | Out-Null
        Invoke-Step '6b. Persistance : docker compose up -d --wait' { docker compose up -d --wait --wait-timeout 300 } | Out-Null
        Invoke-SmokeTests 'apres redemarrage' $EnvValues
        $kept = docker compose logs users-microservice 2>&1 | Select-String 'existe deja'
        Add-Result 'Compte admin conserve dans le volume MySQL' ([bool]$kept)
        Write-Log ''
        docker compose ps 2>&1 | ForEach-Object { Write-Log "$_" }
        docker volume ls --filter 'name=devops-projet' 2>&1 | ForEach-Object { Write-Log "$_" }
        $ports = (docker compose ps --format '{{.Ports}}' 2>$null) -join ' '
        Add-Result 'Aucun port 8080 publie par le projet' (-not ($ports -match ':8080->'))
        Add-Result "Frontend publie sur le port $FrontendPort" ($ports -match (':' + $FrontendPort + '->'))
    }
}

# ---------------------------------------------------------------- Bilan
Write-Log ''
Write-Log '==================== BILAN ===================='
$Results | Format-Table -AutoSize | Out-String -Width 200 | ForEach-Object { Write-Log $_ }
$failed = @($Results | Where-Object { $_.Statut -ne 'OK' }).Count
if ($failed -eq 0) { Write-Log 'RESULTAT GLOBAL : TOUT EST OK' } else { Write-Log "RESULTAT GLOBAL : $failed etape(s) en echec" }
Write-Log "Application : http://localhost:$FrontendPort   |   Journal : $Log"
