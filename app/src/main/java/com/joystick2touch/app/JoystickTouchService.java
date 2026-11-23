package com.joystick2touch.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Locale;

public class JoystickTouchService extends AccessibilityService {

    private static volatile JoystickTouchService instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile String lastActionText = "Servicio de accesibilidad listo.";

    public static JoystickTouchService getInstance() {
        return instance;
    }

    public static boolean isRunning() {
        return instance != null;
    }

    public String getLastActionText() {
        return lastActionText;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        lastActionText = "Servicio conectado y listo para tocar.";
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Sin uso en esta prueba.
    }

    @Override
    public void onInterrupt() {
        TouchMapper.stopAllHolds();
        lastActionText = "Servicio interrumpido.";
    }

    @Override
    public boolean onUnbind(Intent intent) {
        TouchMapper.stopAllHolds();
        if (instance == this) {
            instance = null;
        }
        lastActionText = "Servicio desactivado.";
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        TouchMapper.stopAllHolds();
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        InputDevice dev = event.getDevice();
        if (dev != null && dev.isExternal()) {
            Integer slot = TouchMapper.keyCodeToSlot(event.getKeyCode());
            if (slot != null) {
                int action = event.getAction();
                if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    TouchMapper.startHold(
                            event.getKeyCode(),
                            slot,
                            KeyEvent.keyCodeToString(event.getKeyCode()));
                    return true;
                } else if (action == KeyEvent.ACTION_UP) {
                    TouchMapper.stopHold(event.getKeyCode());
                    return true;
                }
            }
        }
        return super.onKeyEvent(event);
    }

    public void tapAt(final float x, final float y) {
        tapAt(x, y, 70L, String.format(Locale.US, "(%.1f, %.1f)", x, y));
    }

    public void tapAt(final float x, final float y, final String label) {
        tapAt(x, y, 70L, label);
    }

    public void tapAt(final float x, final float y, final long durationMs, final String label) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            lastActionText = "Requiere Android 7.0+ (API 24) para dispatchGesture.";
            return;
        }

        final long safeDuration = Math.max(20L, durationMs);

        mainHandler.post(() -> {
            Path path = new Path();
            path.moveTo(x, y);

            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0L, safeDuration);

            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(stroke);

            lastActionText = "Enviando " + label;

            boolean dispatched = dispatchGesture(builder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    lastActionText = "OK " + label;
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    lastActionText = "CANCELADO " + label;
                }
            }, mainHandler);

            if (!dispatched) {
                lastActionText = "FALSE dispatchGesture en " + label;
            }
        });
    }

    public void swipe(final float x1, final float y1, final float x2, final float y2, final long durationMs) {
        swipe(x1, y1, x2, y2, durationMs,
                String.format(Locale.US, "SWIPE (%.0f,%.0f) -> (%.0f,%.0f)", x1, y1, x2, y2));
    }

    public void swipe(final float x1, final float y1, final float x2, final float y2,
                      final long durationMs, final String label) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            lastActionText = "Requiere Android 7.0+ (API 24) para dispatchGesture.";
            return;
        }

        final long safeDuration = Math.max(20L, durationMs);

        mainHandler.post(() -> {
            Path path = new Path();
            path.moveTo(x1, y1);
            path.lineTo(x2, y2);

            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0L, safeDuration);

            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(stroke);

            lastActionText = "Enviando " + label;

            boolean dispatched = dispatchGesture(builder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    lastActionText = "OK " + label;
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    lastActionText = "CANCELADO " + label;
                }
            }, mainHandler);

            if (!dispatched) {
                lastActionText = "FALSE dispatchGesture en " + label;
            }
        });
    }
}
