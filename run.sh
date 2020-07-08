#!/bin/bash

cd /home/pi/d/java-sandbox/coffee-pi/target
sudo java -classpath .:classes:/opt/pi4j/lib/'*':dependency/'*' com.shanemcnevin.app.CoffeePi
