$root = 'D:\KALCPOS\frontend'
$backend = 'http://127.0.0.1:8081'
$logFile = Join-Path $root 'proxy_requests.log'
if (-not (Test-Path $root)) { Write-Error "Root not found: $root"; exit 2 }
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add('http://127.0.0.1:8000/')
$listener.Start()
Write-Output "Serving $root on http://127.0.0.1:8000/ (proxying /api and /ws to $backend). Logging to $logFile"
Add-Content -Path $logFile -Value "--- Proxy started at $(Get-Date) ---"
while ($true) {
    try {
        $ctx = $listener.GetContext()
        $req = $ctx.Request
        $rawPath = $req.RawUrl.TrimStart('/')
        if ($rawPath -eq '') { $rawPath = 'index.html' }

        # Log request
        $entry = "$(Get-Date -Format o) | ${($req.HttpMethod)} ${($req.Url.PathAndQuery)} | Remote: $($req.RemoteEndPoint)"
        Add-Content -Path $logFile -Value $entry

        # Proxy API and WS requests to backend
        if ($req.Url.AbsolutePath.StartsWith('/api/') -or $req.Url.AbsolutePath.StartsWith('/ws/')) {
            $target = $backend + $req.Url.PathAndQuery
            Add-Content -Path $logFile -Value ("Proxying to: $target")
            try {
                $webReq = [System.Net.WebRequest]::Create($target)
                $webReq.Method = $req.HttpMethod
                $webReq.Timeout = 30000

                foreach ($h in $req.Headers.AllKeys) {
                    switch ($h.ToLower()) {
                        'host' { continue }
                        'content-length' { continue }
                        'transfer-encoding' { continue }
                        'origin' { continue }
                        'referer' { continue }
                        'access-control-request-headers' { continue }
                        'access-control-request-method' { continue }
                        { $_ -like 'sec-*' } { continue }
                        'x-requested-with' { continue }
                        default { try { $webReq.Headers.Add($h, $req.Headers[$h]) } catch { } }
                    }
                }

                if ($req.HasEntityBody) {
                    # Read input stream into a memory buffer without relying on Length
                    try {
                        $ms = New-Object System.IO.MemoryStream
                        $buffer = New-Object byte[] 8192
                        while (($read = $req.InputStream.Read($buffer, 0, $buffer.Length)) -gt 0) {
                            $ms.Write($buffer, 0, $read)
                        }
                        $req.InputStream.Close()
                        $bytes = $ms.ToArray()
                        $ms.Close()
                        $webReq.ContentLength = $bytes.Length
                        $wstream = $webReq.GetRequestStream()
                        $wstream.Write($bytes, 0, $bytes.Length)
                        $wstream.Close()
                        Add-Content -Path $logFile -Value ("Request body length: $($bytes.Length)")
                    } catch {
                        Add-Content -Path $logFile -Value ("Failed reading request body: $($_.Exception.Message)")
                    }
                }

                $resp = $webReq.GetResponse()
                $respStream = $resp.GetResponseStream()
                $buffer = New-Object byte[] 8192
                $read = 0

                $ctx.Response.StatusCode = [int]$resp.StatusCode
                foreach ($hk in $resp.Headers.AllKeys) { try { $ctx.Response.Headers.Add($hk, $resp.Headers[$hk]) } catch { } }

                while (($read = $respStream.Read($buffer, 0, $buffer.Length)) -gt 0) {
                    $ctx.Response.OutputStream.Write($buffer, 0, $read)
                }
                $respStream.Close()
                $resp.Close()
                $ctx.Response.OutputStream.Close()
                Add-Content -Path $logFile -Value ("Proxied response status: $($ctx.Response.StatusCode)")
                continue
            } catch {
                $ctx.Response.StatusCode = 502
                $bytes = [System.Text.Encoding]::UTF8.GetBytes(("Proxy error: {0}" -f $_.Exception.Message))
                $ctx.Response.ContentLength64 = $bytes.Length
                $ctx.Response.OutputStream.Write($bytes,0,$bytes.Length)
                $ctx.Response.OutputStream.Close()
                Add-Content -Path $logFile -Value ("Proxy error: $($_.Exception.Message)")
                continue
            }
        }

        # Serve static files for other GET requests
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
    } catch {
        Add-Content -Path $logFile -Value ("Listener error: $($_.Exception.Message)")
        Start-Sleep -Milliseconds 10
    }
}

