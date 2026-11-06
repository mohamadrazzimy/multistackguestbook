$marker = [guid]::NewGuid().ToString('N')
foreach ($port in 8081..8087) {
    $base = "http://localhost:$port"
    $page = Invoke-WebRequest "$base/"
    if ($page.StatusCode -ne 200) { throw "Page failed on $port" }
    $body = @{ email = 'visitor@example.com'; comment = "Hello $marker" }
    $saved = Invoke-WebRequest "$base/api/entries" -Method Post `
        -ContentType 'application/x-www-form-urlencoded' -Body $body
    if ($saved.StatusCode -ne 201) { throw "Save failed on $port" }
    $public = Invoke-WebRequest "$base/api/entries"
    $rows = @($public.Content | ConvertFrom-Json)
    if (-not ($rows | Where-Object comment -eq "Hello $marker")) {
        throw "Saved comment missing on $port"
    }
    if ($public.Content -match 'visitor@example.com' -or
        $public.Content -match '"email"\s*:') {
        throw "Email leaked on $port"
    }
    $bad = Invoke-WebRequest "$base/api/entries" -Method Post `
        -ContentType 'application/x-www-form-urlencoded' `
        -Body @{ email = 'invalid'; comment = 'Hello' } -SkipHttpErrorCheck
    if ($bad.StatusCode -ne 400) { throw "Validation failed on $port" }
    "Port ${port}: page, submission, privacy and validation passed"
}
