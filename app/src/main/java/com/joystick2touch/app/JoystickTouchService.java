package com.joystick2touch.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

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
        // No requerimos eventos de ventana para disparar gestos.
    }

    @Override
    public void onInterrupt() {
        lastActionText = "Servicio interrumpido.";
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (instance == this) {
            instance = null;
        }
        lastActionText = "Servicio desactivado.";
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    public void tapAt(final float x, final float y) {
        mainHandler.post(() -> {
            Path path = new Path();
            path.moveTo(x, y);

            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0L, 40L);

            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(stroke);

            lastActionText = String.format("TAP en (%.1f, %.1f)", x, y);

            dispatchGesture(builder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    lastActionText = String.format("OK TAP (%.1f, %.1f)", x, y);
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    lastActionText = String.format("CANCELADO TAP (%.1f, %.1f)", x, y);
                }
            }, null);
        });
    }

    public void swipe(final float x1, final float y1, final float x2, final float y2, final long durationMs) {
        mainHandler.post(() -> {
            Path path = new Path();
            path.moveTo(x1, y1);
            path.lineTo(x2, y2);

            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0L, Math.max(20L, durationMs));

            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(stroke);

            lastActionText = String.format("SWIPE (%.0f,%.0f) -> (%.0f,%.0f)", x1, y1, x2, y2);
            dispatchGesture(builder.build(), null, null);
        });
    }
}