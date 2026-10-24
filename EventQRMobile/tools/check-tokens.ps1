<#
.SYNOPSIS
    EventQR Android design-token guard: finds colour / type / size / spacing / string literals that should be tokens.

.DESCRIPTION
    Report-only by default: prints a count per category and always exits 0.
    With -Enforce it exits 1 when any blocking category is non-zero. Not wired into Gradle `check` yet (only
    `gradlew checkTokens`); wire it in once the migration sweep is done.
    Runs on Windows PowerShell 5.1 (what `gradlew checkTokens` uses on Windows) and on pwsh 7 (Linux / macOS):
    every path is normalised to forward slashes before it is matched.

    Colour categories
      hex          colour hex literals in res XML (exempt: res/values/colors*.xml, strings*.xml, ic_*.xml, ic_launcher*)
      kotlin       colour literals in Kotlin (comments are ignored): Color.parseColor, 0xFF...., android.graphics
                   Color.WHITE/BLACK/TRANSPARENT/RED/..., Color.rgb/argb/valueOf, and Compose Color.White/Black/Transparent/Red/...,
                   Color(0x........) of any alpha, Color(r, g, b), Color(red = ...)
                   (exempt files: ReportExporter, IdCardLayoutConfig, AndroidIdPrinter - see $ktAllow)
      primitive    @color/p_* referenced outside values/colors*.xml (use roles, not primitives); the only other allowed
                   file is res/color/primary_ripple.xml (brand state list that must follow p_brand, see $primAllow)
      deco         (info only) @color/deco_* / R.color.deco_* references = decorative one-offs, tracked design debt

    Size / spacing / text categories (phase 2)
      dp-layout    literal dp/sp on layout_width/height, min/maxWidth/Height, drawablePadding, elevation, translationX/Y/Z,
                   lineSpacingExtra, cardElevation, strokeWidth, boxStrokeWidth, chipStrokeWidth, chipMinHeight,
                   textStart/EndPadding, iconSize, iconPadding, trackThickness, chipCornerRadius, cornerRadius, cardCornerRadius,
                   contentPadding, autoSizeMin/MaxTextSize in res/layout. 0dp and 1dp are allowed. SKELETON placeholders are
                   exempt (file include_skeleton_*.xml, or an element whose tag references a bg_*skeleton* / bg_dashboard_loading_*
                   drawable): the exempt count is printed. tools/dp-allow.txt exempts reviewed single-use values.
      dp-spacing   literal dp on padding* / layout_margin* in res XML, except 0dp / 1dp. tools/dp-allow.txt exempts reviewed values.
      drawable     dp/sp/px on radius/width/height/size/inset*/padding/left|top|right|bottom|start|end/gradientRadius/dashWidth/
                   dashGap/*Radius in NON-vector res/drawable*. 0dp is allowed. Vector root width/height, ic_launcher* and
                   android:angle are not checked.
      float        literal floats on android:alpha, letterSpacing, lineSpacingMultiplier (0 and 1 allowed) in res XML
                   (values/dimens.xml excluded)
      fontfamily   literal android:fontFamily="sans..." in layouts (use a TextAppearance.EventQR.* style)
      styles       numeric sp/dp/letterSpacing items in values/styles_*.xml, themes.xml (0dp insets and 0 allowed; dimens.xml is
                   where the numbers live)
      strings      android:text / hint / contentDescription literals (anything not starting with @ or ?) in layouts. tools:text
                   is allowed.
      textsize     android:textSize="Nsp" literals in res/layout, minus the NAMED exceptions in $textExceptions (empty after phase 2)
      stale        $textExceptions, dp-allow.txt and component-exceptions.txt entries that no longer match anything

    Kotlin categories (phase 2)
      kotlin-dp    dp(<n>) calls with a non-zero literal, displayMetrics.density, COMPLEX_UNIT_DIP/SP, applyDimension
                   (Tokens.kt and the allow-listed files are exempt)
      kotlin-shape cornerRadius = n, setStroke(n..., elevation = n, .alpha = n, Typeface.BOLD/NORMAL, textSize = n,
                   setTextSize(literal), postDelayed(..., n), duration = n (0 and 1 allowed for alpha/elevation/radius)
      compose      <n>.dp / <n>.sp literals anywhere in Kotlin (0.dp allowed)

.PARAMETER Enforce
    Exit with code 1 if a blocking category is non-zero. Default: report only (exit 0).
    Also available as `gradlew checkTokens` (registered in the root build.gradle.kts; not wired into `check`/CI).

.PARAMETER Detail
    Also list the offending files per category with their counts.

.PARAMETER PerFileCsv
    Write one row per (file, category) with its count to this CSV path (used to regenerate tools/token-ownership.csv).

.PARAMETER ByOwner
    Print the totals per work-package owner (needs tools/token-ownership.csv).

.EXAMPLE
    pwsh -File tools/check-tokens.ps1 -Detail
    pwsh -File tools/check-tokens.ps1 -Enforce
#>
[CmdletBinding()]
param(
    [switch]$Enforce,
    [switch]$Detail,
    [switch]$ByOwner,
    [string]$PerFileCsv = ''
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$main = [System.IO.Path]::Combine($root, 'app', 'src', 'main')
$res = [System.IO.Path]::Combine($main, 'res')
$java = [System.IO.Path]::Combine($main, 'java')
$dpAllowFile = Join-Path $PSScriptRoot 'dp-allow.txt'
$compExcFile = Join-Path $PSScriptRoot 'component-exceptions.txt'
$ownerFile = Join-Path $PSScriptRoot 'token-ownership.csv'

# All matching is done on forward-slash paths so the script behaves the same on Windows and Linux.
function Get-NormPath([string]$p) { $p.Replace('\', '/') }
function Get-RelPath([string]$p) { (Get-NormPath $p).Substring((Get-NormPath $root).Length + 1) }

function Read-Text([string]$p) { [System.IO.File]::ReadAllText($p) }

# Removes // and /* */ comments from Kotlin source while leaving string literals untouched.
$ktCommentRx = [regex]::new('"(?:\\.|[^"\\\r\n])*"|//[^\r\n]*|/\*.*?\*/', 'Singleline')
$ktCommentEval = [System.Text.RegularExpressions.MatchEvaluator]{ param($m) if ($m.Value.StartsWith('"')) { $m.Value } else { ' ' } }
function Read-KotlinCode([string]$p) { $ktCommentRx.Replace((Read-Text $p), $ktCommentEval) }

