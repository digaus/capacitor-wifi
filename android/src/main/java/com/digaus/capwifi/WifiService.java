package com.digaus.capwifi;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.AsyncTask;
import android.os.Build;
import android.os.PatternMatcher;
import android.provider.Settings;
import android.util.Log;

import com.getcapacitor.Bridge;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class WifiService {
    private static String TAG = "WifiService";

    private static final int API_VERSION = Build.VERSION.SDK_INT;

    private PluginCall savedCall;
    private ConnectivityManager.NetworkCallback networkCallback;

    WifiManager wifiManager;
    ConnectivityManager connectivityManager;
    Context context;

    Bridge bridge;

    public void load(Bridge bridge) {
        this.bridge = bridge;
        this.wifiManager = (WifiManager) this.bridge.getActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        this.connectivityManager = (ConnectivityManager)  this.bridge.getActivity().getApplicationContext().getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        this.context = this.bridge.getContext();
    }
    public void connect(PluginCall call) {
        this.savedCall = call;
        if (API_VERSION < 29) {
            String ssid = "\"" + call.getString("ssid") + "\"";
            String password =  "\"" + call.getString("password") + "\"";
            String authType = call.getString("authType");
            this.add(call, ssid, password, authType);

            int networkIdToConnect = ssidToNetworkId(ssid, authType);

            if (networkIdToConnect > -1) {
                this.forceWifiUsage(false);
                wifiManager.enableNetwork(networkIdToConnect, true);
                this.forceWifiUsage(true);

                // Wait for connection to finish, otherwise throw a timeout error
                new ConnectAsync().execute(call, networkIdToConnect, this);

            } else {
                call.error("INVALID_NETWORK_ID_TO_CONNECT");
            }
        } else {

            String ssid = call.getString("ssid");
            String password =  call.getString("password");

            String connectedSSID = this.getWifiServiceInfo(call);
            //this.forceWifiUsageQ(false, null, null);

            if (!ssid.equals(connectedSSID)) {

                WifiNetworkSpecifier.Builder builder = new WifiNetworkSpecifier.Builder();
                builder.setSsid(ssid);
                if (password != null && password.length() > 0) {
                    builder.setWpa2Passphrase(password);
                }

                WifiNetworkSpecifier wifiNetworkSpecifier = builder.build();
                NetworkRequest.Builder networkRequestBuilder = new NetworkRequest.Builder();
                networkRequestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
                networkRequestBuilder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
                networkRequestBuilder.addCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED);
                networkRequestBuilder.setNetworkSpecifier(wifiNetworkSpecifier);
                NetworkRequest networkRequest = networkRequestBuilder.build();
                this.forceWifiUsageQ(true, networkRequest);
            } else {
                this.getConnectedSSID(call);
            }
        }

    }
    public void connectPrefix(PluginCall call) {
        this.savedCall = call;
        if (API_VERSION < 29) {
            call.error("API_29_OR_GREATE_REQUIRED");
        } else {
            String ssid = call.getString("ssid");
            String password =  call.getString("password");

            String connectedSSID = this.getWifiServiceInfo(call);
            //this.forceWifiUsageQ(false, null, null);

            if (!ssid.equals(connectedSSID)) {

                WifiNetworkSpecifier.Builder builder = new WifiNetworkSpecifier.Builder();
                PatternMatcher ssidPattern = new PatternMatcher(ssid, PatternMatcher.PATTERN_PREFIX);
                builder.setSsidPattern(ssidPattern);
                if (password != null && password.length() > 0) {
                    builder.setWpa2Passphrase(password);
                }

                WifiNetworkSpecifier wifiNetworkSpecifier = builder.build();
                NetworkRequest.Builder networkRequestBuilder = new NetworkRequest.Builder();
                networkRequestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
                networkRequestBuilder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
                networkRequestBuilder.addCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED);
                networkRequestBuilder.setNetworkSpecifier(wifiNetworkSpecifier);
                NetworkRequest networkRequest = networkRequestBuilder.build();
                this.forceWifiUsageQ(true, networkRequest);
            } else {
                this.getConnectedSSID(call);
            }
        }

    }
    /**
     * Scans networks and sends the list back on the success callback
     */
    public boolean scanNetwork(PluginCall call) {
        Log.v(TAG, "Entering startScan");
        this.savedCall = call;
        final ScanSyncContext syncContext = new ScanSyncContext();

        final BroadcastReceiver receiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                Log.v(TAG, "Entering onReceive");
                PluginCall call = WifiService.this.savedCall;
                synchronized (syncContext) {
                    if (syncContext.finished) {
                        Log.v(TAG, "In onReceive, already finished");
                        return;
                    }
                    syncContext.finished = true;
                    context.unregisterReceiver(this);
                }

                Log.v(TAG, "In onReceive, success");
                getScanResults(call);
            }
        };

        final Context context = this.context;

        Log.v(TAG, "Submitting timeout to threadpool");

        this.bridge.execute(new Runnable() {

            public void run() {
                PluginCall call = WifiService.this.savedCall;

                Log.v(TAG, "Entering timeout");

                final int TEN_SECONDS = 10000;

                try {
                    Thread.sleep(TEN_SECONDS);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Received InterruptedException e, " + e);
                    // keep going into error
                }

                Log.v(TAG, "Thread sleep done");

                synchronized (syncContext) {
                    if (syncContext.finished) {
                        Log.v(TAG, "In timeout, already finished");
                        return;
                    }
                    syncContext.finished = true;
                    context.unregisterReceiver(receiver);
                }

                Log.v(TAG, "In timeout, error");
                call.error("TIMEOUT_WAITING_FOR_SCAN");
            }

        });

        Log.v(TAG, "Registering broadcastReceiver");
        context.registerReceiver(
                receiver,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        );

        if (!wifiManager.startScan()) {
            Log.v(TAG, "Scan failed");
            call.error("SCAN_FAILED");
            return false;
        }

        Log.v(TAG, "Starting wifi scan");
        return true;
    }


    public void getConnectedSSID(PluginCall call) {

        String connectedSSID = this.getWifiServiceInfo(call);
        Log.i(TAG, "Connected SSID: " + connectedSSID);

        if (connectedSSID != null) {
            JSObject result = new JSObject();
            result.put("ssid", connectedSSID);
            call.success(result);
        }
    }

    /**
     * Format and return WiFi IPv4 Address
     * @return
     */
    public void getWifiIP(PluginCall call) {
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ip = wifiInfo.getIpAddress();
        String ipString = formatIP(ip);

        if (ipString != null && !ipString.equals("0.0.0.0")) {
            JSObject result = new JSObject();
            result.put("ip", ipString);
            call.success(result);
        } else {
            call.error("NO_VALID_IP_IDENTIFIED");
        }
    }
    private String formatIP(int ip) {
        return String.format(
                "%d.%d.%d.%d",
                (ip & 0xff),
                (ip >> 8 & 0xff),
                (ip >> 16 & 0xff),
                (ip >> 24 & 0xff)
        );
    }

    private String getWifiServiceInfo(PluginCall call) {

        WifiInfo info = wifiManager.getConnectionInfo();

        if (info == null) {
            call.error("UNABLE_TO_READ_WIFI_INFO");
            return null;
        }

        // Only return SSID when actually connected to a network
        SupplicantState state = info.getSupplicantState();
        if (!state.equals(SupplicantState.COMPLETED)) {
            call.error("CONNECTION_NOT_COMPLETED");
            return null;
        }

        String serviceInfo;
        serviceInfo = info.getSSID();

        if (serviceInfo == null || serviceInfo.isEmpty() || serviceInfo == "0x") {
            call.error("WIFI_INFORMATION_EMPTY");
            return null;
        }

        // http://developer.android.com/reference/android/net/wifi/WifiInfo.html#getSSID()
        if (serviceInfo.startsWith("\"") && serviceInfo.endsWith("\"")) {
            serviceInfo = serviceInfo.substring(1, serviceInfo.length() - 1);
        }

        return serviceInfo;

    }
    private Number add(PluginCall call, String ssid, String password, String authType) {
        // Initialize the WifiConfiguration object
        WifiConfiguration wifi = new WifiConfiguration();
        Log.i(TAG, ssid+password+authType);

        try {

            if (authType.equals("WPA2")) {
                /**
                 * WPA2 Data format:
                 * 0: ssid
                 * 1: auth
                 * 2: password
                 */
                wifi.SSID = ssid;
                wifi.preSharedKey = password;

                wifi.status = WifiConfiguration.Status.ENABLED;
                wifi.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                wifi.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                wifi.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                wifi.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
                wifi.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
                wifi.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
                wifi.allowedProtocols.set(WifiConfiguration.Protocol.WPA);

                wifi.networkId = ssidToNetworkId(ssid, authType);

            } else if (authType.equals("WPA")) {
                /**
                 * WPA Data format:
                 * 0: ssid
                 * 1: auth
                 * 2: password
                 */
                wifi.SSID = ssid;
                wifi.preSharedKey = password;

                wifi.status = WifiConfiguration.Status.ENABLED;
                wifi.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                wifi.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                wifi.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                wifi.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
                wifi.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
                wifi.allowedProtocols.set(WifiConfiguration.Protocol.WPA);

                wifi.networkId = ssidToNetworkId(ssid, authType);

            } else if (authType.equals("WEP")) {
                /**
                 * WEP Data format:
                 * 0: ssid
                 * 1: auth
                 * 2: password
                 */
                wifi.SSID = ssid;

                if (getHexKey(password)) {
                    wifi.wepKeys[0] = password;
                } else {
                    wifi.wepKeys[0] = "\"" + password + "\"";
                }
                wifi.wepTxKeyIndex = 0;

                wifi.status = WifiConfiguration.Status.ENABLED;
                wifi.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
                wifi.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
                wifi.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                wifi.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                wifi.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                wifi.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                wifi.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
                wifi.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
                wifi.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
                wifi.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
                wifi.allowedProtocols.set(WifiConfiguration.Protocol.WPA);

                wifi.networkId = ssidToNetworkId(ssid, authType);

            } else if (authType.equals("NONE")) {
                /**
                 * OPEN Network data format:
                 * 0: ssid
                 * 1: auth
                 * 2: <not used>
                 * 3: isHiddenSSID
                 */
                wifi.SSID = ssid;
                wifi.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                wifi.networkId = ssidToNetworkId(ssid, authType);

            } else {

                call.reject("AUTH_TYPE_NOT_SUPPORTED");
                return -1;

            }
            // Set network to highest priority (deprecated in API >= 26)
            if( API_VERSION < 26 ){
                wifi.priority = getMaxWifiPriority(wifiManager) + 1;
            }

            // After processing authentication types, add or update network
            if (wifi.networkId == -1) { // -1 means SSID configuration does not exist yet

                int newNetId = wifiManager.addNetwork(wifi);
                Log.i(TAG, "NETID: " + newNetId);
                if ( newNetId > -1 ){
                    return newNetId;
                } else {
                    call.reject( "ERROR_ADDING_NETWORK" );
                }

            } else {

                int updatedNetID = wifiManager.updateNetwork(wifi);

                if( updatedNetID > -1 ){
                    return updatedNetID;
                } else {
                    call.reject( "ERROR_UPDATING_NETWORK" );
                }

            }
            return -1;

        } catch (Exception e) {
            call.reject(e.getMessage());
            return -1;
        }
    }
    /**
     * Class to store finished boolean in
     */
    private class ScanSyncContext {

        public boolean finished = false;
    }

    /**
     * This method uses the callbackContext.success method to send a JSONArray of the scanned
     * networks.
     */
    private boolean getScanResults(PluginCall call) {
        List<ScanResult> scanResults = wifiManager.getScanResults();

        JSONArray returnList = new JSONArray();


        for (ScanResult scan : scanResults) {
            /*
             * @todo - breaking change, remove this notice when tidying new release and explain changes, e.g.:
             *   0.y.z includes a breaking change to WifiWizard2.getScanResults().
             *   Earlier versions set scans' level attributes to a number derived from wifiManager.calculateSignalLevel.
             *   This update returns scans' raw RSSI value as the level, per Android spec / APIs.
             *   If your application depends on the previous behaviour, we have added an options object that will modify behaviour:
             *   - if `(n == true || n < 2)`, `*.getScanResults({numLevels: n})` will return data as before, split in 5 levels;
             *   - if `(n > 1)`, `*.getScanResults({numLevels: n})` will calculate the signal level, split in n levels;
             *   - if `(n == false)`, `*.getScanResults({numLevels: n})` will use the raw signal level;
             */

            int level = scan.level;

            JSONObject lvl = new JSONObject();
            try {
                lvl.put("level", level);
                lvl.put("SSID", scan.SSID);
                lvl.put("BSSID", scan.BSSID);
                lvl.put("frequency", scan.frequency);
                lvl.put("capabilities", scan.capabilities);
                lvl.put("timestamp", scan.timestamp);

                if (API_VERSION >= 23) { // Marshmallow
                    lvl.put("channelWidth", scan.channelWidth);
                    lvl.put("centerFreq0", scan.centerFreq0);
                    lvl.put("centerFreq1", scan.centerFreq1);
                } else {
                    lvl.put("channelWidth", JSONObject.NULL);
                    lvl.put("centerFreq0", JSONObject.NULL);
                    lvl.put("centerFreq1", JSONObject.NULL);
                }

                returnList.put(lvl);
            } catch (JSONException e) {
                e.printStackTrace();
                call.error(e.toString());
                return false;
            }
        }
        JSObject result = new JSObject();
        result.put("scan", returnList);
        call.success(result);
        return true;
    }

    private static int getMaxWifiPriority(final WifiManager wifiManager) {
        final List<WifiConfiguration> configurations = wifiManager.getConfiguredNetworks();
        int maxPriority = 0;
        for (WifiConfiguration config : configurations) {
            if (config.priority > maxPriority) {
                maxPriority = config.priority;
            }
        }

        return maxPriority;
    }
    private static boolean getHexKey(String s) {
        if (s == null) {
            return false;
        }

        int len = s.length();
        if (len != 10 && len != 26 && len != 58) {
            return false;
        }

        for (int i = 0; i < len; ++i) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }
    private int ssidToNetworkId(String ssid, String authType) {
        try {

            int maybeNetId = Integer.parseInt(ssid);
            return maybeNetId;

        } catch (NumberFormatException e) {
            List<WifiConfiguration> currentNetworks = wifiManager.getConfiguredNetworks();
            int networkId = -1;
            // For each network in the list, compare the SSID with the given one and check if authType matches
            Log.i(TAG, "MyNetwork: " + ssid + "|" + authType);

            for (WifiConfiguration network : currentNetworks) {
                Log.i(TAG, "Network: " + network.SSID + "|" + this.getSecurityType(network));

                if (network.SSID != null) {
                    if (authType.length() == 0) {
                        if(network.SSID.equals(ssid)) {
                            networkId = network.networkId;
                        }
                    } else {
                        String testSSID = network.SSID + this.getSecurityType(network);
                        if(testSSID.equals(ssid + authType)) {
                            networkId = network.networkId;
                        }
                    }
                }
            }
            // Fallback to WPA if WPA2 is not found
            if (networkId == -1 && authType.substring(0,3).equals("WPA")) {
                for (WifiConfiguration network : currentNetworks) {
                    if (network.SSID != null) {
                        if (authType.length() == 0) {
                            if(network.SSID.equals(ssid)) {
                                networkId = network.networkId;
                            }
                        } else {
                            String testSSID = network.SSID + this.getSecurityType(network).substring(0,3);
                            if(testSSID.equals(ssid + authType)) {
                                networkId = network.networkId;
                            }
                        }
                    }
                }
            }
            return networkId;
        }
    }
    public void forceWifiUsageQ(boolean useWifi, NetworkRequest networkRequest) {
        if (API_VERSION >= 29) {
            if (useWifi) {
                final ConnectivityManager manager = (ConnectivityManager) this.context
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
                if (networkRequest == null) {
                    networkRequest = new NetworkRequest.Builder()
                            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            .build();
                }

                manager.requestNetwork(networkRequest, new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        manager.bindProcessToNetwork(network);
                        String currentSSID = WifiService.this.getWifiServiceInfo(null);
                        PluginCall call = WifiService.this.savedCall;
                        String ssid = call.getString("ssid");
                        if (call.getMethodName().equals("connectPrefix") && currentSSID.startsWith(ssid) || call.getMethodName().equals("connect") && currentSSID.equals(ssid)) {
                            WifiService.this.getConnectedSSID(WifiService.this.savedCall);
                        } else {
                            call.error("CONNECTED_SSID_DOES_NOT_MATCH_REQUESTED_SSID");
                        }
                        WifiService.this.networkCallback = this;
                    }
                    @Override
                    public void onUnavailable() {
                        PluginCall call = WifiService.this.savedCall;
                        call.error("CONNECTION_FAILED");
                    }
                });

            } else {
                ConnectivityManager manager = (ConnectivityManager) this.context
                        .getSystemService(Context.CONNECTIVITY_SERVICE);

                if (this.networkCallback != null) {
                    manager.unregisterNetworkCallback(this.networkCallback);
                    this.networkCallback = null;
                }
                manager.bindProcessToNetwork(null);
            }
        }
    }
    public void forceWifiUsage(boolean useWifi) {
        boolean canWriteFlag = false;

        if (useWifi) {
            if (API_VERSION >= Build.VERSION_CODES.LOLLIPOP) {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    canWriteFlag = true;
                    // Only need ACTION_MANAGE_WRITE_SETTINGS on 6.0.0, regular permissions suffice on later versions
                } else if (Build.VERSION.RELEASE.toString().equals("6.0.1")) {
                    canWriteFlag = true;
                    // Don't need ACTION_MANAGE_WRITE_SETTINGS on 6.0.1, if we can positively identify it treat like 7+
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // On M 6.0.0 (N+ or higher and 6.0.1 hit above), we need ACTION_MANAGE_WRITE_SETTINGS to forceWifi.
                    canWriteFlag = Settings.System.canWrite(this.context);
                    if (!canWriteFlag) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                        intent.setData(Uri.parse("package:" + this.context.getPackageName()));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                        this.context.startActivity(intent);
                    }
                }

                if (((API_VERSION>= Build.VERSION_CODES.M) && canWriteFlag) || ((API_VERSION >= Build.VERSION_CODES.LOLLIPOP) && !(API_VERSION >= Build.VERSION_CODES.M))) {
                    final ConnectivityManager manager = (ConnectivityManager) this.context
                            .getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkRequest networkRequest = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();
                    manager.requestNetwork(networkRequest, new ConnectivityManager.NetworkCallback() {
                        @Override
                        public void onAvailable(Network network) {
                            if (API_VERSION >= Build.VERSION_CODES.M) {
                                manager.bindProcessToNetwork(network);
                            } else {
                                //This method was deprecated in API level 23
                                ConnectivityManager.setProcessDefaultNetwork(network);
                            }
                            try {
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            manager.unregisterNetworkCallback(this);
                        }
                    });
                }
            }
        } else {
            if (API_VERSION >= Build.VERSION_CODES.M) {
                ConnectivityManager manager = (ConnectivityManager) this.context
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
                manager.bindProcessToNetwork(null);
            } else if (API_VERSION >= Build.VERSION_CODES.LOLLIPOP) {
                ConnectivityManager.setProcessDefaultNetwork(null);
            }
        }
    }
    private class ConnectAsync extends AsyncTask<Object, Void, String[]> {
        PluginCall call;
        WifiService wifiService;
        @Override
        protected void onPostExecute(String[] results) {
            String error = results[0];
            if (error != null) {
                this.call.error(error);
            } else {
                this.wifiService.getConnectedSSID(call);
            }
        }

        @Override
        protected String[] doInBackground(Object... params) {
            this.call = (PluginCall) params[0];

            int networkIdToConnect = (Integer) params[1];
            this.wifiService = (WifiService) params[2];

            final int TIMES_TO_RETRY = 15;
            for (int i = 0; i < TIMES_TO_RETRY; i++) {

                WifiInfo info = wifiManager.getConnectionInfo();
                NetworkInfo.DetailedState connectionState = info
                        .getDetailedStateOf(info.getSupplicantState());

                boolean isConnected =
                        // need to ensure we're on correct network because sometimes this code is
                        // reached before the initial network has disconnected
                        info.getNetworkId() == networkIdToConnect && (
                                connectionState == NetworkInfo.DetailedState.CONNECTED ||
                                        // Android seems to sometimes get stuck in OBTAINING_IPADDR after it has received one
                                        (connectionState == NetworkInfo.DetailedState.OBTAINING_IPADDR
                                                && info.getIpAddress() != 0)
                        );

                if (isConnected) {
                    return new String[]{ null, "NETWORK_CONNECTION_COMPLETED" };
                }

                Log.d(TAG, "Got " + connectionState.name() + " on " + (i + 1) + " out of " + TIMES_TO_RETRY);
                final int ONE_SECOND = 1000;

                try {
                    Thread.sleep(ONE_SECOND);
                } catch (InterruptedException e) {
                    Log.e(TAG, e.getMessage());
                    return new String[]{ "INTERRUPT_EXCEPT_WHILE_CONNECTING", null };
                }
            }
            Log.d(TAG, "    Network failed to finish connecting within the timeout");
            return new String[]{ "CONNECT_FAILED_TIMEOUT", null };
        }
    }
    static public String getSecurityType(WifiConfiguration wifiConfig) {

        if (wifiConfig.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE)) {
            // If we never set group ciphers, wpa_supplicant puts all of them.
            // For open, we don't set group ciphers.
            // For WEP, we specifically only set WEP40 and WEP104, so CCMP
            // and TKIP should not be there.
            if (!wifiConfig.allowedGroupCiphers.get(WifiConfiguration.GroupCipher.CCMP)
                    && (wifiConfig.allowedGroupCiphers.get(WifiConfiguration.GroupCipher.WEP40)
                    || wifiConfig.allowedGroupCiphers.get(WifiConfiguration.GroupCipher.WEP104))) {
                return "WEP";
            } else {
                return "NONE";
            }
        } else if (wifiConfig.allowedProtocols.get(WifiConfiguration.Protocol.RSN)) {
            return "WPA2";
        } else if (wifiConfig.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_EAP)) {
            return "WPA";//"WPA_EAP";
        } else if (wifiConfig.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.IEEE8021X)) {
            return "WPA";//"IEEE8021X";
        } else if (wifiConfig.allowedProtocols.get(WifiConfiguration.Protocol.WPA)) {
            return "WPA";
        } else {
            return "NONE";
        }
    }

}