$root = 'D:\KALCPOS\frontend'
$backend = 'http://127.0.0.1:8081'
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add('http://127.0.0.1:8000/')
$listener.Start()
Write-Output "Serving $root on http://127.0.0.1:8000/ (proxying /api and /ws to $backend)"
while ($true) {
    try {
        $ctx = $listener.GetContext()
        $req = $ctx.Request
        $rawPath = $req.Url.AbsolutePath.TrimStart('/')
        if ($rawPath -eq '') { $rawPath = 'index.html' }

        # Proxy API and WS requests to backend
        if ($req.Url.AbsolutePath.StartsWith('/api/') -or $req.Url.AbsolutePath.StartsWith('/ws/')) {
            $target = $backend + $req.Url.PathAndQuery
            try {
                $webReq = [System.Net.WebRequest]::Create($target)
                $webReq.Method = $req.HttpMethod
                $webReq.Timeout = 30000

                # Copy headers (skip some restricted headers)
                foreach ($h in $req.Headers.AllKeys) {
                    switch ($h.ToLower()) {
                        'host' { continue }
                        'content-length' { continue }
                        'transfer-encoding' { continue }
                        default { try { $webReq.Headers.Add($h, $req.Headers[$h]) } catch { } }
                    }
                }

                if ($req.HasEntityBody) {
                    $ms = New-Object System.IO.MemoryStream
                    $req.InputStream.CopyTo($ms)
                    $bytes = $ms.ToArray()
                    $ms.Close()
                    $req.InputStream.Close()
                    if ($req.ContentType) { $webReq.ContentType = $req.ContentType }
                    $webReq.ContentLength = $bytes.Length
                    $wstream = $webReq.GetRequestStream()
                    $wstream.Write($bytes, 0, $bytes.Length)
                    $wstream.Close()
                }

                $resp = $webReq.GetResponse()
                $respStream = $resp.GetResponseStream()
                $buffer = New-Object byte[] 8192
                $read = 0

                $ctx.Response.StatusCode = [int]$resp.StatusCode
                foreach ($hk in $resp.Headers.AllKeys) {
                    try { $ctx.Response.Headers.Add($hk, $resp.Headers[$hk]) } catch { }
                }

                while (($read = $respStream.Read($buffer, 0, $buffer.Length)) -gt 0) {
                    $ctx.Response.OutputStream.Write($buffer, 0, $read)
                }
                $respStream.Close()
                $resp.Close()
                $ctx.Response.OutputStream.Close()
                continue
            } catch [System.Net.WebException] {
                if ($_.Exception.Response) {
                    $resp = $_.Exception.Response
                    $ctx.Response.StatusCode = [int]$resp.StatusCode
                    foreach ($hk in $resp.Headers.AllKeys) {
                        try { $ctx.Response.Headers.Add($hk, $resp.Headers[$hk]) } catch { }
                    }
                    $respStream = $resp.GetResponseStream()
                    $buffer = New-Object byte[] 8192
                    $read = 0
                    while (($read = $respStream.Read($buffer, 0, $buffer.Length)) -gt 0) {
                        $ctx.Response.OutputStream.Write($buffer, 0, $read)
                    }
                    $respStream.Close()
                    $resp.Close()
                    $ctx.Response.OutputStream.Close()
                    continue
                } else {
                    $ctx.Response.StatusCode = 502
                    $bytes = [System.Text.Encoding]::UTF8.GetBytes(("Proxy error: {0}" -f $_.Exception.Message))
                    $ctx.Response.ContentLength64 = $bytes.Length
                    $ctx.Response.OutputStream.Write($bytes,0,$bytes.Length)
                    $ctx.Response.OutputStream.Close()
                    continue
                }
            } catch {
                $ctx.Response.StatusCode = 502
                $bytes = [System.Text.Encoding]::UTF8.GetBytes(("Proxy error: {0}" -f $_.Exception.Message))
                $ctx.Response.ContentLength64 = $bytes.Length
                $ctx.Response.OutputStream.Write($bytes,0,$bytes.Length)
                $ctx.Response.OutputStream.Close()
                continue
            }
        }

        # Serve static files for other GET requests
        if ($req.HttpMethod -ne 'GET') { $ctx.Response.StatusCode = 405; $ctx.Response.Close(); continue }

        $fp = Join-Path $root ($rawPath -replace '/','\\')
        if (-not (Test-Path $fp)) { $ctx.Response.StatusCode = 404; $ctx.Response.Close(); continue }
        $b = [System.IO.File]::ReadAllBytes($fp)
        $ext = [System.IO.Path]::GetExtension($fp).ToLowerInvariant()
        $ct = switch ($ext) {
            '.html' { 'text/html' }
            '.js' { 'application/javascript' }
            '.css' { 'text/css' }
            '.svg' { 'image/svg+xml' }
            '.json' { 'application/json' }
            '.png' { 'image/png' }
            '.jpg' { 'image/jpeg' }
            default { 'application/octet-stream' }
        }
        $ctx.Response.ContentType = $ct
        $ctx.Response.ContentLength64 = $b.Length
        $ctx.Response.OutputStream.Write($b,0,$b.Length)
        $ctx.Response.OutputStream.Close()
    } catch {
        Start-Sleep -Milliseconds 10
    }
}

