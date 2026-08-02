[CmdletBinding()]
param(
    [string] $DependencyRoot = (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)),
    [string] $MavenRepository = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.m2\repository')
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$dependencyRootPath = [IO.Path]::GetFullPath($DependencyRoot)
$mavenRepositoryPath = [IO.Path]::GetFullPath($MavenRepository)
New-Item -ItemType Directory -Path $dependencyRootPath -Force | Out-Null

function Invoke-GitChecked {
    param([Parameter(Mandatory)][string[]] $Arguments)

    & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "git failed: git $($Arguments -join ' ')"
    }
}

function Get-ExactRepository {
    param(
        [Parameter(Mandatory)][string] $Name,
        [Parameter(Mandatory)][string] $Url,
        [Parameter(Mandatory)][string] $Commit
    )

    $destination = [IO.Path]::GetFullPath((Join-Path $dependencyRootPath $Name))
    if (-not $destination.StartsWith($dependencyRootPath + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Dependency path escaped the requested root: $destination"
    }

    if (-not (Test-Path -LiteralPath $destination -PathType Container)) {
        Invoke-GitChecked @('clone', '--filter=blob:none', $Url, $destination)
        Invoke-GitChecked @('-C', $destination, 'checkout', '--detach', $Commit)
    }

    $actual = (& git -C $destination rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $actual -ne $Commit) {
        throw "$Name is at '$actual'; expected '$Commit'. Refusing to rewrite an existing checkout."
    }

    return $destination
}

$patcherPath = Get-ExactRepository `
    -Name 'morphe-patcher' `
    -Url 'https://github.com/SysAdminDoc/morphe-patcher.git' `
    -Commit '464c002ad086039561766f1fee8e1cb8126cf56d'

$libraryPath = Get-ExactRepository `
    -Name 'morphe-library' `
    -Url 'https://github.com/MorpheApp/morphe-library.git' `
    -Commit 'a5b1fb512306d497cad8a13c0399a5fb28553522'

$jadbPath = Get-ExactRepository `
    -Name 'jadb' `
    -Url 'https://github.com/MorpheApp/jadb.git' `
    -Commit 'd6db20b20b754cd3ac4c22e435b9802405d40051'

# Align the composite library with the manager's AGP/Kotlin toolchain. These edits are
# intentionally idempotent and limited to the exact upstream v1.4.0 files.
$versionsFile = Join-Path $libraryPath 'gradle\libs.versions.toml'
$versionsText = [IO.File]::ReadAllText($versionsFile)
if ($versionsText -match 'android = "8\.9\.3"') {
    $versionsText = $versionsText.Replace('android = "8.9.3"', 'android = "9.3.1"')
} elseif ($versionsText -notmatch 'android = "9\.3\.1"') {
    throw "Unexpected Android Gradle Plugin version in $versionsFile"
}
if ($versionsText -match 'kotlin = "2\.2\.21"') {
    $versionsText = $versionsText.Replace('kotlin = "2.2.21"', 'kotlin = "2.4.10"')
} elseif ($versionsText -notmatch 'kotlin = "2\.4\.10"') {
    throw "Unexpected Kotlin version in $versionsFile"
}
[IO.File]::WriteAllText($versionsFile, $versionsText, [Text.UTF8Encoding]::new($false))

$libraryPropertiesFile = Join-Path $libraryPath 'gradle.properties'
$libraryProperties = [IO.File]::ReadAllText($libraryPropertiesFile)
foreach ($property in @('android.newDsl=false', 'android.builtInKotlin=false')) {
    if ($libraryProperties -notmatch "(?m)^$([Regex]::Escape($property))$") {
        $libraryProperties = $libraryProperties.TrimEnd("`r", "`n") + "`n$property`n"
    }
}
[IO.File]::WriteAllText($libraryPropertiesFile, $libraryProperties, [Text.UTF8Encoding]::new($false))

# Morphe's public jadb source is Apache-2.0 but its GitHub Maven package requires
# authentication. Build the exact tagged source locally so no token is needed.
$javac = Get-Command javac -ErrorAction Stop
$jar = Get-Command jar -ErrorAction Stop
$sourceFiles = Get-ChildItem -LiteralPath (Join-Path $jadbPath 'src') -Recurse -File -Filter '*.java' |
    Sort-Object FullName |
    Select-Object -ExpandProperty FullName
if ($sourceFiles.Count -eq 0) {
    throw "No jadb Java sources found in $jadbPath"
}

$buildDirectory = [IO.Path]::GetFullPath((Join-Path $jadbPath ('.patchdock-build-' + [Guid]::NewGuid().ToString('N'))))
if (-not $buildDirectory.StartsWith($jadbPath + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Temporary build path escaped the jadb checkout: $buildDirectory"
}

try {
    $classesDirectory = Join-Path $buildDirectory 'classes'
    New-Item -ItemType Directory -Path $classesDirectory -Force | Out-Null
    & $javac.Source --release 17 -d $classesDirectory @sourceFiles
    if ($LASTEXITCODE -ne 0) { throw 'javac failed while building jadb' }

    $builtJar = Join-Path $buildDirectory 'jadb-1.2.3.jar'
    & $jar.Source --create --file $builtJar -C $classesDirectory .
    if ($LASTEXITCODE -ne 0) { throw 'jar failed while packaging jadb' }

    $artifactDirectory = Join-Path $mavenRepositoryPath 'app\morphe\jadb\1.2.3'
    New-Item -ItemType Directory -Path $artifactDirectory -Force | Out-Null
    Copy-Item -LiteralPath $builtJar -Destination (Join-Path $artifactDirectory 'jadb-1.2.3.jar') -Force

    $pom = @'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>app.morphe</groupId>
  <artifactId>jadb</artifactId>
  <version>1.2.3</version>
</project>
'@
    [IO.File]::WriteAllText(
        (Join-Path $artifactDirectory 'jadb-1.2.3.pom'),
        $pom,
        [Text.UTF8Encoding]::new($false)
    )
} finally {
    if (Test-Path -LiteralPath $buildDirectory) {
        $buildItem = Get-Item -LiteralPath $buildDirectory -Force
        if (($buildItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Refusing to remove reparse-point build directory: $buildDirectory"
        }
        Remove-Item -LiteralPath $buildDirectory -Recurse -Force
    }
}

Write-Output "morphe-patcher: $patcherPath"
Write-Output "morphe-library: $libraryPath"
Write-Output "jadb 1.2.3 installed in: $mavenRepositoryPath"
