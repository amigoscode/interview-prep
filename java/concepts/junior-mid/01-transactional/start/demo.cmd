@echo off
rem Runs on Windows. On macOS and Linux use ./demo
call "%~dp0mvnw.cmd" -q -f "%~dp0pom.xml" compile exec:java "-Dexec.mainClass=com.amigoscode.transactional.demo.Demo" "-Dexec.args=%*"
