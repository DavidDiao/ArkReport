javac burp/BurpExtender.java
if [ $? == 0 ]
then
    jar cvf arkreport.jar burp/*.class com/eclipsesource/json/*.class
fi
