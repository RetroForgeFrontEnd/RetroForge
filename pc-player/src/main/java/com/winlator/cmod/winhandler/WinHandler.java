package com.winlator.cmod.winhandler;

/**
 * RetroForge: stand-in for Winlator's WinHandler, which talks to a helper program inside Wine
 * (winhandler.exe) for relative mouse input and window focus. RetroForge's PC player doesn't run
 * that helper yet, so these calls do nothing; absolute (X11) mouse input still works.
 */
public class WinHandler {
    public void mouseEvent(int flags, int dx, int dy, int wheelDelta) {}

    public void bringToFront(String processName) {}

    public void bringToFront(String processName, long handle) {}
}
