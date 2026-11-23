package com.joystick2touch.app;

import android.view.KeyEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class TouchMapper {

    private static final Map<Integer, float[]> BUTTON_COORDS = new HashMap<>();
    private static final Map<String, Long> LAST_TAP_MS = new HashMap<>();
    private static final long TAP_COOLDOWN_MS = 120L;

    static {
        BUTTON_COORDS.put(2, new float[]{838f, 356f});
        BUTTON_COORDS.put(3, new float[]{772f, 423f});
        BUTTON_COORDS.put(5, new float[]{880f, 310f});
        BUTTON_COORDS.put(6, new float[]{880f, 167f});
        BUTTON_COORDS.put(7, new float[]{345f, 497f});
        BUTTON_COORDS.put(8, new float[]{565f, 497f});
    }

    private TouchMapper() {
    }

    public static Integer keyCodeToSlot(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_2 || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            return 2;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_3 || keyCode == KeyEvent.KEYCODE_BUTTON_X) {
            return 3;
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
        return null;
    }

    public static boolean triggerTap(int slotNumber, String sourceLabel) {
        float[] coords = BUTTON_COORDS.get(slotNumber);
        if (coords == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        String key = sourceLabel + "#" + slotNumber;
        Long prev = LAST_TAP_MS.get(key);
        if (prev != null && (now - prev) < TAP_COOLDOWN_MS) {
            return false;
        }
        LAST_TAP_MS.put(key, now);

        JoystickTouchService svc = JoystickTouchService.getInstance();
        if (svc == null) {
            return false;
        }

        svc.tapAt(coords[0], coords[1],
                String.format(Locale.US, "btn %d (%.0f, %.0f) [%s]",
                        slotNumber, coords[0], coords[1], sourceLabel));
        return true;
    }
}
