$properties = [IO.File]::ReadAllText((Join-Path $PSScriptRoot '../gradle.properties'))
$tag = [regex]::Match($properties, '(?m)^windwovenVersion[ \t]*=[ \t]*([0-9]+\.[0-9]+\.[0-9]+)[ \t]*\r?$')
if (!$tag.Success) { throw 'Missing numeric windwovenVersion in gradle.properties' }
$tag.Groups[1].Value
