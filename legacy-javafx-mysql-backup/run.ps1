$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$FxLib = if ($env:FX_LIB) { $env:FX_LIB } else { "C:\javafx-sdk-21\lib" }
$Driver = "lib\mysql-connector-j.jar"

if (-not (Test-Path "$FxLib\javafx.controls.jar")) {
    throw "JavaFX SDK was not found at '$FxLib'. Set FX_LIB to its lib folder."
}
if (-not (Test-Path $Driver)) {
    throw "MySQL Connector/J was not found at '$Driver'. Place the driver there or edit run.ps1."
}
$PromptedPassword = $false
if ([string]::IsNullOrWhiteSpace($env:ENTRI_DB_PASSWORD)) {
    $SecurePassword = Read-Host "Enter the MySQL password for entri_app" -AsSecureString
    if ($SecurePassword.Length -eq 0) {
        throw "The MySQL password cannot be empty."
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
    & javac --release 21 --module-path $FxLib --add-modules javafx.controls -cp $Driver -d out $Sources
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    if (Test-Path src\main\resources) {
        Copy-Item -Path src\main\resources\* -Destination out -Recurse -Force
    }
    & java --enable-native-access=javafx.graphics --module-path $FxLib --add-modules javafx.controls -cp "out;$Driver" main.java.com.entri.MainApp
    exit $LASTEXITCODE
}
finally {
    if ($PromptedPassword) { Remove-Item Env:ENTRI_DB_PASSWORD -ErrorAction SilentlyContinue }
}
