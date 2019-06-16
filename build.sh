javac burp/BurpExtender.java
if [ $? == 0 ]
then
    jar cvf arkreport.jar arkreport/*.class burp/*.class
fi