# Removes <!-- --> comments from XML (their prose would otherwise be parsed as tags / attributes).
$xmlCommentRx = [regex]::new('<!--.*?-->', 'Singleline')
function Read-XmlCode([string]$p) { $xmlCommentRx.Replace((Read-Text $p), ' ') }

# Result object: Total + per-file rows. Add-Hit records `n` hits for a file.
function New-Result { [pscustomobject]@{ Total = 0; Rows = (New-Object System.Collections.ArrayList) } }
function Add-Hit($result, [string]$file, [int]$n) {
    if ($n -le 0) { return }
    [void]$result.Rows.Add([pscustomobject]@{ File = $file; Count = $n })
    $result.Total += $n
}

# Counts regex matches per file; returns {Total, Rows}.
function Count-Matches($files, [string]$pattern, [scriptblock]$reader = $null) {
    $rx = [regex]::new($pattern)
    $r = New-Result
    foreach ($f in $files) {
        $text = if ($reader) { & $reader $f.FullName } else { Read-Text $f.FullName }
        Add-Hit $r (Get-RelPath $f.FullName) $rx.Matches($text).Count
    }
    return $r
}

# ---- allow-lists -------------------------------------------------------------------------------------------------
# dp-allow.txt:               file|attr|value|reason     e.g. activity_foo.xml|layout_width|58dp|logo is not on the scale
# component-exceptions.txt:   file|needle|reason         e.g. activity_foo.xml|@drawable/bg_scanner_button|single-site look
# '#' starts a comment line. An entry that matches nothing is STALE and fails the check.
function Read-Allow([string]$path, [int]$minParts) {
    $list = @()
    if (-not (Test-Path $path)) { return $list }
    foreach ($line in [System.IO.File]::ReadAllLines($path)) {
        $t = $line.Trim()
        if ($t.Length -eq 0 -or $t.StartsWith('#')) { continue }
        $parts = $t.Split('|')
        if ($parts.Count -lt $minParts) { continue }
        $list += , @{ Parts = $parts; Hits = 0; Line = $t }
    }
    return $list
}
$dpAllow = @(Read-Allow $dpAllowFile 3)
$compExc = @(Read-Allow $compExcFile 2)

