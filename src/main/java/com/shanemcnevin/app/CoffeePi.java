package com.shanemcnevin.app;

import java.util.concurrent.locks.ReentrantLock;
import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;

public class CoffeePi implements GpioPinListenerDigital {

    	final ReentrantLock stateChangeLock = new ReentrantLock();
	final GpioPinDigitalInput startButton;
	final GpioPinDigitalInput readyButton;
	final GpioPinDigitalInput cancelButton;
    	boolean isReady = false;
   	boolean isStarted = false;

	public CoffeePi () throws InterruptedException {
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


		System.out.println("Starting loop...");

		while(true) {
			Thread.sleep(100);
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

    	public static void main( String[] args ) throws InterruptedException {
		new CoffeePi();
    	}
}
