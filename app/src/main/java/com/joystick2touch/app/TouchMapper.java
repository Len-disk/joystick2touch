package com.joystick2touch.app;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TouchMapper {

    private static final Map<Integer, float[]> BUTTON_COORDS = new HashMap<>();
    private static final Handler HOLD_HANDLER = new Handler(Looper.getMainLooper());
    private static final Map<Integer, Runnable> ACTIVE_HOLDS = new ConcurrentHashMap<>();

    private static final long HOLD_PULSE_DURATION_MS = 160L;
    private static final long HOLD_REPEAT_INTERVAL_MS = 130L;

    static {
        // Coordenadas actualizadas de HUD_android-10.jpg
        BUTTON_COORDS.put(1, new float[]{112f, 212f});   //[cite: 3]
        BUTTON_COORDS.put(2, new float[]{1345f, 489f}); //[cite: 3]
        BUTTON_COORDS.put(3, new float[]{1253f, 593f}); //[cite: 3]
        BUTTON_COORDS.put(4, new float[]{112f, 119f});   //[cite: 3]
        BUTTON_COORDS.put(5, new float[]{1387f, 420f}); //[cite: 3]
        BUTTON_COORDS.put(6, new float[]{1387f, 227f}); //[cite: 3]
        BUTTON_COORDS.put(7, new float[]{599f, 668f});  //[cite: 3]
        BUTTON_COORDS.put(8, new float[]{928f, 668f});  //[cite: 3]
        BUTTON_COORDS.put(10, new float[]{112f, 23f});   //[cite: 3]
    }

    private TouchMapper() {
    }

    public static float[] getStickCenter() {
        return new float[]{270f, 539f}; //[cite: 3]
    }

    public static Integer keyCodeToSlot(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_1 || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
            return 1;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_2 || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            return 2;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_3 || keyCode == KeyEvent.KEYCODE_BUTTON_X) {
            return 3;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_4 || keyCode == KeyEvent.KEYCODE_BUTTON_Y) {
            return 4;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_5 || keyCode == KeyEvent.KEYCODE_BUTTON_L1) {
            return 5;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_6 || keyCode == KeyEvent.KEYCODE_BUTTON_R1) {
            return 6;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_7 || keyCode == KeyEvent.KEYCODE_BUTTON_L2) {
            return 7;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_8 || keyCode == KeyEvent.KEYCODE_BUTTON_R2) {
            return 8;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_10 || keyCode == KeyEvent.KEYCODE_BUTTON_START) {
            return 10;
        }
        return null;
    }

    public static boolean startHold(final int keyCode, final int slotNumber, final String sourceLabel) {
        final float[] coords = BUTTON_COORDS.get(slotNumber);
        if (coords == null) {
            return false;
        }

        stopHold(keyCode);

        final JoystickTouchService svc = JoystickTouchService.getInstance();
        if (svc == null) {
            return false;
        }

        final String label = String.format(Locale.US, "btn %d (%.0f, %.0f) [%s]",
                slotNumber, coords[0], coords[1], sourceLabel);

        Runnable pulseRunnable = new Runnable() {
            @Override
            public void run() {
                JoystickTouchService currentSvc = JoystickTouchService.getInstance();
                if (currentSvc == null || !ACTIVE_HOLDS.containsKey(keyCode)) {
                    ACTIVE_HOLDS.remove(keyCode);
                    return;
                }
                currentSvc.tapAt(coords[0], coords[1], HOLD_PULSE_DURATION_MS, label);
                HOLD_HANDLER.postDelayed(this, HOLD_REPEAT_INTERVAL_MS);
            }
        };

        ACTIVE_HOLDS.put(keyCode, pulseRunnable);
        HOLD_HANDLER.post(pulseRunnable);
        return true;
    }

    public static void stopHold(int keyCode) {
        Runnable existing = ACTIVE_HOLDS.remove(keyCode);
        if (existing != null) {
            HOLD_HANDLER.removeCallbacks(existing);
        }
    }

    public static void stopAllHolds() {
        for (Runnable r : ACTIVE_HOLDS.values()) {
            HOLD_HANDLER.removeCallbacks(r);
        }
        ACTIVE_HOLDS.clear();
    }
}