function Test-DpAllowed([string]$fileName, [string]$attr, [string]$value) {
    foreach ($e in $dpAllow) {
        if ($e.Parts[0].Trim() -eq $fileName -and $e.Parts[1].Trim() -eq $attr -and $e.Parts[2].Trim() -eq $value) { $e.Hits++; return $true }
    }
    return $false
}

$resXml = @(Get-ChildItem -Path $res -Recurse -Filter *.xml -File)
$kotlin = @(Get-ChildItem -Path $java -Recurse -Filter *.kt -File)
$layouts = @($resXml | Where-Object { (Get-NormPath $_.FullName) -like '*/res/layout*' })
$drawables = @($resXml | Where-Object { (Get-NormPath $_.FullName) -like '*/res/drawable*' })

# ---- 1. hex literals in res XML ------------------------------------------------------------------------------
$hexFiles = $resXml | Where-Object {
    $_.Name -notlike 'colors*.xml' -and $_.Name -notlike 'strings*.xml' -and
    $_.Name -notlike 'ic_*.xml' -and $_.Name -notlike 'ic_launcher*'
}
$hex = Count-Matches $hexFiles '(?<=["''>])#(?:[0-9A-Fa-f]{8}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{3,4})(?=["''<])'

# ---- 2. text sizes in layouts ---------------------------------------------------------------------------------
# After phase 2 every size comes from a TextAppearance.EventQR.* style, so the exception list is EMPTY. A single named
# exception is allowed only where an AppCompatButton site cannot match a ButtonPlatform* style. Entry shape:
#   @{ File = 'activity_x.xml'; Id = 'btnId' (or '*' when the element has no android:id); Size = '15sp'; Why = '...' }
$textExceptions = @()
$tagRx = [regex]::new('<([A-Za-z0-9_.]+)\b[^>]*>', 'Singleline')
$sizeRx = [regex]::new('android:textSize="(\d+(?:\.\d+)?sp)"')
$idRx = [regex]::new('android:id="@\+id/([A-Za-z0-9_]+)"')
$text = New-Result; $textExempt = 0
$used = @{}
foreach ($f in $layouts) {
    $n = 0
    foreach ($m in $tagRx.Matches((Read-XmlCode $f.FullName))) {
        $sm = $sizeRx.Match($m.Value)
        if (-not $sm.Success) { continue }
        $size = $sm.Groups[1].Value
        $im = $idRx.Match($m.Value)
        $id = if ($im.Success) { $im.Groups[1].Value } else { '' }
        $hit = $false
        for ($i = 0; $i -lt $textExceptions.Count; $i++) {
            $e = $textExceptions[$i]
            if ($e.File -eq $f.Name -and $e.Size -eq $size -and ($e.Id -eq '*' -or $e.Id -eq $id)) { $hit = $true; $used[$i] = $true }
        }
        if ($hit) { $textExempt++ } else { $n++ }
    }
    Add-Hit $text (Get-RelPath $f.FullName) $n
}
$staleRows = New-Object System.Collections.ArrayList
for ($i = 0; $i -lt $textExceptions.Count; $i++) {
    if (-not $used.ContainsKey($i)) {
        $e = $textExceptions[$i]
        [void]$staleRows.Add([pscustomobject]@{ File = "textsize exception: $($e.File) id=$($e.Id) size=$($e.Size)"; Count = 1 })
    }
}

