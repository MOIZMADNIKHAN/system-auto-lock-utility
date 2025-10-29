package com.microservices.autolock;

import com.sun.jna.Library;
import com.sun.jna.Native;

public interface User32 extends Library {
    User32 INSTANCE = Native.load("user32", User32.class);

    boolean LockWorkStation();
}
