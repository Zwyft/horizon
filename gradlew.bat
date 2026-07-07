@echo off
set APP_HOME=%~dp0
if defined JAVA_HOME (
  set JAVA_BIN=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_BIN=java.exe
)
"%JAVA_BIN%" -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar;%APP_HOME%gradle\wrapper\gradle-wrapper-shared.jar" org.gradle.wrapper.GradleWrapperMain %*

