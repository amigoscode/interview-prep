@echo off
rem Runs on Windows. On macOS and Linux use ./check
call "%~dp0mvnw.cmd" -q -f "%~dp0pom.xml" test-compile exec:java "-Dexec.mainClass=com.amigoscode.locking.Check" "-Dexec.classpathScope=test" "-Dexec.args=%*"
