package org.cgutman.shieldcontrollerextensions;

import android.content.Context;
import android.view.InputDevice;

/**
 * RetroForge: stand-in for Moonlight's ShieldControllerExtensions library (NVIDIA SHIELD TV controller battery and
 * rumble), which is only published on JitPack. It recognises no controllers, so the standard Android paths are used.
 */
public class SceManager {
    public SceManager(Context context) {}

    public void start() {}

    public void stop() {}

    public boolean isRecognizedDevice(InputDevice device) { return false; }

    public int getBatteryPercentage(InputDevice device) { return -1; }

    public SceConnectionType getConnectionType(InputDevice device) { return SceConnectionType.UNKNOWN; }

    public SceChargingState getChargingState(InputDevice device) { return SceChargingState.UNKNOWN; }

    public boolean rumble(InputDevice device, int lowFreqMotor, int highFreqMotor) { return false; }
}
