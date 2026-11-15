@echo off
set JAVA_HOME=D:\MuRong\cwsgi12313\MRPlus\_tmp\ysm\env\zulu8.98.0.111-ca-jdk8.0.512-win_x64
set GRADLE_USER_HOME=D:\MuRong\cwsgi12313\MRPlus\_tmp\ysm\gradle-home
set TMP=D:\MuRong\cwsgi12313\MRPlus\_tmp\ysm\tmp
set TEMP=D:\MuRong\cwsgi12313\MRPlus\_tmp\ysm\tmp
if not exist "%TMP%" mkdir "%TMP%"
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d D:\MuRong\cwsgi12313\MRPlus\_tmp\ysm\port
call "D:\MuRong\cwsgi12313\MRPlus\_tmp\ysm\env\gradle-4.10.3\bin\gradle.bat" %* --no-daemon --console=plain -I "D:\MuRong\cwsgi12313\MRPlus\_tmp\ysm\init-mirror.gradle" -Djava.io.tmpdir=%TMP% -Dorg.gradle.internal.http.connectionTimeout=30000 -Dorg.gradle.internal.http.socketTimeout=120000
echo EXITCODE=%ERRORLEVEL%