# ---- 3. dp-layout / dp-spacing / float / fontfamily / strings in layouts (one pass over the opening tags) ----------
$layoutDpAttrs = @{}
foreach ($a in @('layout_width', 'layout_height', 'minWidth', 'minHeight', 'maxWidth', 'maxHeight', 'drawablePadding', 'elevation',
        'translationX', 'translationY', 'translationZ', 'lineSpacingExtra', 'cardElevation', 'strokeWidth', 'boxStrokeWidth',
        'chipStrokeWidth', 'chipMinHeight', 'textStartPadding', 'textEndPadding', 'iconSize', 'iconPadding', 'trackThickness',
        'chipCornerRadius', 'cornerRadius', 'cardCornerRadius', 'contentPadding', 'autoSizeMinTextSize', 'autoSizeMaxTextSize')) { $layoutDpAttrs[$a] = $true }
$attrRx = [regex]::new('(?<ns>android|app):(?<name>[A-Za-z_]+)="(?<val>[^"]*)"')
$dimRx = [regex]::new('^-?\d+(?:\.\d+)?(?:dp|dip|sp)$')
$floatRx = [regex]::new('^-?\d*\.?\d+$')
$skeletonTagRx = [regex]::new('@drawable/(?:bg_staff_skeleton_|bg_skeleton_|bg_dashboard_loading_|bg_rewards_dropdown_skeleton)')
$dpLayout = New-Result; $dpLayoutExempt = 0
$dpSpacing = New-Result; $dpSpacingSkeleton = 0
$floatLit = New-Result
$fontFam = New-Result
$strings = New-Result
foreach ($f in $layouts) {
    $isSkeletonFile = $f.Name -like 'include_skeleton_*'
    $nLayout = 0; $nSpacing = 0; $nFloat = 0; $nFont = 0; $nStr = 0
    foreach ($m in $tagRx.Matches((Read-XmlCode $f.FullName))) {
        $tag = $m.Value
        $skeleton = $isSkeletonFile -or $skeletonTagRx.IsMatch($tag)
        foreach ($am in $attrRx.Matches($tag)) {
            $ns = $am.Groups['ns'].Value; $name = $am.Groups['name'].Value; $val = $am.Groups['val'].Value
            if ($layoutDpAttrs.ContainsKey($name) -and $dimRx.IsMatch($val)) {
                if ($val -ne '0dp' -and $val -ne '1dp') {
                    if ($skeleton) { $dpLayoutExempt++ }
                    elseif (Test-DpAllowed $f.Name $name $val) { }
                    else { $nLayout++ }
                }
            }
            elseif ($ns -eq 'android' -and ($name -like 'padding*' -or $name -like 'layout_margin*') -and $dimRx.IsMatch($val)) {
                if ($val -ne '0dp' -and $val -ne '1dp') {
                    if (Test-DpAllowed $f.Name $name $val) { }
                    else { $nSpacing++; if ($skeleton) { $dpSpacingSkeleton++ } }
                }
            }
            elseif ($ns -eq 'android' -and ($name -eq 'alpha' -or $name -eq 'letterSpacing' -or $name -eq 'lineSpacingMultiplier') -and $floatRx.IsMatch($val)) {
                if ($val -notmatch '^[01](?:\.0+)?$') { $nFloat++ }
            }
            elseif ($name -eq 'fontFamily' -and $val.Length -gt 0 -and $val[0] -ne '@' -and $val[0] -ne '?') { $nFont++ }
            elseif ($ns -eq 'android' -and ($name -eq 'text' -or $name -eq 'hint' -or $name -eq 'contentDescription') -and
                $val.Length -ge 0 -and -not ($val.StartsWith('@') -or $val.StartsWith('?'))) { $nStr++ }
        }
    }
    $rel = Get-RelPath $f.FullName
    Add-Hit $dpLayout $rel $nLayout; Add-Hit $dpSpacing $rel $nSpacing; Add-Hit $floatLit $rel $nFloat
    Add-Hit $fontFam $rel $nFont; Add-Hit $strings $rel $nStr
}

