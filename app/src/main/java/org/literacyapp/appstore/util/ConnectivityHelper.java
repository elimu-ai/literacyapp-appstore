package org.literacyapp.appstore.util;

import android.content.Context;
import android.net.wifi.WifiManager;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.InetAddress;

public class ConnectivityHelper {

    private static Logger logger = Logger.getLogger(ConnectivityHelper.class);

    public static boolean isWifiEnabled(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        return wifiManager.isWifiEnabled();
    }

    public static boolean isServerReachable(Context context) {
        String domain = EnvironmentSettings.getDomain();
        logger.info("Checking if server is reachable: " + domain);
        try {
            return InetAddress.getByName(domain).isReachable(5000);
        } catch (IOException e) {
            return false;
        }
    }
}