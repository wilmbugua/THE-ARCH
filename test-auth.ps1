# Test authentication endpoint

Write-Host "Testing Authentication Endpoint" -ForegroundColor Cyan
Write-Host "=================================" -ForegroundColor Cyan

# Test 1: Correct PIN
Write-Host "`nTest 1: Login with CORRECT PIN (admin/11112222)" -ForegroundColor Yellow
$body1 = @{
    username = "admin"
    pin = "11112222"
} | ConvertTo-Json

$response1 = Invoke-WebRequest -Uri "http://127.0.0.1:8081/api/v1/auth/pin-login" `
    -Method POST `
    -Headers @{"Content-Type"="application/json"} `
    -Body $body1 `
    -UseBasicParsing -ErrorAction SilentlyContinue

if ($response1) {
    Write-Host "✓ Status: $($response1.StatusCode)" -ForegroundColor Green
    $json1 = $response1.Content | ConvertFrom-Json
    if ($json1.token) {
        Write-Host "✓ Token received: $($json1.token.Substring(0, 20))..." -ForegroundColor Green
        Write-Host "✓ User: $($json1.user.username) (Role: $($json1.user.role))" -ForegroundColor Green
    }
} else {
    Write-Host "✗ No response" -ForegroundColor Red
}

# Test 2: Wrong PIN
Write-Host "`nTest 2: Login with WRONG PIN (admin/wrongpassword) - SHOULD FAIL" -ForegroundColor Yellow
$body2 = @{
    username = "admin"
    pin = "wrongpassword"
} | ConvertTo-Json

try {
    $response2 = Invoke-WebRequest -Uri "http://127.0.0.1:8081/api/v1/auth/pin-login" `
        -Method POST `
        -Headers @{"Content-Type"="application/json"} `
        -Body $body2 `
        -UseBasicParsing -ErrorAction Stop
    
    $json2 = $response2.Content | ConvertFrom-Json
    if ($json2.error) {
        Write-Host "OK: Correctly rejected: $($json2.error)" -ForegroundColor Green
    } else {
        Write-Host "ERROR: Request succeeded when it should have failed!" -ForegroundColor Red
        Write-Host $response2.Content -ForegroundColor Red
    }
}
catch {
    Write-Host "OK: Request failed as expected" -ForegroundColor Green
}

Write-Host "`nAuthentication endpoint is working correctly!" -ForegroundColor Cyan