# dp-spacing / float also apply to drawables and non-layout XML (menus, styles use their own check below)
foreach ($f in ($resXml | Where-Object { (Get-NormPath $_.FullName) -notlike '*/res/layout*' -and (Get-NormPath $_.FullName) -notlike '*/res/values*' })) {
    $nSpacing = 0; $nFloat = 0
    foreach ($m in $tagRx.Matches((Read-XmlCode $f.FullName))) {
        foreach ($am in $attrRx.Matches($m.Value)) {
            $ns = $am.Groups['ns'].Value; $name = $am.Groups['name'].Value; $val = $am.Groups['val'].Value
            if ($ns -eq 'android' -and ($name -like 'padding*' -or $name -like 'layout_margin*') -and $dimRx.IsMatch($val) -and $val -ne '0dp' -and $val -ne '1dp') {
                if (-not (Test-DpAllowed $f.Name $name $val)) { $nSpacing++ }
            }
            elseif ($ns -eq 'android' -and ($name -eq 'alpha' -or $name -eq 'letterSpacing' -or $name -eq 'lineSpacingMultiplier') -and $floatRx.IsMatch($val) -and $val -notmatch '^[01](?:\.0+)?$') { $nFloat++ }
        }
    }
    $rel = Get-RelPath $f.FullName
    Add-Hit $dpSpacing $rel $nSpacing; Add-Hit $floatLit $rel $nFloat
}

# ---- 4. non-vector drawables -----------------------------------------------------------------------------------------
$drawAttrRx = [regex]::new('android:(?<name>radius|topLeftRadius|topRightRadius|bottomLeftRadius|bottomRightRadius|width|height|size|insetLeft|insetTop|insetRight|insetBottom|inset|padding|left|top|right|bottom|start|end|gradientRadius|dashWidth|dashGap|centerX|centerY)="(?<val>-?\d+(?:\.\d+)?(?:dp|dip|sp|px))"')
$rootRx = [regex]::new('<([A-Za-z0-9_.\-]+)')
$drawable = New-Result
foreach ($f in $drawables) {
    if ($f.Name -like 'ic_launcher*') { continue }
    $code = Read-XmlCode $f.FullName
    $first = $rootRx.Match(($code -replace '<\?xml[^>]*\?>', ''))
    if ($first.Success -and ($first.Groups[1].Value -eq 'vector' -or $first.Groups[1].Value -eq 'animated-vector')) { continue }
    $n = 0
    foreach ($m in $drawAttrRx.Matches($code)) {
        if ($m.Groups['val'].Value -eq '0dp') { continue }
        if (Test-DpAllowed $f.Name $m.Groups['name'].Value $m.Groups['val'].Value) { continue }
        $n++
    }
    Add-Hit $drawable (Get-RelPath $f.FullName) $n
}

# ---- 5. styles / themes -------------------------------------------------------------------------------------------------
$styleFiles = @($resXml | Where-Object { (Get-NormPath $_.FullName) -like '*/res/values*' -and ($_.Name -like 'styles*.xml' -or $_.Name -like 'themes*.xml') })
$styleItemRx = [regex]::new('<item\s+name="(?<name>[^"]+)"[^>]*>\s*(?<val>-?\d+(?:\.\d+)?(?:dp|dip|sp|px)?)\s*</item>')
$styles = New-Result
foreach ($f in $styleFiles) {
    $n = 0
    foreach ($m in $styleItemRx.Matches((Read-XmlCode $f.FullName))) {
        $name = $m.Groups['name'].Value; $val = $m.Groups['val'].Value
        if ($val -match '^(?:-?0+(?:\.0+)?)(?:dp|dip|sp|px)?$') { continue }                  # 0 / 0dp are allowed (insets, letterSpacing 0)
        if ($val -match '(?:dp|dip|sp|px)$' -or $name -match 'letterSpacing|alpha|lineSpacingMultiplier|textSize|elevation') { $n++ }
    }
    Add-Hit $styles (Get-RelPath $f.FullName) $n
}

