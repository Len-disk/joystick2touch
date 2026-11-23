package com.joystick2touch.app;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.input.InputManager;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private static final String ACTION_USB_PERMISSION =
            "com.joystick2touch.app.USB_PERMISSION";

    // Xbox 360 Wired Controller (VID 045E, PID 028E)
    private static final int XBOX360_VID = 0x045E;
    private static final int XBOX360_PID = 0x028E;

    // Coordenadas tomadas de tu croquis (px directos para prueba inicial)
    private static final float[] STICK_CENTER = {58f, 321f};
    private static final Map<Integer, float[]> BUTTON_COORDS = new HashMap<>();

    static {
        // Mapeo por número de botón (ej. "button 2", "button 3", etc.)
        BUTTON_COORDS.put(2, new float[]{838f, 356f});
        BUTTON_COORDS.put(3, new float[]{772f, 423f});
        BUTTON_COORDS.put(5, new float[]{880f, 310f});
        BUTTON_COORDS.put(6, new float[]{880f, 167f});
        BUTTON_COORDS.put(7, new float[]{345f, 497f});
        BUTTON_COORDS.put(8, new float[]{565f, 497f});
    }

    private InputManager inputManager;
    private UsbManager usbManager;

    private TextView serviceStatusInfo;
    private TextView deviceInfo;
    private TextView usbInfo;
    private TextView buttonInfo;
    private TextView xboxRawInfo;
    private TextView eventInfo;

    private final Map<Integer, String> pressedButtons = new LinkedHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Estado USB Xbox 360
    private volatile boolean readingXbox = false;
    private Thread xboxReadThread;
    private UsbDeviceConnection xboxConnection;
    private UsbInterface xboxInterface;
    private int lastXboxButtonsMask = 0;

    // Anti-rebote simple para gestos al mantener pulsado
    private final Map<String, Long> lastTapTimeMs = new HashMap<>();
    private static final long TAP_COOLDOWN_MS = 140L;

    private final InputManager.InputDeviceListener deviceListener =
            new InputManager.InputDeviceListener() {
                @Override
                public void onInputDeviceAdded(int deviceId) {
                    refreshAll();
                    log("InputDevice conectado: " + deviceId);
                }

                @Override
                public void onInputDeviceRemoved(int deviceId) {
                    refreshAll();
                    log("InputDevice desconectado: " + deviceId);
                }

                @Override
                public void onInputDeviceChanged(int deviceId) {
                    refreshAll();
                }
            };

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                boolean granted = intent.getBooleanExtra(
                        UsbManager.EXTRA_PERMISSION_GRANTED, false);

                if (granted && device != null) {
                    log("Permiso USB concedido para " + device.getDeviceName());
                    startXbox360Reader(device);
                } else {
                    log("Permiso USB denegado para Xbox 360.");
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null
                        && device.getVendorId() == XBOX360_VID
                        && device.getProductId() == XBOX360_PID) {
                    stopXbox360Reader();
                    log("Xbox 360 USB desconectado.");
                }
                refreshAll();
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                refreshAll();
                tryConnectXbox360();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        inputManager = (InputManager) getSystemService(INPUT_SERVICE);
        usbManager = (UsbManager) getSystemService(USB_SERVICE);

        createInterface();

        if (inputManager != null) {
            inputManager.registerInputDeviceListener(deviceListener, handler);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        registerReceiver(usbReceiver, filter);

        refreshAll();
        tryConnectXbox360();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceStatus();
        tryConnectXbox360();
    }

    private void createInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 20, 20, 20);

        TextView title = new TextView(this);
        title.setText("USB JOYSTICK VIRTUAL TOUCH");
        title.setTextSize(22);
        title.setPadding(0, 0, 0, 12);
        root.addView(title);

        Button openSettingsBtn = new Button(this);
        openSettingsBtn.setText("Abrir Ajustes de Accesibilidad");
        openSettingsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
        root.addView(openSettingsBtn);

        Button testTapBtn = new Button(this);
        testTapBtn.setText("Probar Touch en Button 3 (772, 423)");
        testTapBtn.setOnClickListener(v -> triggerVirtualTapForSlot(3, "TEST_BTN"));
        root.addView(testTapBtn);

        Button refreshBtn = new Button(this);
        refreshBtn.setText("Actualizar y conectar Xbox 360 USB");
        refreshBtn.setOnClickListener(v -> {
            updateServiceStatus();
            refreshAll();
            tryConnectXbox360();
        });
        root.addView(refreshBtn);

        serviceStatusInfo = new TextView(this);
        deviceInfo = new TextView(this);
        usbInfo = new TextView(this);
        buttonInfo = new TextView(this);
        xboxRawInfo = new TextView(this);
        eventInfo = new TextView(this);

        addSection(root, "ESTADO DE ACCESIBILIDAD", serviceStatusInfo);
        addSection(root, "DISPOSITIVOS DE ENTRADA", deviceInfo);
        addSection(root, "DISPOSITIVOS USB (INTERFACES Y ENDPOINTS)", usbInfo);
        addSection(root, "PROBADOR DE BOTONES (KEYEVENT)", buttonInfo);
        addSection(root, "XBOX 360 RAW USB (045E:028E)", xboxRawInfo);
        addSection(root, "EVENTOS Y TOQUES VIRTUALES", eventInfo);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);

        updateButtonDisplay();
        updateServiceStatus();
        xboxRawInfo.setText("Xbox 360 USB no conectado o sin permiso.");
    }

    private void addSection(LinearLayout root, String title, TextView content) {
        TextView heading = new TextView(this);
        heading.setText("\n" + title);
        heading.setTextSize(17);

        content.setTextSize(13);
        content.setPadding(0, 8, 0, 12);

        root.addView(heading);
        root.addView(content);
    }

    private void updateServiceStatus() {
        if (serviceStatusInfo == null) return;
        JoystickTouchService svc = JoystickTouchService.getInstance();
        if (svc != null) {
            serviceStatusInfo.setText("ACTIVO | " + svc.getLastActionText());
        } else {
            serviceStatusInfo.setText("INACTIVO — Activa 'USB Joystick Touch' en Ajustes -> Accesibilidad.");
        }
    }

    private void triggerVirtualTapForSlot(int slotNumber, String sourceLabel) {
        float[] coords = BUTTON_COORDS.get(slotNumber);
        if (coords == null) return;

        long now = System.currentTimeMillis();
        String key = sourceLabel + "#" + slotNumber;
        Long prev = lastTapTimeMs.get(key);
        if (prev != null && (now - prev) < TAP_COOLDOWN_MS) {
            return;
        }
        lastTapTimeMs.put(key, now);

        JoystickTouchService svc = JoystickTouchService.getInstance();
        if (svc == null) {
            log("Touch omitido (accesibilidad inactiva) para button " + slotNumber);
            updateServiceStatus();
            return;
        }

        svc.tapAt(coords[0], coords[1]);
        handler.post(() -> {
            updateServiceStatus();
            log(String.format(Locale.US,
                    "TOUCH -> button %d (%.0f, %.0f) desde %s",
                    slotNumber, coords[0], coords[1], sourceLabel));
        });
    }

    private void mapKeyEventToVirtualTouch(int keyCode) {
        Integer slot = null;
        if (keyCode == KeyEvent.KEYCODE_BUTTON_2 || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            slot = 2;
        } else if (keyCode == KeyEvent.KEYCODE_BUTTON_3 || keyCode == KeyEvent.KEYCODE_BUTTON_X) {
            slot = 3;
        } else if (keyCode == KeyEvent.KEYCODE_BUTTON_5 || keyCode == KeyEvent.KEYCODE_BUTTON_L1) {
            slot = 5;
        } else if (keyCode == KeyEvent.KEYCODE_BUTTON_6 || keyCode == KeyEvent.KEYCODE_BUTTON_R1) {
            slot = 6;
        } else if (keyCode == KeyEvent.KEYCODE_BUTTON_7 || keyCode == KeyEvent.KEYCODE_BUTTON_L2) {
            slot = 7;
        } else if (keyCode == KeyEvent.KEYCODE_BUTTON_8 || keyCode == KeyEvent.KEYCODE_BUTTON_R2) {
            slot = 8;
        }

        if (slot != null) {
            triggerVirtualTapForSlot(slot, KeyEvent.keyCodeToString(keyCode));
        }
    }

    // -------------------------------------------------
    // DISPOSITIVOS DE ENTRADA DE ANDROID
    // -------------------------------------------------

    private void refreshAll() {
        refreshInputDevices();
        refreshUsbDevices();
    }

    private void refreshInputDevices() {
        StringBuilder result = new StringBuilder();
        int[] ids = InputDevice.getDeviceIds();
        boolean foundAny = false;

        for (int id : ids) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null) continue;

            int sources = device.getSources();
            if (!isControllerSource(sources) && !device.isExternal()) {
                continue;
            }
            foundAny = true;

            result.append("\nNombre: ").append(device.getName());
            result.append("\nID: ").append(device.getId());
            result.append(" | Externo: ").append(device.isExternal());
            result.append("\nDescriptor: ").append(device.getDescriptor());

            result.append(String.format(
                    Locale.US,
                    "\nVID: %04X  PID: %04X",
                    device.getVendorId(),
                    device.getProductId()));

            result.append(String.format(
                    Locale.US,
                    "\nSources: 0x%08X",
                    sources));

            result.append("\nEjes disponibles:");
            for (InputDevice.MotionRange range : device.getMotionRanges()) {
                result.append("\n  ")
                        .append(MotionEvent.axisToString(range.getAxis()))
                        .append(" [")
                        .append(range.getMin())
                        .append(" / ")
                        .append(range.getMax())
                        .append("]");
            }

            result.append("\n--------------------\n");
        }

        if (!foundAny) {
            result.append("No hay mandos/dispositivos externos registrados en InputManager.");
        }

        deviceInfo.setText(result.toString());
    }

    // -------------------------------------------------
    // DISPOSITIVOS USB: INFORMACIÓN DE INTERFACES
    // -------------------------------------------------

    private void refreshUsbDevices() {
        StringBuilder result = new StringBuilder();
        if (usbManager == null) {
            usbInfo.setText("UsbManager no disponible.");
            return;
        }

        Map<String, UsbDevice> devices = usbManager.getDeviceList();

        if (devices.isEmpty()) {
            result.append("No hay dispositivos USB.");
        } else {
            for (UsbDevice device : devices.values()) {
                result.append("\nNombre: ").append(device.getDeviceName());
                result.append(String.format(Locale.US, "\nVID: %04X", device.getVendorId()));
                result.append(String.format(Locale.US, "\nPID: %04X", device.getProductId()));
                result.append("\nClase dispositivo: ").append(device.getDeviceClass());
                result.append("\nSubclase dispositivo: ").append(device.getDeviceSubclass());
                result.append("\nProtocolo dispositivo: ").append(device.getDeviceProtocol());
                result.append("\nInterfaces: ").append(device.getInterfaceCount());

                for (int i = 0; i < device.getInterfaceCount(); i++) {
                    UsbInterface usbInterface = device.getInterface(i);

                    result.append("\n\n  Interfaz ").append(i);
                    result.append("\n  ID: ").append(usbInterface.getId());
                    result.append("\n  Clase: ").append(usbInterface.getInterfaceClass());
                    result.append("\n  Subclase: ").append(usbInterface.getInterfaceSubclass());
                    result.append("\n  Protocolo: ").append(usbInterface.getInterfaceProtocol());
                    result.append("\n  Endpoints: ").append(usbInterface.getEndpointCount());

                    for (int e = 0; e < usbInterface.getEndpointCount(); e++) {
                        UsbEndpoint endpoint = usbInterface.getEndpoint(e);

                        result.append("\n    Endpoint ").append(e);
                        result.append(" | Dirección: ")
                                .append(endpoint.getDirection() == UsbConstants.USB_DIR_IN
                                        ? "IN" : "OUT");
                        result.append(" | Tipo: ")
                                .append(endpointTypeToString(endpoint.getType()));
                        result.append(" | Número: ")
                                .append(endpoint.getEndpointNumber());
                        result.append(" | Max packet: ")
                                .append(endpoint.getMaxPacketSize());
                    }
                }

                result.append("\n--------------------\n");
            }
        }

        usbInfo.setText(result.toString());
    }

    private String endpointTypeToString(int type) {
        switch (type) {
            case UsbConstants.USB_ENDPOINT_XFER_CONTROL:
                return "CONTROL";
            case UsbConstants.USB_ENDPOINT_XFER_ISOC:
                return "ISOCHRONOUS";
            case UsbConstants.USB_ENDPOINT_XFER_BULK:
                return "BULK";
            case UsbConstants.USB_ENDPOINT_XFER_INT:
                return "INTERRUPT";
            default:
                return "DESCONOCIDO (" + type + ")";
        }
    }

    // -------------------------------------------------
    // XBOX 360 (045E:028E) VIA USB HOST SIN ROOT
    // -------------------------------------------------

    private void tryConnectXbox360() {
        if (usbManager == null || readingXbox) return;

        for (UsbDevice device : usbManager.getDeviceList().values()) {
            if (device.getVendorId() == XBOX360_VID
                    && device.getProductId() == XBOX360_PID) {

                if (usbManager.hasPermission(device)) {
                    startXbox360Reader(device);
                } else {
                    Intent permissionIntent = new Intent(ACTION_USB_PERMISSION);
                    permissionIntent.setPackage(getPackageName());

                    PendingIntent pendingIntent = PendingIntent.getBroadcast(
                            this, 0, permissionIntent, PendingIntent.FLAG_IMMUTABLE);

                    usbManager.requestPermission(device, pendingIntent);
                    log("Solicitando permiso USB para Xbox 360...");
                }
                return;
            }
        }
    }

    private void startXbox360Reader(UsbDevice device) {
        stopXbox360Reader();

        if (device.getInterfaceCount() == 0) {
            log("Xbox 360 sin interfaces USB.");
            return;
        }

        UsbInterface iface = device.getInterface(0);
        UsbEndpoint endpointIn = null;

        for (int i = 0; i < iface.getEndpointCount(); i++) {
            UsbEndpoint ep = iface.getEndpoint(i);
            if (ep.getDirection() == UsbConstants.USB_DIR_IN
                    && ep.getType() == UsbConstants.USB_ENDPOINT_XFER_INT) {
                endpointIn = ep;
                break;
            }
        }

        if (endpointIn == null) {
            log("No se encontró endpoint INTERRUPT IN en interfaz 0 del Xbox 360.");
            return;
        }

        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            log("No se pudo abrir UsbDeviceConnection para Xbox 360.");
            return;
        }

        if (!connection.claimInterface(iface, true)) {
            connection.close();
            log("No se pudo reclamar interfaz 0 del Xbox 360.");
            return;
        }

        this.xboxConnection = connection;
        this.xboxInterface = iface;
        this.lastXboxButtonsMask = 0;
        this.readingXbox = true;

        final UsbEndpoint targetEp = endpointIn;
        log("Leyendo Xbox 360 USB en interfaz 0, endpoint " + targetEp.getEndpointNumber());

        xboxReadThread = new Thread(() -> {
            byte[] buffer = new byte[Math.max(32, targetEp.getMaxPacketSize())];

            while (readingXbox) {
                int bytesRead = connection.bulkTransfer(
                        targetEp, buffer, buffer.length, 100);

                if (bytesRead >= 14 && buffer[0] == 0x00) {
                    final String parsed = parseXbox360Report(buffer, bytesRead);
                    handler.post(() -> {
                        if (readingXbox && xboxRawInfo != null) {
                            xboxRawInfo.setText(parsed);
                        }
                    });
                }
            }
        }, "Xbox360-UsbReader");

        xboxReadThread.start();
    }

    private String parseXbox360Report(byte[] b, int len) {
        int btnLow = b[2] & 0xFF;
        int btnHigh = b[3] & 0xFF;
        int currentMask = (btnHigh << 8) | btnLow;

        // Detectar flancos de subida (botones recién presionados)
        int newlyPressed = currentMask & (~lastXboxButtonsMask);
        lastXboxButtonsMask = currentMask;

        if (newlyPressed != 0) {
            if ((newlyPressed & 0x2000) != 0) triggerVirtualTapForSlot(2, "XBOX_B");
            if ((newlyPressed & 0x4000) != 0) triggerVirtualTapForSlot(3, "XBOX_X");
            if ((newlyPressed & 0x0100) != 0) triggerVirtualTapForSlot(5, "XBOX_LB");
            if ((newlyPressed & 0x0200) != 0) triggerVirtualTapForSlot(6, "XBOX_RB");
            if ((newlyPressed & 0x0020) != 0) triggerVirtualTapForSlot(7, "XBOX_BACK");
            if ((newlyPressed & 0x0010) != 0) triggerVirtualTapForSlot(8, "XBOX_START");
        }

        StringBuilder buttons = new StringBuilder();
        if ((btnLow & 0x01) != 0) buttons.append("DPAD_UP ");
        if ((btnLow & 0x02) != 0) buttons.append("DPAD_DOWN ");
        if ((btnLow & 0x04) != 0) buttons.append("DPAD_LEFT ");
        if ((btnLow & 0x08) != 0) buttons.append("DPAD_RIGHT ");
        if ((btnLow & 0x10) != 0) buttons.append("START ");
        if ((btnLow & 0x20) != 0) buttons.append("BACK ");
        if ((btnLow & 0x40) != 0) buttons.append("L_THUMB ");
        if ((btnLow & 0x80) != 0) buttons.append("R_THUMB ");

        if ((btnHigh & 0x01) != 0) buttons.append("LB ");
        if ((btnHigh & 0x02) != 0) buttons.append("RB ");
        if ((btnHigh & 0x04) != 0) buttons.append("GUIDE ");
        if ((btnHigh & 0x10) != 0) buttons.append("A ");
        if ((btnHigh & 0x20) != 0) buttons.append("B ");
        if ((btnHigh & 0x40) != 0) buttons.append("X ");
        if ((btnHigh & 0x80) != 0) buttons.append("Y ");

        int lt = b[4] & 0xFF;
        int rt = b[5] & 0xFF;

        ByteBuffer bb = ByteBuffer.wrap(b, 6, 8).order(ByteOrder.LITTLE_ENDIAN);
        short lx = bb.getShort();
        short ly = bb.getShort();
        short rx = bb.getShort();
        short ry = bb.getShort();

        StringBuilder hex = new StringBuilder();
        int shown = Math.min(len, 20);
        for (int i = 0; i < shown; i++) {
            hex.append(String.format(Locale.US, "%02X ", b[i] & 0xFF));
        }

        return String.format(Locale.US,
                "Botones: %s\n"
                        + "LT: %3d | RT: %3d\n"
                        + "LX: %6d | LY: %6d\n"
                        + "RX: %6d | RY: %6d\n"
                        + "Raw (%dB): %s",
                buttons.length() == 0 ? "(ninguno)" : buttons.toString().trim(),
                lt, rt, lx, ly, rx, ry, len, hex.toString().trim());
    }

    private void stopXbox360Reader() {
        readingXbox = false;

        if (xboxReadThread != null) {
            try {
                xboxReadThread.join(300);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            xboxReadThread = null;
        }

        if (xboxConnection != null) {
            try {
                if (xboxInterface != null) {
                    xboxConnection.releaseInterface(xboxInterface);
                }
            } catch (Exception ignored) {
            }
            try {
                xboxConnection.close();
            } catch (Exception ignored) {
            }
            xboxConnection = null;
            xboxInterface = null;
        }
    }

    // -------------------------------------------------
    // PROBADOR DE BOTONES (KEYEVENT)
    // -------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        InputDevice dev = event.getDevice();
        if (dev != null && dev.isExternal()) {
            int action = event.getAction();
            int keyCode = event.getKeyCode();

            if (action == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    pressedButtons.put(keyCode, KeyEvent.keyCodeToString(keyCode));
                    updateButtonDisplay();
                    mapKeyEventToVirtualTouch(keyCode);

                    log(String.format(Locale.US,
                            "DOWN | %s (key=%d, scan=%d, src=0x%X)",
                            KeyEvent.keyCodeToString(keyCode),
                            keyCode,
                            event.getScanCode(),
                            event.getSource()));
                }
                return true;
            }

            if (action == KeyEvent.ACTION_UP) {
                pressedButtons.remove(keyCode);
                updateButtonDisplay();

                log(String.format(Locale.US,
                        "UP   | %s (key=%d, scan=%d, src=0x%X)",
                        KeyEvent.keyCodeToString(keyCode),
                        keyCode,
                        event.getScanCode(),
                        event.getSource()));
                return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    private void updateButtonDisplay() {
        if (buttonInfo == null) return;

        if (pressedButtons.isEmpty()) {
            buttonInfo.setText(
                    "Ningún botón presionado.\n"
                            + "Presiona los botones del mando para comprobarlos.");
            return;
        }

        StringBuilder result = new StringBuilder();
        result.append("BOTONES PRESIONADOS:\n\n");

        for (Map.Entry<Integer, String> entry : pressedButtons.entrySet()) {
            result.append("● ")
                    .append(entry.getValue())
                    .append("  (código ")
                    .append(entry.getKey())
                    .append(")\n");
        }

        buttonInfo.setText(result.toString());
    }

    // -------------------------------------------------
    // EJES ANALÓGICOS
    // -------------------------------------------------

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (isControllerSource(event.getSource())
                && event.getAction() == MotionEvent.ACTION_MOVE) {

            StringBuilder values = new StringBuilder();
            values.append("Dispositivo: ")
                    .append(event.getDevice() != null
                            ? event.getDevice().getName()
                            : "Desconocido");

            int[] axes = {
                    MotionEvent.AXIS_X,
                    MotionEvent.AXIS_Y,
                    MotionEvent.AXIS_Z,
                    MotionEvent.AXIS_RZ,
                    MotionEvent.AXIS_RX,
                    MotionEvent.AXIS_RY,
                    MotionEvent.AXIS_HAT_X,
                    MotionEvent.AXIS_HAT_Y,
                    MotionEvent.AXIS_LTRIGGER,
                    MotionEvent.AXIS_RTRIGGER,
                    MotionEvent.AXIS_BRAKE,
                    MotionEvent.AXIS_GAS
            };

            for (int axis : axes) {
                float value = event.getAxisValue(axis);
                values.append("\n")
                        .append(MotionEvent.axisToString(axis))
                        .append(": ")
                        .append(String.format(Locale.US, "%.3f", value));
            }

            eventInfo.setText(values.toString());
            return true;
        }

        return super.dispatchGenericMotionEvent(event);
    }

    private boolean isControllerSource(int source) {
        return (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                || (source & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD;
    }

    // -------------------------------------------------
    // REGISTRO DE EVENTOS
    // -------------------------------------------------

    private void log(String message) {
        if (eventInfo == null) return;

        String current = eventInfo.getText().toString();
        current = message + "\n" + current;

        if (current.length() > 6000) {
            current = current.substring(0, 5000);
        }

        eventInfo.setText(current);
    }

    @Override
    protected void onDestroy() {
        stopXbox360Reader();

        try {
            unregisterReceiver(usbReceiver);
        } catch (Exception ignored) {
        }

        if (inputManager != null) {
            inputManager.unregisterInputDeviceListener(deviceListener);
        }

        super.onDestroy();
    }

		private void testCenterScreenTap() {
		JoystickTouchService svc = JoystickTouchService.getInstance();
		if (svc == null) {
			log("Servicio inactivo.");
			return;
		}

		android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
		float centerX = dm.widthPixels / 2f;
		float centerY = dm.heightPixels / 2f;

		log(String.format(java.util.Locale.US,
				"Probando centro (%dx%d): (%.1f, %.1f)",
				dm.widthPixels, dm.heightPixels, centerX, centerY));

		// Puedes añadir un método tapAt(x, y, durationMs) en JoystickTouchService
		svc.swipe(centerX, centerY, centerX, centerY, 80L);
	}
            
}
