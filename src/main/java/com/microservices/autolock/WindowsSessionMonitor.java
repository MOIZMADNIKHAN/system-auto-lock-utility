package com.microservices.autolock;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.Wtsapi32;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Monitors Windows session state (locked/unlocked).
 */
public class WindowsSessionMonitor {

    private static final int WM_WTSSESSION_CHANGE = 0x02B1;
    private static final int WTS_SESSION_LOCK = 0x7;
    private static final int WTS_SESSION_UNLOCK = 0x8;

    private final AtomicBoolean isSystemLocked = new AtomicBoolean(false);
    private volatile SessionChangeListener listener;
    private HWND hwnd;
    private Thread messageLoopThread;

    public interface SessionChangeListener {
        void onSessionLocked();
        void onSessionUnlocked();
    }

    public void start(SessionChangeListener listener) {
        this.listener = listener;

        messageLoopThread = new Thread(() -> {
            try {
                // Create a hidden window to receive messages
                HWND hwnd = createMessageWindow();
                if (hwnd == null) {
                    System.err.println("❌ Failed to create message window");
                    return;
                }

                this.hwnd = hwnd;

                // Register for session notifications
                if (!Wtsapi32.INSTANCE.WTSRegisterSessionNotification(hwnd, Wtsapi32.NOTIFY_FOR_THIS_SESSION)) {
                    System.err.println("❌ Failed to register for session notifications");
                    return;
                }

                System.out.println("✅ Session monitor started");

                // Message loop
                WinUser.MSG msg = new WinUser.MSG();
                while (User32.INSTANCE.GetMessage(msg, hwnd, 0, 0) != 0) {
                    User32.INSTANCE.TranslateMessage(msg);
                    User32.INSTANCE.DispatchMessage(msg);
                }

            } catch (Exception e) {
                System.err.println("❌ Session monitor error: " + e.getMessage());
                e.printStackTrace();
            }
        }, "SessionMonitor");

        messageLoopThread.setDaemon(true);
        messageLoopThread.start();
    }

    private HWND createMessageWindow() {
        WinUser.WNDCLASSEX wndClass = new WinUser.WNDCLASSEX();
        wndClass.lpszClassName = "FaceWatchSessionMonitor";
        wndClass.lpfnWndProc = new WinUser.WindowProc() {
            @Override
            public LRESULT callback(HWND hwnd, int uMsg, WPARAM wParam, LPARAM lParam) {
                if (uMsg == WM_WTSSESSION_CHANGE) {
                    handleSessionChange(wParam.intValue());
                }
                return User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
            }
        };

        if (User32.INSTANCE.RegisterClassEx(wndClass).intValue() == 0) {
            int error = Kernel32.INSTANCE.GetLastError();
            if (error != 1410) { // Ignore "class already exists" error
                System.err.println("❌ RegisterClassEx failed: " + error);
                return null;
            }
        }

        return User32.INSTANCE.CreateWindowEx(
                0,
                wndClass.lpszClassName,
                "FaceWatch Session Monitor",
                0,
                0, 0, 0, 0,
                null,
                null,
                null,
                null
        );
    }

    private void handleSessionChange(int event) {
        switch (event) {
            case WTS_SESSION_LOCK:
                isSystemLocked.set(true);
                if (listener != null) {
                    listener.onSessionLocked();
                }
                break;

            case WTS_SESSION_UNLOCK:
                isSystemLocked.set(false);
                if (listener != null) {
                    listener.onSessionUnlocked();
                }
                break;
        }
    }

    public boolean isSystemLocked() {
        return isSystemLocked.get();
    }

    public void stop() {
        if (hwnd != null) {
            try {
                Wtsapi32.INSTANCE.WTSUnRegisterSessionNotification(hwnd);
                User32.INSTANCE.PostMessage(hwnd, WinUser.WM_QUIT, null, null);
            } catch (Exception e) {
                System.err.println("⚠️ Error stopping session monitor: " + e.getMessage());
            }
        }

        if (messageLoopThread != null) {
            messageLoopThread.interrupt();
        }
    }
}