# ---- 6. colour literals in Kotlin ------------------------------------------------------------------------------
# Files whose values are not UI tokens (printing / export pipelines with their own fixed palettes and pixel maths).
$ktAllow = @('ReportExporter.kt', 'IdCardLayoutConfig.kt', 'AndroidIdPrinter.kt')
$ktFiles = $kotlin | Where-Object { $ktAllow -notcontains $_.Name }
$named = 'White|Black|Transparent|Red|Green|Blue|Yellow|Cyan|Magenta|Gray|LightGray|DarkGray'
$androidNamed = 'WHITE|BLACK|TRANSPARENT|RED|GREEN|BLUE|YELLOW|CYAN|MAGENTA|GRAY|LTGRAY|DKGRAY'
$ktPattern = @(
    'Color\.parseColor',                                    # android.graphics.Color.parseColor("#...")
    '0x[Ff]{2}[0-9A-Fa-f]{6}\b',                            # 0xFF...... literal
    "\bColor\.(?:$androidNamed)\b",                         # android.graphics.Color.WHITE ...
    '\bColor\.(?:rgb|argb|valueOf)\(',                      # android.graphics.Color.rgb / argb / valueOf
    "(?<![\w.])Color\.(?:Companion\.)?(?:$named)\b",        # Compose Color.White ...
    '(?<![\w.])Color\(\s*(?:0x|red\s*=|\d)'                 # Compose Color(0x......) any alpha, Color(r, g, b), Color(red = ...)
) -join '|'
$kt = Count-Matches $ktFiles $ktPattern ${function:Read-KotlinCode}

# ---- 7. primitives referenced outside colour files -------------------------------------------------------------
# Allowed: values/colors*.xml and the brand state list res/color/primary_ripple.xml, which must follow p_brand directly.
$primAllow = @('primary_ripple.xml')
$primFiles = @($resXml | Where-Object {
    $_.Name -notlike 'colors*.xml' -and -not ((Get-NormPath $_.FullName) -like '*/res/color/*' -and $primAllow -contains $_.Name)
}) + @($kotlin)
$prim = Count-Matches $primFiles '@color/p_|R\.color\.p_'

# ---- 8. decorative colours (info only) ---------------------------------------------------------------------------
$decoFiles = @($resXml | Where-Object { $_.Name -notlike 'colors*.xml' }) + @($kotlin)
$deco = Count-Matches $decoFiles '@color/deco_|R\.color\.deco_'

# ---- 9. Kotlin size / shape / motion literals --------------------------------------------------------------------
# Tokens.kt is the one place allowed to touch density / units / Typeface.BOLD.
$ktTokens = $ktFiles | Where-Object { $_.Name -ne 'Tokens.kt' }
$ktDpPattern = @(
    '\bdp\(\s*-?[1-9]',                                     # dp(8), dp(16) ... (dp(0) and dp(CONSTANT) are fine)
    'displayMetrics\.density',
    'COMPLEX_UNIT_(?:DIP|SP)\b',
    '\bapplyDimension\b'
) -join '|'
$ktDp = Count-Matches $ktTokens $ktDpPattern ${function:Read-KotlinCode}
$ktShapePattern = @(
    '\bcornerRadius\s*=\s*-?(?!0(?:\.0+)?f?\b)\d',          # cornerRadius = 999f (0 allowed)
    '\bsetStroke\(\s*-?\d',                                 # setStroke(1, color)
    '\belevation\s*=\s*-?(?!0(?:\.0+)?f?\b)\d',             # elevation = 8f (0 allowed)
    '\.alpha\s*=\s*-?(?![01](?:\.0+)?f?\b)\d',              # .alpha = 0.5f (0 and 1 allowed)
    '\bTypeface\.(?:BOLD|NORMAL|BOLD_ITALIC|ITALIC)\b',     # use strong() from Tokens.kt
    '\btextSize\s*=\s*-?\d',                                # textSize = 14f
    '\bsetTextSize\(\s*(?:TypedValue\.COMPLEX_UNIT_\w+\s*,\s*)?-?\d',
    '\bpostDelayed\([^)]*,\s*\d',                           # postDelayed(r, 280)
    '\bduration\s*=\s*\d'                                   # duration = 280L
) -join '|'
$ktShape = Count-Matches ($ktTokens | Where-Object { $_.Name -ne 'Tokens.kt' }) $ktShapePattern ${function:Read-KotlinCode}
$composePattern = '(?<![\w.])(?!0\.dp\b)\d+(?:\.\d+)?\.(?:dp|sp)\b'
$compose = Count-Matches $ktTokens $composePattern ${function:Read-KotlinCode}

