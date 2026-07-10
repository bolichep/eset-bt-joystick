package com.btjoystick;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final UUID SPP_UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private ActivityResultLauncher<String[]> permLauncher;
    private WebView          webView;
    private BluetoothAdapter btAdapter;
    private BluetoothSocket  btSocket;
    private OutputStream     btOut;
    private volatile boolean connected = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        // Permissions (Android 12+)
        permLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            grants -> {}
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permLauncher.launch(new String[]{
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            });
        }

        // WebView config
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);   // @SuppressLint above covers this
        ws.setDomStorageEnabled(true);
        // Load HTML from assets using loadDataWithBaseURL to avoid
        // avoids deprecated setAllowFileAccessFromFileURLs
        webView.addJavascriptInterface(new BTBridge(), "Android");
        webView.setWebViewClient(new WebViewClient());
        try {
            java.io.InputStream is = getAssets().open("index.html");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String html = new String(buffer, "UTF-8");
            webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
        } catch (Exception e) {
            webView.loadUrl("file:///android_asset/index.html");
        }

        BluetoothManager btManager =
            (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack();
                else { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); }
            }
        });
    }

    // ── JS → Android bridge ───────────────────────────────────────────
    private class BTBridge {

        @SuppressLint("MissingPermission")
        @JavascriptInterface
        public void getPairedDevices() {
            try {
                if (btAdapter == null) { jsCallback("error", "Bluetooth no disponible"); return; }
                Set<BluetoothDevice> bonded = btAdapter.getBondedDevices();
                JSONArray arr = new JSONArray();
                for (BluetoothDevice d : bonded) {
                    JSONObject o = new JSONObject();
                    o.put("name",    d.getName() != null ? d.getName() : "");
                    o.put("address", d.getAddress());
                    arr.put(o);
                }
                jsCallback("deviceList", arr.toString());
            } catch (Exception e) {
                jsCallback("error", e.getMessage());
            }
        }

        @SuppressLint("MissingPermission")
        @JavascriptInterface
        public void connect(final String address) {
            new Thread(() -> {
                try {
                    if (btSocket != null) { try { btSocket.close(); } catch(Exception ignored){} }
                    BluetoothDevice device = btAdapter.getRemoteDevice(address);
                    btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                    btAdapter.cancelDiscovery();
                    btSocket.connect();
                    btOut     = btSocket.getOutputStream();
                    connected = true;
                    jsCallback("connected", device.getName() != null ? device.getName() : address);
                } catch (Exception e) {
                    connected = false;
                    jsCallback("error", "Conexión fallida: " + e.getMessage());
                    jsCallback("disconnected", "");
                }
            }).start();
        }

        @JavascriptInterface
        public void disconnect() {
            new Thread(() -> {
                connected = false;
                try { if (btSocket != null) btSocket.close(); } catch(Exception ignored){}
                btSocket = null;
                btOut    = null;
                jsCallback("disconnected", "");
            }).start();
        }

        @JavascriptInterface
        public void sendString(final String str) {
            if (!connected || btOut == null) {
                jsCallback("error", "Sin conexión BT");
                return;
            }
            new Thread(() -> {
                try {
                    btOut.write(str.getBytes("UTF-8"));
                    btOut.flush();
                    jsCallback("sent", str);
                } catch (Exception e) {
                    connected = false;
                    jsCallback("error", "Error al enviar: " + e.getMessage());
                    jsCallback("disconnected", "");
                }
            }).start();
        }
    }

    // ── Android → JS ──────────────────────────────────────────────────
    private void jsCallback(final String event, final String data) {
        final String escaped = data == null ? "" :
            data.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
        runOnUiThread(() ->
            webView.evaluateJavascript(
                "window.btCallback('" + event + "', '" + escaped + "')", null)
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { if (btSocket != null) btSocket.close(); } catch(Exception ignored){}
    }
}
