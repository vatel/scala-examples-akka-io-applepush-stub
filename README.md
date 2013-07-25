Apple Push Stub - example of Akka I/O with SSL

Currently only server-side stub.

1) Build & run this stub server:
gradle run

2) [required at client-side if using javapns lib] Add this line to /etc/hosts:
127.0.0.1	gateway.push.apple.com

3) [required at client-side if using noops-apns lib] Put 'cacerts' file somewhere and run push sender with this option:
-Djavax.net.ssl.trustStore=/pathTo.../cacerts
//////////////////////

Instructions for manual cert & cacerts creation:
- Generate self-signed cert for this stub server:
keytool -genkey -keyalg RSA -alias applePushStub -keystore stresstest.jks -storepass 1234567 -validity 365 -keysize 1024
enter "your name" (aka CN): gateway.push.apple.com

- Import it to trusted authoorities file (make a separate copy of JDK's cacerts file):
keytool -keystore ./stresstest.jks -alias applePushStub -export -file applePushStub.cert
keytool -keystore ./cacerts -alias applePushStub -import -file applePushStub.cert\

//////////////////////
For SSL debug you may use: -Djavax.net.debug=ssl
at client or server side
//////////////////////

To run in eclipse or idea, you can first generate project file like this:
gradle eclipse
-or-
gradle idea