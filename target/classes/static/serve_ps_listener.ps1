$root = 'D:\KALCPOS\frontend'
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add('http://127.0.0.1:8000/')
$listener.Start()
Write-Output "Serving $root on http://127.0.0.1:8000/"
while ($true) {
    try {
        $ctx = $listener.GetContext()
        $p = $ctx.Request.RawUrl.TrimStart('/')
        if ($p -eq '') { $p = 'index.html' }
        $fp = Join-Path $root ($p -replace '/','\\')
        if (-not (Test-Path $fp)) {
            $ctx.Response.StatusCode = 404
            $ctx.Response.Close()
            continue
        }
        $b = [System.IO.File]::ReadAllBytes($fp)
        $ext = [System.IO.Path]::GetExtension($fp).ToLowerInvariant()
        switch ($ext) {
            '.html' { $ct = 'text/html' }
            '.js'   { $ct = 'application/javascript' }
            '.css'  { $ct = 'text/css' }
            '.svg'  { $ct = 'image/svg+xml' }
            default { $ct = 'application/octet-stream' }
        }
        $ctx.Response.ContentType = $ct
        $ctx.Response.ContentLength64 = $b.Length
        $ctx.Response.OutputStream.Write($b,0,$b.Length)
        $ctx.Response.OutputStream.Close()
    } catch {
        Start-Sleep -Milliseconds 10
    }
}