# ---- 10. stale allow-list entries --------------------------------------------------------------------------------
foreach ($e in $dpAllow) {
    if ($e.Hits -eq 0) { [void]$staleRows.Add([pscustomobject]@{ File = "dp-allow.txt: $($e.Line)"; Count = 1 }) }
}
foreach ($e in $compExc) {
    $fileName = $e.Parts[0].Trim(); $needle = $e.Parts[1].Trim()
    $match = @($resXml | Where-Object { $_.Name -eq $fileName } | Where-Object { (Read-Text $_.FullName).Contains($needle) })
    if ($match.Count -eq 0) { [void]$staleRows.Add([pscustomobject]@{ File = "component-exceptions.txt: $($e.Line)"; Count = 1 }) }
}
$stale = [pscustomobject]@{ Total = $staleRows.Count; Rows = $staleRows }

# ---- report ------------------------------------------------------------------------------------------------------
$categories = @(
    @{ Key = 'hex';          R = $hex;       Block = $true;  Msg = 'colour hex literals in res XML' },
    @{ Key = 'kotlin';       R = $kt;        Block = $true;  Msg = 'Color.parseColor / 0xFF.. / Color.WHITE|Color.White / Color(0x..) / rgb()' },
    @{ Key = 'primitive';    R = $prim;      Block = $true;  Msg = '@color/p_* used outside colour files' },
    @{ Key = 'dp-layout';    R = $dpLayout;  Block = $true;  Msg = 'literal dp/sp on size/elevation/stroke/... attrs in layouts' },
    @{ Key = 'dp-spacing';   R = $dpSpacing; Block = $true;  Msg = 'padding*/layout_margin* dp literals except 0dp/1dp' },
    @{ Key = 'drawable';     R = $drawable;  Block = $true;  Msg = 'dp literals in non-vector drawables' },
    @{ Key = 'float';        R = $floatLit;  Block = $true;  Msg = 'literal android:alpha / letterSpacing / lineSpacingMultiplier' },
    @{ Key = 'fontfamily';   R = $fontFam;   Block = $true;  Msg = 'literal android:fontFamily in layouts' },
    @{ Key = 'styles';       R = $styles;    Block = $true;  Msg = 'numeric sp/dp/letterSpacing in styles_*.xml / themes.xml' },
    @{ Key = 'strings';      R = $strings;   Block = $true;  Msg = 'android:text/hint/contentDescription literals in layouts' },
    @{ Key = 'textsize';     R = $text;      Block = $true;  Msg = 'android:textSize="Nsp" in layouts (outside the named exceptions)' },
    @{ Key = 'kotlin-dp';    R = $ktDp;      Block = $true;  Msg = 'dp(N) / density / COMPLEX_UNIT_* / applyDimension in Kotlin' },
    @{ Key = 'kotlin-shape'; R = $ktShape;   Block = $true;  Msg = 'cornerRadius/setStroke/elevation/alpha/Typeface/textSize/postDelayed/duration literals' },
    @{ Key = 'compose';      R = $compose;   Block = $true;  Msg = '<n>.dp / <n>.sp literals in Kotlin' },
    @{ Key = 'stale';        R = $stale;     Block = $true;  Msg = 'allow-list / exception entries matching nothing (delete them)' },
    @{ Key = 'deco';         R = $deco;      Block = $false; Msg = 'deco_* references (info only; decorative one-offs = design debt)' }
)

