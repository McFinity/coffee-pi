package com.shanemcnevin.app;

import java.util.concurrent.locks.ReentrantLock;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.lang.Math;
import java.io.IOException;
import com.pi4j.io.i2c.*;
import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import io.javalin.Javalin;

public class CoffeePi implements GpioPinListenerDigital {

    	final ReentrantLock stateChangeLock;
	final GpioController gpio;
	final GpioPinDigitalInput startButton;
	final GpioPinDigitalInput readyButton;
	final GpioPinDigitalInput cancelButton;
	final TempModule tempMod;
	final long BREW_DURATION_MILLIS = 15 * 60 * 1000; // 15 min
	final long WARMING_DURATION_MILLIS = 60 * 60 * 1000; // 60 min
	final double MIN_DESIRED_TEMP_C = 70.0;
	final double MAX_DESIRED_TEMP_C = 85.0;
	final double TEMP_ADJ = 19.0;

    	boolean isReady = false;
   	boolean isBrewing = false;
	boolean isWarming = false;
	boolean isPowerOn = false;
	long brewingStartedMillis;
	long warmingStartedMillis;

	public CoffeePi () throws I2CFactory.UnsupportedBusNumberException, IOException {
		stateChangeLock = new ReentrantLock();
		tempMod = new TempModule(0x40);

		// setup gpio
		 gpio = GpioFactory.getInstance();

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
	}

	public void startLoop() throws InterruptedException, IOException {
		System.out.println("Starting loop...");

		// setup oututs
		final GpioPinDigitalOutput readyLed = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01, "ReadyLED", PinState.LOW);
		readyLed.setShutdownOptions(true, PinState.LOW);

		final GpioPinDigitalOutput relay = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_05, "Relay", PinState.LOW);
		relay.setShutdownOptions(true, PinState.LOW);

		while(true) {
			Thread.sleep(1000);
			stateChangeLock.lock();
			try {
				double tempC = readTempC();
				long nowMillis = System.currentTimeMillis();
				if (isBrewing && (nowMillis - brewingStartedMillis) >= BREW_DURATION_MILLIS) {
					isBrewing = false;
					isWarming = true;
					warmingStartedMillis = nowMillis;
				}
				if (isWarming && (nowMillis - warmingStartedMillis) >= WARMING_DURATION_MILLIS) {
					isWarming = false;
					isPowerOn = false;
				}
				if (isWarming && tempC < MIN_DESIRED_TEMP_C) {
					isPowerOn = true;
				}
				if (isWarming && tempC > MAX_DESIRED_TEMP_C) {
					isPowerOn = false;
				}
				readyLed.setState(isReady);
				relay.setState(isPowerOn);
				System.out.println(
					"isReady: " + isReady +
					", isBrewing: " + isBrewing +
					", isWarming: " + isWarming +
					", Temp: " + tempC +
					", Below Temp: " + (tempC < MIN_DESIRED_TEMP_C) +
					", Above Temp: " + (tempC > MAX_DESIRED_TEMP_C) +
					", isPowerOn: " + isPowerOn);
			} finally {
				stateChangeLock.unlock();
			}
		}
	}

	@Override
	public void handleGpioPinDigitalStateChangeEvent (GpioPinDigitalStateChangeEvent event) {
		final GpioPin pin = event.getPin();
		if (pin.equals(startButton)) {
			handleStartButtonEvent(event);
		}
		if (pin.equals(readyButton)) {
			handleReadyButtonEvent(event);
		}
		if (pin.equals(cancelButton)) {
			handleCancelButtonEvent(event);
		}
	}

	public boolean startBrewing() {
		boolean wasStarted = false;
		stateChangeLock.lock();
		try {
			if (!isBrewing && isReady) {
				wasStarted = true;
				isBrewing = true;
				isPowerOn = true;
				isReady = false;
				brewingStartedMillis = System.currentTimeMillis();
			}
		} finally {
			stateChangeLock.unlock();
		}
		return wasStarted;
	}

	public void makeReady() {
		stateChangeLock.lock();
		try {
			isReady = true;
		} finally {
			stateChangeLock.unlock();
		}
	}

	public void cancel() {
		stateChangeLock.lock();
		try {
			isReady = false;
			isBrewing = false;
			isWarming = false;
			isPowerOn = false;
		} finally {
			stateChangeLock.unlock();
		}
	}

	public boolean isReady() {
		return isReady;
	}

	public boolean isBrewing() {
		return isBrewing;
	}

	public boolean isWarming() {
		return isWarming;
	}

	public boolean isPowerOn() {
		return isPowerOn;
	}

	public double readTempC() {
		double temp = 0.0;
		try {
			temp = tempMod.readTempC();
		} catch (IOException ex) {
			System.err.println("Problem reading temp:");
			ex.printStackTrace();
		} finally {
			return temp + TEMP_ADJ;
		}
	}

	private void handleStartButtonEvent (GpioPinDigitalStateChangeEvent event) {
		if (event.getState() == PinState.HIGH) {
			startBrewing();
		}
    	}

	private void handleReadyButtonEvent (GpioPinDigitalStateChangeEvent event) {
		if (event.getState() == PinState.HIGH) {
			makeReady();
		}
	}

	private void handleCancelButtonEvent (GpioPinDigitalStateChangeEvent event) {
		if (event.getState() == PinState.HIGH) {
			cancel();
		}
	}

	private Map<String, Boolean> getCoffee () {
		Map<String, Boolean> data = new HashMap<>();
		data.put("success", startBrewing());
		return data;
	}

	private Map<String, String> getStatus () {
		Map<String, String> data = new HashMap<>();
		if (isReady()) {
			data.put("status", "ready");
		} else {
			data.put("status", "not ready");
		}
		if (isBrewing()) {
			data.put("status", "brewing");
		}
		if (isWarming()) {
			data.put("status", "warming");
		}
		return data;
	}

    	public static void main( String[] args ) throws InterruptedException, I2CFactory.UnsupportedBusNumberException, java.io.IOException {
		final CoffeePi pi = new CoffeePi();

		Javalin app = Javalin.create(config -> {
			config.addStaticFiles("/static");
		}).start(7000);

		app.get("/coffee", ctx -> ctx.json(pi.getCoffee()));
		app.get("/status", ctx -> ctx.json(pi.getStatus()));

		pi.startLoop();
    	}
}
