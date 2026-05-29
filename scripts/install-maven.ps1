Param(
    [switch]$Force
)

Write-Host "Checking for existing 'mvn'..."
$mvn = Get-Command mvn -ErrorAction SilentlyContinue
if ($mvn -and -not $Force) {
    Write-Host "Maven already available at: $($mvn.Source)"
    & mvn -v
    exit 0
}

function Try-Choco {
    if (Get-Command choco -ErrorAction SilentlyContinue) {
        Write-Host "Installing Maven via Chocolatey..."
        choco install maven -y
        if (Get-Command refreshenv -ErrorAction SilentlyContinue) { refreshenv }
        return $true
    }
    return $false
}

function Try-Scoop {
    if (Get-Command scoop -ErrorAction SilentlyContinue) {
        Write-Host "Installing Maven via Scoop..."
        scoop install openjdk maven
        return $true
    }
    return $false
}

function Download-And-Extract {
    param($version = '3.9.4')
    $fileName = "apache-maven-$version-bin.zip"
    $urls = @("https://dlcdn.apache.org/maven/maven-3/$version/binaries/$fileName",
              "https://archive.apache.org/dist/maven/maven-3/$version/binaries/$fileName")

    $tmp = [System.IO.Path]::GetTempPath()
    $zipPath = Join-Path $tmp $fileName
    $installedDir = Join-Path $env:USERPROFILE "apache-maven-$version"

    foreach ($u in $urls) {
        try {
            Write-Host "Downloading $u ..."
            Invoke-WebRequest -Uri $u -OutFile $zipPath -UseBasicParsing -ErrorAction Stop
            break
        } catch {
            Write-Host "Download failed: $u"
        }
    }

    if (-not (Test-Path $zipPath)) { Write-Error "Failed to download Maven binary."; return $false }

    Write-Host "Extracting to $installedDir ..."
    if (Test-Path $installedDir) { Remove-Item -Recurse -Force $installedDir }
    Expand-Archive -LiteralPath $zipPath -DestinationPath $env:USERPROFILE -Force

    # The zip extracts to apache-maven-<version>
    $bin = Join-Path $installedDir 'bin'
    if (-not (Test-Path $bin)) {
        Write-Error "Extraction did not produce expected folder: $bin"
        return $false
    }

    Write-Host "Adding $bin to user PATH (setx)..."
    $current = [Environment]::GetEnvironmentVariable('Path', 'User')
    if ($current -notlike "*$bin*") {
        $new = "$current;$bin"
        setx PATH $new | Out-Null
        Write-Host "User PATH updated. You may need to restart your shell to pick up changes."
    } else {
        Write-Host "PATH already contains Maven bin.";
    }

    return $true
}

if (Try-Choco) {
    Write-Host "Maven install attempted via choco. Verify with 'mvn -v'."
    exit 0
}

if (Try-Scoop) {
    Write-Host "Maven install attempted via scoop. Verify with 'mvn -v'."
    exit 0
}

Write-Host "Falling back to direct download and user-path install..."
if (Download-And-Extract) {
    Write-Host "Maven installed to user profile. Start a new shell and run 'mvn -v' to verify."
    exit 0
} else {
    Write-Error "Automatic installation failed. Please install Maven manually (choco, scoop, or from apache.org)."
    exit 2
}
