$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$OutputDir = Join-Path $env:TEMP ("entri-test-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $OutputDir | Out-Null
try {
    $Sources = Get-ChildItem -Path src\main\java\main\java\com\entri -Filter *.java |
        Where-Object { $_.Name -notin @("MainApp.java", "AccountingSection.java", "EntriTheme.java",
            "OverviewSection.java", "RecentViewsSection.java",
            "JournalEntrySection.java", "LedgerSection.java", "WorksheetSection.java") } |
        ForEach-Object { $_.FullName }
    $TestSources = Get-ChildItem -Path src\test\java\main\java\com\entri -Filter *.java |
        ForEach-Object { $_.FullName }
    & javac --release 21 -d $OutputDir ($Sources + $TestSources)
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & java -cp $OutputDir main.java.com.entri.PasswordHasherTest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & java -cp $OutputDir main.java.com.entri.AccountingServiceTest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & java -cp $OutputDir main.java.com.entri.CurrencyFormatterTest
    exit $LASTEXITCODE
}
finally {
    Remove-Item -LiteralPath $OutputDir -Recurse -Force
}
