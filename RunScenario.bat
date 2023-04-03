@echo off

set JAR_FILE_NAME=University_Of_Greenwich-COMP1549-Advanced_Programming-Coursework.jar

@REM Ensure that the working directory is where this file is placed https://stackoverflow.com/questions/17063947/get-current-batchfile-directory
cd %~dp0

set ARGS=127.0.0.1 8080 Anonymous

set useUnpackedMethod=0
if %useUnpackedMethod% equ 1 (
    goto unpacked_method
) else (
    goto packed_method
)

::#region Unpacked method
:unpacked_method
@REM The program will be unpacked and runnable from the bin directory, our entrypoint file is "App"
@REM We can use the -cp flag to specify the path that contains the class file we want to run.
set COMMAND=java.exe -cp .\bin\ App
goto run
::#endregion

::#region Packed method
:packed_method
@REM The program will be packed into a jar file.
@REM We can use the -jar flag to specify the path to the jar file we want to run.
set COMMAND=java.exe -jar %JAR_FILE_NAME%
goto run
::#endregion

:run
@REM Start 3 instance of the app, 1 will start up as a server, 2 will start up as clients.
set INSTANCES=3
for /l %%i in (1,1,%INSTANCES%) do (
    @REM echo %COMMAND% %ARGS%
    start "" %COMMAND% %ARGS%
)
