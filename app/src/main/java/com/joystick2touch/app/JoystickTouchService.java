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

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        InputDevice dev = event.getDevice();
        if (dev != null && dev.isExternal()) {
            Integer slot = TouchMapper.keyCodeToSlot(event.getKeyCode());
            if (slot != null) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    TouchMapper.triggerTap(slot, KeyEvent.keyCodeToString(event.getKeyCode()));
                }
                // Consumir el evento del botón mapeado para no interferir con la app destino
                return true;
            }
        }
        return super.onKeyEvent(event);
    }

    public void tapAt(final float x, final float y) {
        tapAt(x, y, String.format(Locale.US, "(%.1f, %.1f)", x, y));
    }

    public void tapAt(final float x, final float y, final String label) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            lastActionText = "Requiere Android 7.0+ (API 24) para dispatchGesture.";
            return;
        }

        mainHandler.post(() -> {
            Path path = new Path();
            path.moveTo(x, y);

            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0L, 70L);

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
