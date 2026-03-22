# setup_branches.ps1
# Place this file inside the Hash-Table folder, then run:
# powershell -ExecutionPolicy Bypass -File setup_branches.ps1

$SRC = Split-Path -Parent (Get-Location)

Write-Host "=== Hash Table Project Branch Setup ===" -ForegroundColor Cyan
Write-Host "Repo  : $(Get-Location)"
Write-Host "Source: $SRC"
Write-Host ""

# Configure git user if not already set
$gitName = & git config user.name 2>$null
if (-not $gitName) {
    & git config user.email "you@example.com"
    & git config user.name "Hash Table Setup"
}

function Create-Branch {
    param([string]$Branch, [string]$SourceDir, [string]$FileName, [string]$Description)

    Write-Host "-> $Branch" -ForegroundColor Yellow
    & git checkout main --quiet
    & git branch -D $Branch 2>$null
    & git checkout -b $Branch --quiet
    New-Item -ItemType Directory -Force -Path "src" | Out-Null
    Copy-Item "$SourceDir\$FileName" "src\$FileName" -Force
    $javaClass = $FileName -replace '\.java',''
    $content = "# $Description`n`nBranch: $Branch`n`n## Run`n`ncmd: cd src && javac $FileName && java $javaClass`n"
    Set-Content -Path "README.md" -Value $content -Encoding UTF8
    & git add .
    & git commit -m "feat: $Description" --quiet
    Write-Host "   OK" -ForegroundColor Green
}

# Step 1: initial commit on main
Write-Host "--- Setting up main ---" -ForegroundColor Cyan
Copy-Item "$SRC\README.md" "README.md" -Force
& git add README.md
& git commit -m "docs: root README" --quiet 2>$null
& git branch -M main 2>$null
Write-Host "   main ready" -ForegroundColor Green
Write-Host ""

# Step 2: 10 problem branches
Create-Branch "p1/username-checker"     "$SRC\p1_username_checker"     "UsernameChecker.java"     "Problem 1: Social Media Username Availability Checker"
Create-Branch "p2/flash-sale-inventory" "$SRC\p2_flash_sale"           "FlashSaleInventory.java"  "Problem 2: E-commerce Flash Sale Inventory Manager"
Create-Branch "p3/dns-cache"            "$SRC\p3_dns_cache"            "DNSCache.java"            "Problem 3: DNS Cache with TTL"
Create-Branch "p4/plagiarism-detector"  "$SRC\p4_plagiarism_detector"  "PlagiarismDetector.java"  "Problem 4: Plagiarism Detection System"
Create-Branch "p5/analytics-dashboard" "$SRC\p5_analytics_dashboard"  "AnalyticsDashboard.java"  "Problem 5: Real-Time Analytics Dashboard"
Create-Branch "p6/rate-limiter"         "$SRC\p6_rate_limiter"         "RateLimiter.java"         "Problem 6: Distributed Rate Limiter for API Gateway"
Create-Branch "p7/autocomplete"         "$SRC\p7_autocomplete"         "AutocompleteSystem.java"  "Problem 7: Autocomplete System for Search Engine"
Create-Branch "p8/parking-lot"          "$SRC\p8_parking_lot"          "ParkingLot.java"          "Problem 8: Parking Lot Management with Open Addressing"
Create-Branch "p9/transaction-analyzer" "$SRC\p9_transaction_analyzer" "TransactionAnalyzer.java" "Problem 9: Two-Sum Variants for Financial Transactions"
Create-Branch "p10/multi-level-cache"   "$SRC\p10_multi_level_cache"   "MultiLevelCache.java"     "Problem 10: Multi-Level Cache System"

# Step 3: push all
Write-Host ""
Write-Host "--- Pushing to GitHub ---" -ForegroundColor Cyan
$branches = @("main","p1/username-checker","p2/flash-sale-inventory","p3/dns-cache",
              "p4/plagiarism-detector","p5/analytics-dashboard","p6/rate-limiter",
              "p7/autocomplete","p8/parking-lot","p9/transaction-analyzer","p10/multi-level-cache")

foreach ($branch in $branches) {
    & git checkout $branch --quiet
    & git push -u origin $branch
    Write-Host "  Pushed: $branch" -ForegroundColor Green
}
& git checkout main --quiet
Write-Host ""
Write-Host "=== Done! https://github.com/Ashmit-Singh/Hash-Table ===" -ForegroundColor Cyan