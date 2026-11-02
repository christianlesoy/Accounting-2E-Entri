$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$FxLib = if ($env:FX_LIB) { $env:FX_LIB } else { "C:\javafx-sdk-21\lib" }
$JdkHome = $env:ENTRI_JDK_HOME
if ([string]::IsNullOrWhiteSpace($JdkHome)) {
    $JdkHome = Get-ChildItem -Path (Join-Path $env:ProgramFiles "Java") `
        -Directory -Filter "jdk-21*" -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
$Driver = "lib\postgresql-42.7.14.jar"

if ([string]::IsNullOrWhiteSpace($JdkHome) -or
        -not (Test-Path (Join-Path $JdkHome "bin\java.exe")) -or
        -not (Test-Path (Join-Path $JdkHome "bin\javac.exe"))) {
    throw "JDK 21 was not found. Install JDK 21 or set ENTRI_JDK_HOME to its installation folder."
}
if (-not (Test-Path "$FxLib\javafx.controls.jar")) {
    throw "JavaFX SDK was not found at '$FxLib'. Set FX_LIB to its lib folder."
}
if (-not (Test-Path $Driver)) {
    throw "PostgreSQL JDBC driver was not found at '$Driver'. Download it from https://jdbc.postgresql.org/download/."
}
$PromptedPassword = $false
if ([string]::IsNullOrWhiteSpace($env:ENTRI_DB_PASSWORD)) {
    $SecurePassword = Read-Host "Enter your Supabase database password" -AsSecureString
    if ($SecurePassword.Length -eq 0) {
        throw "The Supabase database password cannot be empty."
    }
    $PasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecurePassword)
    try {
        $env:ENTRI_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($PasswordPointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($PasswordPointer)
        $SecurePassword.Dispose()
    }
    $PromptedPassword = $true
}

try {
    New-Item -ItemType Directory -Force -Path out | Out-Null
    $Sources = Get-ChildItem -Recurse -Path src\main\java -Filter *.java | ForEach-Object { $_.FullName }
    & (Join-Path $JdkHome "bin\javac.exe") --release 21 --module-path $FxLib --add-modules javafx.controls -cp $Driver -d out $Sources
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    if (Test-Path src\main\resources) {
        Copy-Item -Path src\main\resources\* -Destination out -Recurse -Force
    }
    & (Join-Path $JdkHome "bin\java.exe") --enable-native-access=javafx.graphics --module-path $FxLib --add-modules javafx.controls -cp "out;$Driver" main.java.com.entri.MainApp
    exit $LASTEXITCODE
}
finally {
    if ($PromptedPassword) { Remove-Item Env:ENTRI_DB_PASSWORD -ErrorAction SilentlyContinue }
}
