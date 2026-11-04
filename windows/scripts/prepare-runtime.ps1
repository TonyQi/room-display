$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$resources=Join-Path $projectRoot 'runtime-resources'
if (-not $env:JAVA_HOME) { throw '请设置 JAVA_HOME 为 JDK17 路径，仅用于构建。运行成品无需安装Java。' }
$jlink=Join-Path $env:JAVA_HOME 'bin/jlink.exe'
$jar=Join-Path $projectRoot '../server/target/room-server-1.0.0.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw '请先构建Java服务和Vue管理界面。' }
New-Item -ItemType Directory -Force $resources | Out-Null
$runtime=Join-Path $resources 'jre'
if (-not (Test-Path -LiteralPath (Join-Path $runtime 'bin/java.exe'))) {
    & $jlink --add-modules java.se,jdk.unsupported,jdk.crypto.ec,jdk.charsets,jdk.zipfs --strip-debug --no-header-files --no-man-pages --compress=2 --output $runtime
    if ($LASTEXITCODE -ne 0) { throw '运行环境生成失败。' }
}
Copy-Item -LiteralPath $jar -Destination (Join-Path $resources 'room-server.jar') -Force
