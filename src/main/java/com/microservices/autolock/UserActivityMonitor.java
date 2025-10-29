package com.microservices.autolock;

import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.win32.StdCallLibrary;

import java.util.Arrays;
import java.util.List;

/**
 * Monitors Windows user activity using JNA to detect idle time.
 */
public class UserActivityMonitor {

    // ✅ Extend User32 to access GetLastInputInfo
    public interface User32Extended extends StdCallLibrary {
        User32Extended INSTANCE = Native.load("user32", User32Extended.class);

        boolean GetLastInputInfo(LASTINPUTINFO plii);
    }

    // ✅ LASTINPUTINFO structure
    public static class LASTINPUTINFO extends Structure {
        public int cbSize;
        public int dwTime;

        public LASTINPUTINFO() {
            this.cbSize = this.size();
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("cbSize", "dwTime");
        }
    }

    /**
     * Get the time in seconds since last user input (keyboard or mouse).
     */
    public static long getIdleTimeSeconds() {
        try {
            LASTINPUTINFO lastInputInfo = new LASTINPUTINFO();
            User32Extended.INSTANCE.GetLastInputInfo(lastInputInfo);

            int currentTickCount = Kernel32.INSTANCE.GetTickCount();
            int lastInputTick = lastInputInfo.dwTime;

            long idleMillis = currentTickCount - lastInputTick;

            // Prevent negative values (rare but possible on system time changes)
            if (idleMillis < 0) {
                return 0;
            }

            return idleMillis / 1000; // Convert to seconds
        } catch (Exception e) {
            System.err.println("❌ Error getting idle time: " + e.getMessage());
            e.printStackTrace();
            return 0; // Assume active on error
        }
    }

    /**
     * Initialize the monitor (if needed).
     */
    public static void start() {
        // Test that JNA is working
        try {
            long idleTime = getIdleTimeSeconds();
            System.out.println("✅ UserActivityMonitor initialized (current idle time: " + idleTime + "s)");
        } catch (Exception e) {
            System.err.println("❌ Failed to initialize UserActivityMonitor: " + e.getMessage());
            throw new RuntimeException("UserActivityMonitor initialization failed", e);
        }
    }

    // ✅ Test method
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Testing UserActivityMonitor...");
        System.out.println("Move your mouse or press a key to reset idle time.");
        System.out.println();

        for (int i = 0; i < 20; i++) {
            long idleTime = getIdleTimeSeconds();
            System.out.println("[" + i + "] Idle time: " + idleTime + " seconds");
            Thread.sleep(1000);
        }
    }

}