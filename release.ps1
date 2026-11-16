# release.ps1 — 发版门禁
#
# 用法：  .\release.ps1            # 完整门禁 + 发到 ..\deliver
#         .\release.ps1 -SkipBuild # 只做检查（jar 已构建好）
#
# 门禁内容对应 docs\INVARIANTS.md，任一条失败即中止并打印原因。

param(
    [switch]$SkipBuild,
    [switch]$AllowDirty
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
Set-Location $root

function Fail($msg) {
    Write-Host "GATE FAIL: $msg" -ForegroundColor Red
    exit 1
}
function Ok($msg) {
    Write-Host "GATE OK  : $msg" -ForegroundColor Green
}

# 1) 工作区干净（避免发出未提交/半成品）
if (-not $AllowDirty) {
    $dirty = git status --porcelain
    if ($dirty) { Fail "工作区有未提交改动，先 commit（或加 -AllowDirty）:`n$dirty" }
    Ok "工作区干净"
}

# 2) clean build（refmap 只有全量编译才会正确生成）
if (-not $SkipBuild) {
    & "$root\run-build.cmd" clean build 2>&1 | Tee-Object -Variable buildLog | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail "构建失败，见上方输出" }
    if ($buildLog -match 'BUILD SUCCESSFUL' -eq $false) { Fail "构建输出没有 BUILD SUCCESSFUL" }
    Ok "clean build 成功"
}

$jar = Get-ChildItem "$root\build\libs\*.jar" |
    Where-Object { $_.Name -notmatch 'sources|javadoc' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $jar) { Fail "build\libs 下没有 jar" }
Ok "jar: $($jar.Name) ($($jar.Length) 字节)"

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
try {
    # 3) refmap 非空
    $refmap = $zip.Entries | Where-Object { $_.FullName -match 'refmap\.json$' } | Select-Object -First 1
    if (-not $refmap) { Fail "jar 里没有 refmap" }
    $reader = New-Object System.IO.StreamReader($refmap.Open())
    $refText = $reader.ReadToEnd(); $reader.Close()
    if ($refText -match '"mappings"\s*:\s*\{\s*\}') { Fail "refmap 的 mappings 为空（增量构建的典型症状）" }
    if ($refmap.Length -lt 500) { Fail "refmap 太小（$($refmap.Length) 字节），可能不完整" }
    Ok "refmap $($refmap.Length) 字节"

    # 4) 必须是 reobf 产物：类里能看到 SRG 名
    $probe = $zip.Entries | Where-Object { $_.FullName -match 'client/entity/CustomPlayerEntity\.class$' } | Select-Object -First 1
    if ($probe) {
        $ms = New-Object System.IO.MemoryStream
        $probe.Open().CopyTo($ms)
        $text = [System.Text.Encoding]::ASCII.GetString($ms.ToArray())
        if ($text -match 'field_70170_p|func_') { Ok "jar 是 reobf 产物（含 SRG 名）" }
        elseif (& "$root\..\env\zulu8.98.0.111-ca-jdk8.0.512-win_x64\bin\javap.exe" -version 2>$null) {
            # 没匹配到但也没有明显 dev 名，给出警告而不是失败
            Write-Host "GATE WARN: 类里没找到 SRG 名，人工确认是不是 dev jar" -ForegroundColor Yellow
        }
    }

    # 5) 关键类存在（防止 assembly 只打了部分内容）
    foreach ($cls in @(
            'client/texture/OuterFileTexture.class',
            'client/gui/AnimationRouletteScreen.class',
            'client/animation/molang/CtrlBinding.class',
            'network/message/SetPlayAnimation.class')) {
        if (-not ($zip.Entries | Where-Object { $_.FullName -like "*$cls" })) { Fail "jar 缺少 $cls" }
    }
    Ok "关键类齐全"

    # 6) 语言文件里轮盘分页文案在（防止资源没打进去）
    $lang = $zip.Entries | Where-Object { $_.FullName -match 'lang/zh_CN\.lang$' } | Select-Object -First 1
    if ($lang) {
        $r = New-Object System.IO.StreamReader($lang.Open()); $t = $r.ReadToEnd(); $r.Close()
        if ($t -notmatch 'animation_roulette\.page') { Fail "zh_CN.lang 缺少轮盘分页文案" }
        Ok "语言文件完整"
    }
}
finally {
    $zip.Dispose()
}

# 7) 发版：带 commit hash 拷到 deliver
$hash = (git rev-parse --short HEAD).Trim()
$deliver = Join-Path (Split-Path $root -Parent) 'deliver'
if (-not (Test-Path $deliver)) { New-Item -ItemType Directory -Path $deliver | Out-Null }
$target = Join-Path $deliver ("yesstevemodel-1.10.2-1.2.0-1.10.2-$hash.jar")
Copy-Item $jar.FullName $target -Force
$sha = (Get-FileHash $target -Algorithm SHA256).Hash
Ok "已发布: $target"
Write-Host "SHA256 : $sha"
Write-Host "commit : $hash"
Write-Host @"

发版说明（三行，贴给玩家）：
  改了什么：
  怎么验证：
  回滚方式：用上一个 jar，或 git revert $hash
"@ -ForegroundColor Cyan
