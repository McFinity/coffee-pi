package com.shanemcnevin.app;

import java.util.Arrays;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.nio.ByteOrder;
import com.pi4j.io.i2c.*;
import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;


public class TempModule {
	final int BYTE_MASK = (int) 255;
        final int TMP006_MAN_ID = 0x5449;
        final int TMP006_DEV_ID = 0x67;
        final byte TMP_CFG = (byte) 0x02;
        final int TMP_CFG_MODEON = 0x7000;
        final int TMP_CFG_DRDYEN = 0x0100;
        final int TMP_CFG_SAMPLE = 0x0000;
        final int TMP_CFG_INIT = TMP_CFG_MODEON | TMP_CFG_DRDYEN | TMP_CFG_SAMPLE;
        final byte TMP_CFG_INIT_BYTE_1 = (byte)(TMP_CFG_INIT >> 8);
        final byte TMP_CFG_INIT_BYTE_2 = (byte)(TMP_CFG_INIT);
        final byte TMP_REG_MAN_ID = (byte) 0xFE;
        final byte TMP_REG_DEV_ID = (byte) 0xFF;
        final byte TMP_REG_VOLTS = (byte) 0x0;
        final byte TMP_REG_DIE_TEMP = (byte) 0x01;
        final double TMP_TREF = 298.15d;
        final double TMP_A1 = 0.00175d;
        final double TMP_A2 = -0.00001678d;
        final double TMP_B0 = -0.0000294d;
        final double TMP_B1 = -0.00000057d;
        final double TMP_B2 = 0.00000000463d;
        final double TMP_C2 = 13.4d;
        final double TMP_S0 = 6.4d;
	final I2CDevice device;

	public TempModule (int i2cAddress) throws I2CFactory.UnsupportedBusNumberException, IOException {

		final I2CBus bus = I2CFactory.getInstance(I2CBus.BUS_1);
                device = bus.getDevice(i2cAddress);

                this.writeI2CInt(TMP_CFG, TMP_CFG_INIT);

                int manufacturerId = this.readI2CInt(TMP_REG_MAN_ID);
                System.out.println("Temp Module Manufacturer ID: " + manufacturerId);

                int deviceId = this.readI2CInt(TMP_REG_DEV_ID);
                System.out.println("Temp Module Device ID: " + deviceId);

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

	}

	void writeI2CInt (byte address, int data) throws IOException {
		byte[] bytes = new byte[2];
		bytes[0] = (byte)(data >> 8);
		bytes[1] = (byte) data;
		this.device.write(address, bytes);
	}

	int readI2CInt (byte address) throws IOException {
		byte[] data = new byte[2];
                this.device.read(address, data, 0, 2);
		System.out.println(address + " bytes: " + Arrays.toString(data));
                return (int) ((data[0] << 8) | data[1]);
	}

	double readI2CDouble (byte address) throws IOException {
                return (double) readI2CInt(address);
	}

	double readVolts () throws IOException {
		byte[] data = new byte[2];
		this.device.read(TMP_REG_VOLTS, data, 0, 2);
		System.out.println("Bytes: " + Arrays.toString(data));
		int msb = (int) data[0];
		System.out.println("MSB raw: " + Integer.toBinaryString(msb));
		msb <<= 8;
		System.out.println("MSB: " + Integer.toBinaryString(msb));
		int lsb = (int) data[1];
		System.out.println("LSB raw: " + Integer.toBinaryString(lsb));
		lsb &= BYTE_MASK;
		System.out.println("LSB: " + Integer.toBinaryString(lsb));
		int combined = (msb | lsb);
		System.out.println("Combined: " + combined + " bin: " + Integer.toBinaryString(combined));
		return combined * 156.25 / 1000.0 / 1000.0 / 1000.0;
	}

	double readDieTempC () throws IOException {
		byte[] data = new byte[2];
		this.device.read(TMP_REG_DIE_TEMP, data, 0, 2);
		byte msb = data[0];
		int lsb = (int) data[1];
		lsb &= BYTE_MASK; // remove sign bits
		int shiftedMsb = msb << 8; // make room for right byte
		int combined = shiftedMsb | lsb;
		int shiftedCombined = combined >> 2;
		return shiftedCombined * 0.03125;
	}

	double readTempC () throws IOException {
		double volts = readVolts();
		double dieTemp = readDieTempC();
                dieTemp += 273.15;

                double dieTempTref = dieTemp - TMP_TREF;
                double S = (1 + TMP_A1 * dieTempTref + TMP_A2 * dieTempTref * dieTempTref);
                S *= TMP_S0;
                S /= 10000000.0;
                S /= 10000000.0;

                double Vos = TMP_B0 + TMP_B1 * dieTempTref + TMP_B2 * dieTempTref * dieTempTref;
                double fVobj = (volts - Vos) + TMP_C2 * (volts - Vos) * (volts - Vos);
                double Tobj = Math.sqrt(Math.sqrt(dieTemp * dieTemp * dieTemp * dieTemp + fVobj / S));

                double tempInC = Tobj - 273.15;
		return tempInC;
	}
}
