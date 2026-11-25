@echo off
rem Runs on Windows. On macOS and Linux use ./inspect
call "%~dp0mvnw.cmd" -q -f "%~dp0pom.xml" compile exec:java "-Dexec.mainClass=com.amigoscode.pagination.demo.Inspect" "-Dexec.args=%*"
