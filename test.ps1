$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
New-Item -ItemType Directory -Force 'build\tests' | Out-Null
python tests\validate_chart.py
if ($LASTEXITCODE -ne 0) { throw 'Chart validation failed' }
javac -encoding UTF-8 -d build\tests app\src\main\java\studio\interval\Engine.java tests\EngineTest.java tests\ChartSimulation.java tests\MotionTest.java tests\TrackTest.java
if ($LASTEXITCODE -ne 0) { throw 'Test compile failed' }
java -cp build\tests EngineTest
if ($LASTEXITCODE -ne 0) { throw 'Judgement tests failed' }
java -cp build\tests MotionTest
if ($LASTEXITCODE -ne 0) { throw 'Region motion tests failed' }
java -cp build\tests ChartSimulation build\chart-fixture.txt
if ($LASTEXITCODE -ne 0) { throw 'Full chart simulation failed' }

java -cp build\tests TrackTest
if ($LASTEXITCODE -ne 0) { throw 'Trajectory tests failed' }
