# Nécessite Java installé (pour jar tf) ou utilise .NET pour lire les ZIP

Add-Type -AssemblyName System.IO.Compression.FileSystem

$jarFiles = Get-ChildItem -Path . -Filter "*.jar"

foreach ($jar in $jarFiles) {
    try {
        $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -match "pom\.xml$") {
                $stream = $entry.Open()
                $reader = New-Object System.IO.StreamReader($stream)
                $content = $reader.ReadToEnd()
                $reader.Close()
                $stream.Close()
                
                if ($content -match "httpclient5|httpcore5") {
                    Write-Host "==> $($jar.Name) dépend de httpclient5/httpcore5" -ForegroundColor Yellow
                }
            }
        }
        $zip.Dispose()
    }
    catch {
        # Ignorer les erreurs de lecture
    }
}