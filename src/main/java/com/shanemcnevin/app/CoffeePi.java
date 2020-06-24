package com.shanemcnevin.app;

import java.util.concurrent.locks.ReentrantLock;
import java.util.Arrays;
import java.lang.Math;
import com.pi4j.io.i2c.*;
import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;

public class CoffeePi implements GpioPinListenerDigital {

    	final ReentrantLock stateChangeLock = new ReentrantLock();
	final GpioPinDigitalInput startButton;
	final GpioPinDigitalInput readyButton;
	final GpioPinDigitalInput cancelButton;
	final int TMP_ADDR = 0x40;
	final byte TMP_CFG = (byte) 0x02;
	final int TMP_CFG_MODEON = 0x7000;
	final int TMP_CFG_DRDYEN = 0x0100;
	final int TMP_CFG_SAMPLE = 0x0000;
	final int TMP_CFG_INIT = TMP_CFG_MODEON | TMP_CFG_DRDYEN | TMP_CFG_SAMPLE;
	final byte TMP_CFG_INIT_BYTE_1 = (byte)(TMP_CFG_INIT >> 8);
	final byte TMP_CFG_INIT_BYTE_2 = (byte)(TMP_CFG_INIT);
	final byte TMP_REQ_MAN_ID = (byte) 0xFE;
	final int TMP006_MAN_ID = 0x5449;
	final byte TMP_REQ_DEV_ID = (byte) 0xFF;
	final int TMP006_DEV_ID = 0x67;
	final byte TMP_REQ_VOLTS = (byte) 0x0;
	final byte TMP_REQ_DIE_TEMP = (byte) 0x01;
	final float TMP_TREF = 298.15f;
	final float TMP_A1 = 0.00175f;
	final float TMP_A2 = -0.00001678f;
	final float TMP_B0 = -0.0000294f;
	final float TMP_B1 = -0.00000057f;
	final float TMP_B2 = 0.00000000463f;
	final float TMP_C2 = 13.4f;
	final float TMP_S0 = 6.4f;

    	boolean isReady = false;
   	boolean isStarted = false;

	public CoffeePi () throws InterruptedException, I2CFactory.UnsupportedBusNumberException, java.io.IOException {
		// setup gpio
		final GpioController gpio = GpioFactory.getInstance();

		// setup oututs
		final GpioPinDigitalOutput readyLed = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01, "ReadyLED", PinState.LOW);
		readyLed.setShutdownOptions(true, PinState.LOW);

		final GpioPinDigitalOutput relay = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_05, "Relay", PinState.LOW);
		relay.setShutdownOptions(true, PinState.LOW);

		// setup inputs
		startButton = gpio.provisionDigitalInputPin(RaspiPin.GPIO_02, PinPullResistance.PULL_DOWN);
		startButton.setShutdownOptions(true);
		startButton.addListener(this);

		readyButton = gpio.provisionDigitalInputPin(RaspiPin.GPIO_03, PinPullResistance.PULL_DOWN);
		readyButton.setShutdownOptions(true);
		readyButton.addListener(this);

		cancelButton = gpio.provisionDigitalInputPin(RaspiPin.GPIO_04, PinPullResistance.PULL_DOWN);
		cancelButton.setShutdownOptions(true);
		cancelButton.addListener(this);

		TempModule tempMod = new TempModule(0x40);

		System.out.println("Starting loop...");

		while(true) {
			Thread.sleep(1000);
			//System.out.println("V1: " + tempMod.readVolts());
			System.out.println("Die Temp: " + tempMod.readDieTempC());
			System.out.println("Voltage: " + tempMod.readVolts());
			System.out.println("Temp: " + tempMod.readTempC());
			/*
			float voltValue = (float) readI2CData(tempDevice, TMP_REQ_VOLTS);
			System.out.println("V Val " + voltValue);
			voltValue *= 156.25f;
			voltValue /= 1000.0f;
			voltValue /= 1000.0f;
			voltValue /= 1000.0f;
			System.out.println("Voltage Value :" + voltValue);

			float dieTemp = (float) (readI2CData(tempDevice, TMP_REQ_DIE_TEMP) >> 2);
			dieTemp *= 0.03125f;
			System.out.println("Temp Module Die Temp: " + dieTemp);
			dieTemp += 273.15f;

			float dieTempTref = dieTemp - TMP_TREF;
			float S = (1 + TMP_A1 * dieTempTref + TMP_A2 * dieTempTref * dieTempTref);
			S *= TMP_S0;
			S /= 10000000.0f;
			S /= 10000000.0f;

			float Vos = TMP_B0 + TMP_B1 * dieTempTref + TMP_B2 * dieTempTref * dieTempTref;
			float fVobj = (voltValue - Vos) + TMP_C2 * (voltValue - Vos) * (voltValue - Vos);
			float Tobj = (float) Math.sqrt((float) Math.sqrt(dieTemp * dieTemp * dieTemp + fVobj / S));

			float tempInC = Tobj - 273.15f;

			System.out.println("Temp: " + tempInC);
			*/
			stateChangeLock.lock();
			try {
				readyLed.setState(isReady);
				relay.setState(isStarted);
			} finally {
				stateChangeLock.unlock();
			}
		}
	}

	@Override
	public void handleGpioPinDigitalStateChangeEvent (GpioPinDigitalStateChangeEvent event) {
		final GpioPin pin = event.getPin();
		if (pin.equals(this.startButton)) {
			this.handleStartButtonEvent(event);
		}
		if (pin.equals(this.readyButton)) {
			this.handleReadyButtonEvent(event);
		}
		if (pin.equals(this.cancelButton)) {
			this.handleCancelButtonEvent(event);
		}
	}

	private void handleStartButtonEvent (GpioPinDigitalStateChangeEvent event) {
		System.out.println("Start Button State Change: " + event.getState());
		if (event.getState() == PinState.HIGH && this.isReady) {
			this.stateChangeLock.lock();
			try {
				this.isStarted = true;
				this.isReady = false;
			} finally {
				this.stateChangeLock.unlock();
			}
		}
    	}

	private void handleReadyButtonEvent (GpioPinDigitalStateChangeEvent event) {
		System.out.println("Ready Button State Change: " + event.getState());
		if (event.getState() == PinState.HIGH) {
			this.stateChangeLock.lock();
			try {
				this.isReady = true;
			} finally {
				this.stateChangeLock.unlock();
			}
		}
	}

	private void handleCancelButtonEvent (GpioPinDigitalStateChangeEvent event) {
		System.out.println("Cancel Button State Change: " + event.getState());
		if (event.getState() == PinState.HIGH) {
			this.stateChangeLock.lock();
			try {
				this.isReady = false;
				this.isStarted = false;
			} finally {
				this.stateChangeLock.unlock();
			}
		}
	}

    	public static void main( String[] args ) throws InterruptedException, I2CFactory.UnsupportedBusNumberException, java.io.IOException {
		new CoffeePi();
    	}
}
