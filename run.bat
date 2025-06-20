@echo off
echo Starting Among Us Telegram Bot...

rem Check if JAR exists
if not exist target\among-us-telegram-bot-1.0-SNAPSHOT-jar-with-dependencies.jar (
    echo Building project...
    call mvn clean package
)

rem Run the bot with JDK 17
"%JAVA_HOME%\bin\java" -jar target\among-us-telegram-bot-1.0-SNAPSHOT-jar-with-dependencies.jar

echo Bot stopped.
pause 