Write-Host ''
Write-Host 'EventQR token check'
Write-Host ('-' * 78)
foreach ($c in $categories) { '{0,-13} {1,6}   {2}' -f $c.Key, $c.R.Total, $c.Msg }
Write-Host ('-' * 78)
'{0,-13} {1,6}   {2}' -f 'exempt:skel', $dpLayoutExempt, 'dp-layout literals on skeleton placeholders (exempt by rule, info only)'
'{0,-13} {1,6}   {2}' -f 'exempt:text', $textExempt, 'textSize literals allowed by the named exception list (info only)'
'{0,-13} {1,6}   {2}' -f 'allow:dp', ($dpAllow | Measure-Object).Count, 'tools/dp-allow.txt entries'
'{0,-13} {1,6}   {2}' -f 'allow:comp', ($compExc | Measure-Object).Count, 'tools/component-exceptions.txt entries'
'{0,-13} {1,6}   {2}' -f 'info:skelsp', $dpSpacingSkeleton, 'of the dp-spacing hits sit on skeleton elements (not exempt)'
Write-Host ('-' * 78)

if ($Detail) {
    foreach ($c in $categories) {
        if ($c.R.Rows.Count -gt 0) {
            Write-Host ''
            Write-Host ("[{0}] {1} files" -f $c.Key, $c.R.Rows.Count)
            $c.R.Rows | Sort-Object Count -Descending | ForEach-Object { '  {0,5}  {1}' -f $_.Count, $_.File }
        }
    }
    Write-Host ''
}

if ($PerFileCsv -ne '') {
    $lines = New-Object System.Collections.ArrayList
    [void]$lines.Add('file,category,count')
    foreach ($c in $categories) { foreach ($r in $c.R.Rows) { [void]$lines.Add(('{0},{1},{2}' -f $r.File, $c.Key, $r.Count)) } }
    [System.IO.File]::WriteAllLines($PerFileCsv, [string[]]$lines.ToArray())
}

if ($ByOwner -and (Test-Path $ownerFile)) {
    $owners = @{}
    foreach ($line in ([System.IO.File]::ReadAllLines($ownerFile) | Select-Object -Skip 1)) {
        $cols = $line.Split(','); if ($cols.Count -ge 3) { $owners[$cols[0]] = $cols[2] }
    }
    $perOwner = @{}
    foreach ($c in $categories) {
        if (-not $c.Block) { continue }
        foreach ($r in $c.R.Rows) {
            $rel = ($r.File -replace '^app/src/main/', '')
            $o = if ($owners.ContainsKey($rel)) { $owners[$rel] } else { '(unowned)' }
            if (-not $perOwner.ContainsKey($o)) { $perOwner[$o] = 0 }
            $perOwner[$o] += $r.Count
        }
    }
    Write-Host 'Remaining blocking hits per owner:'
    $perOwner.GetEnumerator() | Sort-Object Name | ForEach-Object { '  {0,-10} {1,6}' -f $_.Name, $_.Value }
    Write-Host ''
}

$fail = $false
foreach ($c in $categories) { if ($c.Block -and $c.R.Total -gt 0) { $fail = $true } }
if ($Enforce) {
    if ($fail) { Write-Host 'FAIL (enforce): blocking categories above are non-zero.'; exit 1 }
    Write-Host 'OK (enforce)'
    exit 0
}
Write-Host ('report-only mode: exit 0' + $(if ($fail) { ' (would FAIL under -Enforce)' } else { '' }))
exit 0
