@echo off
javac -cp api; burp/BurpExtender.java
if not ERRORLEVEL 1 (
    jar cvf arkreport.jar burp/*.class com/eclipsesource/json/*.class
)
