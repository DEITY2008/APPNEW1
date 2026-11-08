package com.thepkub.rc4wd;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.getcapacitor.PluginMethod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CapacitorPlugin(
    name = "CarLink",
    permissions = { @Permission(alias = "btconnect", strings = { "android.permission.BLUETOOTH_CONNECT" }) }
)
public class CarLinkPlugin extends Plugin {
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile BluetoothSocket socket;
    private volatile OutputStream out;

    private boolean needPerm() {
        return Build.VERSION.SDK_INT >= 31 && getPermissionState("btconnect") != PermissionState.GRANTED;
    }

    @PluginMethod
    public void list(PluginCall call) {
        if (needPerm()) { requestPermissionForAlias("btconnect", call, "afterPermList"); return; }
        doList(call);
    }

    @PermissionCallback
    private void afterPermList(PluginCall call) {
        if (needPerm()) call.reject("ต้องอนุญาตสิทธิ์ Bluetooth (อุปกรณ์ใกล้เคียง)");
        else doList(call);
    }

    @SuppressLint("MissingPermission")
    private void doList(PluginCall call) {
        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if (ad == null) { call.reject("เครื่องนี้ไม่มี Bluetooth"); return; }
        if (!ad.isEnabled()) { call.reject("กรุณาเปิด Bluetooth"); return; }
        JSArray arr = new JSArray();
        try {
            for (BluetoothDevice d : ad.getBondedDevices()) {
                JSObject o = new JSObject();
                o.put("name", d.getName());
                o.put("address", d.getAddress());
                arr.put(o);
            }
        } catch (SecurityException e) { call.reject("ไม่มีสิทธิ์ Bluetooth"); return; }
        JSObject r = new JSObject();
        r.put("devices", arr);
        call.resolve(r);
    }

    @PluginMethod
    public void connect(PluginCall call) {
        if (needPerm()) { requestPermissionForAlias("btconnect", call, "afterPermConnect"); return; }
        doConnect(call);
    }

    @PermissionCallback
    private void afterPermConnect(PluginCall call) {
        if (needPerm()) call.reject("ต้องอนุญาตสิทธิ์ Bluetooth (อุปกรณ์ใกล้เคียง)");
        else doConnect(call);
    }

    @SuppressLint("MissingPermission")
    private void doConnect(PluginCall call) {
        final String address = call.getString("address");
        io.execute(() -> {
            try {
                closeQuiet();
                BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
                if (ad == null || address == null) throw new IOException("no adapter/address");
                BluetoothDevice d = ad.getRemoteDevice(address);
                ad.cancelDiscovery();
                BluetoothSocket s;
                try {
                    s = d.createRfcommSocketToServiceRecord(SPP);
                    s.connect();
                } catch (IOException first) {
                    s = d.createInsecureRfcommSocketToServiceRecord(SPP);
                    s.connect();
                }
                socket = s;
                out = s.getOutputStream();
                watch(s);
                call.resolve();
            } catch (Exception e) {
                closeQuiet();
                call.reject("เชื่อมต่อไม่สำเร็จ: " + e.getMessage());
            }
        });
    }

    private void watch(final BluetoothSocket s) {
        new Thread(() -> {
            try {
                InputStream in = s.getInputStream();
                byte[] buf = new byte[64];
                while (in.read(buf) >= 0) { }
            } catch (IOException ignored) { }
            if (socket == s) {
                closeQuiet();
                notifyListeners("disconnected", new JSObject());
            }
        }).start();
    }

    @PluginMethod
    public void write(PluginCall call) {
        final String data = call.getString("data");
        io.execute(() -> {
            try {
                OutputStream o = out;
                if (o == null || data == null) throw new IOException("not connected");
                o.write(data.getBytes("UTF-8"));
                o.flush();
                call.resolve();
            } catch (Exception e) {
                closeQuiet();
                call.reject("write failed");
            }
        });
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        closeQuiet();
        call.resolve();
    }

    private void closeQuiet() {
        BluetoothSocket s = socket;
        socket = null;
        out = null;
        if (s != null) { try { s.close(); } catch (IOException ignored) { } }
    }

    @SuppressWarnings("deprecation")
    @PluginMethod
    public void bindWifi(PluginCall call) {
        try {
            ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities c = cm.getNetworkCapabilities(n);
                if (c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    cm.bindProcessToNetwork(n);
                    break;
                }
            }
        } catch (Exception ignored) { }
        call.resolve();
    }
}
