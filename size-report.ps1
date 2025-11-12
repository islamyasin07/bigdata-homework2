$total = (Get-ChildItem -Path 'C:\TweetAnalyzer' -Recurse -File -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
$tgt = (Get-ChildItem -Path 'C:\TweetAnalyzer\scala-seed-project\target' -Recurse -File -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
Write-Output "TOTAL_MB:$([math]::Round($total/1MB,2))"
Write-Output "TARGET_MB:$([math]::Round($tgt/1MB,2))"
Get-ChildItem -Path 'C:\TweetAnalyzer\scala-seed-project\target' -Recurse -File -ErrorAction SilentlyContinue |
  Sort-Object Length -Descending |
  Select-Object -First 20 @{Name='MB';Expression={[math]::Round($_.Length/1MB,2)}}, FullName |
  ConvertTo-Json -Compress
