# Simple HttpClient-based proxy and static server (port 8100)
$root = 'D:\KALCPOS\frontend'
$backend = 'http://127.0.0.1:8081'
$logFile = Join-Path $root 'proxy_requests_8100.log'
if (-not (Test-Path $root)) { Write-Error "Root not found: $root"; exit 2 }

Add-Type -AssemblyName System.Net.Http
Add-Content -Path $logFile -Value "--- Proxy started at $(Get-Date) ---"

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add('http://127.0.0.1:8100/')
$listener.Start()
Write-Output "Serving $root on http://127.0.0.1:8100/ (proxying /api and /ws to $backend). Logging to $logFile"

$handler = New-Object System.Net.Http.HttpClientHandler
$handler.AllowAutoRedirect = $false
$httpClient = New-Object System.Net.Http.HttpClient($handler)
$httpClient.Timeout = [System.TimeSpan]::FromSeconds(30)

while ($true) {
    try {
        $ctx = $listener.GetContext()
        $req = $ctx.Request
        $rawPath = $req.RawUrl.TrimStart('/')
        if ($rawPath -eq '') { $rawPath = 'index.html' }

        $entry = "$(Get-Date -Format o) | $($req.HttpMethod) $($req.Url.PathAndQuery) | Remote: $($req.RemoteEndPoint)"
        Add-Content -Path $logFile -Value $entry

        if ($req.Url.AbsolutePath.StartsWith('/api/') -or $req.Url.AbsolutePath.StartsWith('/ws/')) {
            $target = $backend + $req.Url.PathAndQuery
            Add-Content -Path $logFile -Value ("Proxying to: $target")
            try {
                $method = [System.Net.Http.HttpMethod]::new($req.HttpMethod)
                $message = New-Object System.Net.Http.HttpRequestMessage($method, $target)
                if ($req.HasEntityBody) {
                    $ms = New-Object System.IO.MemoryStream
                    $buffer = New-Object byte[] 8192
                    while (($read = $req.InputStream.Read($buffer, 0, $buffer.Length)) -gt 0) {
                        $ms.Write($buffer, 0, $read)
                    }
                    $bytes = $ms.ToArray()
                    $ms.Close()
                    $content = [System.Net.Http.ByteArrayContent]::new($bytes)
                    if ($req.ContentType) {
                        try { $content.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($req.ContentType) } catch { }
                    }
                    $message.Content = $content
                    Add-Content -Path $logFile -Value ("Request body length: $($bytes.Length)")
                }
                foreach ($h in $req.Headers.AllKeys) {
                    $lower = $h.ToLower()
                    if ($lower -in @('host','content-length','transfer-encoding','origin','referer','access-control-request-headers','access-control-request-method','connection')) { continue }
                    if ($lower -like 'sec-*' -or $lower -eq 'x-requested-with') { continue }
                    try { $message.Headers.TryAddWithoutValidation($h, $req.Headers[$h]) | Out-Null } catch { }
                }
                $resp = $httpClient.SendAsync($message).GetAwaiter().GetResult()
                $ctx.Response.StatusCode = [int]$resp.StatusCode
                foreach ($hdr in $resp.Headers) { try { $ctx.Response.Headers.Add($hdr.Key, ($hdr.Value -join ',')) } catch { } }
                if ($resp.Content) {
                    foreach ($hdr in $resp.Content.Headers) { try { $ctx.Response.Headers.Add($hdr.Key, ($hdr.Value -join ',')) } catch { } }
                    $stream = $resp.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
                    $buffer = New-Object byte[] 8192
                    while (($read = $stream.Read($buffer, 0, $buffer.Length)) -gt 0) { $ctx.Response.OutputStream.Write($buffer, 0, $read) }
                    $stream.Close()
                }
                $ctx.Response.OutputStream.Close()
                Add-Content -Path $logFile -Value ("Proxied response status: $($ctx.Response.StatusCode)")
                continue
            } catch {
                $err = $_.Exception.Message
                $ctx.Response.StatusCode = 502
                $bytes = [System.Text.Encoding]::UTF8.GetBytes(("Proxy error: {0}" -f $err))
                $ctx.Response.ContentLength64 = $bytes.Length
                $ctx.Response.OutputStream.Write($bytes,0,$bytes.Length)
                $ctx.Response.OutputStream.Close()
                Add-Content -Path $logFile -Value ("Proxy error: $err")
                continue
            }
        }
        if ($req.HttpMethod -ne 'GET') { $ctx.Response.StatusCode = 405; $ctx.Response.Close(); continue }
        $fp = Join-Path $root ($rawPath -replace '/','\\')
        if (-not (Test-Path $fp)) { $ctx.Response.StatusCode = 404; $ctx.Response.Close(); Add-Content -Path $logFile -Value ("Static 404: $fp") ; continue }
        $b = [System.IO.File]::ReadAllBytes($fp)
        $ext = [System.IO.Path]::GetExtension($fp).ToLowerInvariant()
        $ct = switch ($ext) { '.html' { 'text/html' } '.js' { 'application/javascript' } '.css' { 'text/css' } '.svg' { 'image/svg+xml' } '.json' { 'application/json' } '.png' { 'image/png' } '.jpg' { 'image/jpeg' } default { 'application/octet-stream' } }
        $ctx.Response.ContentType = $ct
        $ctx.Response.ContentLength64 = $b.Length
        $ctx.Response.OutputStream.Write($b,0,$b.Length)
        $ctx.Response.OutputStream.Close()
    } catch { Add-Content -Path $logFile -Value ("Listener error: $($_.Exception.Message)"); Start-Sleep -Milliseconds 10 }
}

