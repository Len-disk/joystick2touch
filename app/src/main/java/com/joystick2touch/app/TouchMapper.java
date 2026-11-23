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
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final Map<Integer, Runnable> ACTIVE_HOLDS = new ConcurrentHashMap<>();

    private static final long HOLD_PULSE_DURATION_MS = 160L;
    private static final long HOLD_REPEAT_INTERVAL_MS = 130L;

    // Centro y radio del stick en HUD_2.jpg
    private static final float STICK_CENTER_X = 270f;
    private static final float STICK_CENTER_Y = 539f;
    private static final float STICK_MAX_RADIUS = 130f;
    private static final float STICK_DEADZONE = 0.20f;
    private static final float STICK_MIN_STEP_PX = 4f;
    private static final long STICK_STEP_DURATION_MS = 90L;
    private static final long STICK_STEP_INTERVAL_MS = 100L;
    private static final long STICK_IDLE_TIMEOUT_MS = 300L;


    

    private static volatile float currentStickX = 0f;
    private static volatile float currentStickY = 0f;
    private static volatile long lastStickUpdateMs = 0L;
    private static volatile boolean stickActive = false;

    private static float activeTouchX = STICK_CENTER_X;
    private static float activeTouchY = STICK_CENTER_Y;

    private static final Runnable STICK_STEP_RUNNABLE = new Runnable() {
        @Override
        public void run() {
            JoystickTouchService svc = JoystickTouchService.getInstance();
            long now = System.currentTimeMillis();

            if (svc == null || !stickActive || (now - lastStickUpdateMs) > STICK_IDLE_TIMEOUT_MS) {
                resetStickTouch();
                return;
            }

            float ax = currentStickX;
            float ay = currentStickY;
            float mag = (float) Math.hypot(ax, ay);

            if (mag < STICK_DEADZONE) {
                resetStickTouch();
                return;
            }

            float normX = mag > 1f ? ax / mag : ax;
            float normY = mag > 1f ? ay / mag : ay;
            float clampedMag = Math.min(1f, mag);

            float desiredX = STICK_CENTER_X + (normX * STICK_MAX_RADIUS * clampedMag);
            float desiredY = STICK_CENTER_Y + (normY * STICK_MAX_RADIUS * clampedMag);

            float dx = desiredX - activeTouchX;
            float dy = desiredY - activeTouchY;
            float dist = (float) Math.hypot(dx, dy);

            float nextX = desiredX;
            float nextY = desiredY;

            svc.swipe(
                    activeTouchX,
                    activeTouchY,
                    nextX,
                    nextY,
                    STICK_STEP_DURATION_MS,
                    String.format(Locale.US, "STICK (%.0f, %.0f) -> (%.0f, %.0f)",
                            activeTouchX, activeTouchY, nextX, nextY)
            );

            activeTouchX = nextX;
            activeTouchY = nextY;

            HANDLER.postDelayed(this, STICK_STEP_INTERVAL_MS);
        }
    };

    static {
        BUTTON_COORDS.put(1, new float[]{112f, 212f});
        BUTTON_COORDS.put(2, new float[]{1345f, 489f});
        BUTTON_COORDS.put(3, new float[]{1253f, 593f});
        BUTTON_COORDS.put(4, new float[]{1188f, 489f});
        BUTTON_COORDS.put(5, new float[]{1387f, 420f});
        BUTTON_COORDS.put(6, new float[]{1387f, 227f});
        BUTTON_COORDS.put(7, new float[]{599f, 668f});
        BUTTON_COORDS.put(8, new float[]{928f, 668f});
        BUTTON_COORDS.put(9, new float[]{112f, 119f});
        BUTTON_COORDS.put(10, new float[]{112f, 23f});
    }

    private TouchMapper() {
    }

    private static void resetStickTouch() {
        stickActive = false;
        currentStickX = 0f;
        currentStickY = 0f;
        HANDLER.removeCallbacks(STICK_STEP_RUNNABLE);
        activeTouchX = STICK_CENTER_X;
        activeTouchY = STICK_CENTER_Y;
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
        if (keyCode == KeyEvent.KEYCODE_BUTTON_9 || keyCode == KeyEvent.KEYCODE_BUTTON_SELECT) {
            return 9;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_10 || keyCode == KeyEvent.KEYCODE_BUTTON_START) {
            return 10;
        }
        return null;
    }

    public static void updateStick(float xAxis, float yAxis) {
        currentStickX = xAxis;
        currentStickY = yAxis;
        lastStickUpdateMs = System.currentTimeMillis();

        float mag = (float) Math.hypot(xAxis, yAxis);
        if (mag >= STICK_DEADZONE) {
            if (!stickActive) {
                stickActive = true;
                activeTouchX = STICK_CENTER_X;
                activeTouchY = STICK_CENTER_Y;
                HANDLER.removeCallbacks(STICK_STEP_RUNNABLE);
                HANDLER.post(STICK_STEP_RUNNABLE);
            }
        } else {
            resetStickTouch();
        }
    }

    public static boolean startHold(final int keyCode, final int slotNumber, final String sourceLabel) {
        final float[] coords = BUTTON_COORDS.get(slotNumber);
        if (coords == null) {
            return false;
        }

        stopHold(keyCode);

        if (JoystickTouchService.getInstance() == null) {
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
                HANDLER.postDelayed(this, HOLD_REPEAT_INTERVAL_MS);
            }
        };

        ACTIVE_HOLDS.put(keyCode, pulseRunnable);
        HANDLER.post(pulseRunnable);
        return true;
    }

    public static void stopHold(int keyCode) {
        Runnable existing = ACTIVE_HOLDS.remove(keyCode);
        if (existing != null) {
            HANDLER.removeCallbacks(existing);
        }
    }

    public static void stopAllHolds() {
        for (Runnable r : ACTIVE_HOLDS.values()) {
            HANDLER.removeCallbacks(r);
        }
        ACTIVE_HOLDS.clear();
        resetStickTouch();
    }

    public static float[] getButtonCoords(int slotNumber) {
        return BUTTON_COORDS.get(slotNumber);
    }

    public static boolean triggerSingleTap(int slotNumber, String sourceLabel) {
        float[] coords = BUTTON_COORDS.get(slotNumber);
        if (coords == null) {
            return false;
        }
        JoystickTouchService svc = JoystickTouchService.getInstance();
        if (svc == null) {
            return false;
        }
        svc.tapAt(coords[0], coords[1], 70L,
                String.format(Locale.US, "btn %d (%.0f, %.0f) [%s]",
                        slotNumber, coords[0], coords[1], sourceLabel));
        return true;
    }
}
