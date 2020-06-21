#!/bin/bash

cd target
sudo java -classpath .:classes:/opt/pi4j/lib/'*' com.shanemcnevin.app.CoffeePi
