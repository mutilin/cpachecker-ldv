@echo off


:: build the Classpath: bin directory, every .jar in lib and every .jar in lib\java\runtime
set CLASSPATH="bin;cpachecker.jar:lib\*:lib\java\runtime\*"

set OLDPATH=%PATH%
set PATH=%PATH%;lib\native\x86-win32
java -Djava.library.path=lib\native\x86-win32 -Xmx1200m -ea org.sosy_lab.cpachecker.cmdline.CPAMain %*
set PATH=%OLDPATH%
