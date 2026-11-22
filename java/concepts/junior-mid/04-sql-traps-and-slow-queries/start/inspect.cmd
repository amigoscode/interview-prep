@echo off
rem Runs on Windows. On macOS and Linux use ./inspect
rem Put the SELECT in double quotes: .\inspect "SELECT ..."
call "%~dp0mvnw.cmd" -q -f "%~dp0pom.xml" compile exec:java "-Dexec.mainClass=com.amigoscode.sqltraps.demo.Inspect" "-Dinspect.sql=%~1"
