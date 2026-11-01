package com.github.matheuscruzsouza.androidserver.config;

import android.content.Context;
import android.content.SharedPreferences;

import com.github.matheuscruzsouza.nanospring.server.Environment;

public class ServerConfig {
    private static final String PREF_NAME = "s4_server_preferences";
    private static final String KEY_SERVER_PORT = "custom_server_port";
    private static final String KEY_LT_ENABLED = "localtunnel_enabled";
    private static final String KEY_LT_SUBDOMAIN = "localtunnel_subdomain";
    public static final int DEFAULT_PORT = 9090;
    public static final boolean DEFAULT_LT_ENABLED = false;
    public static final String DEFAULT_LT_SUBDOMAIN = "s4-android";

    public static int getServerPort(Context context) {
        if (context == null) {
            String portProp = Environment.getProperty("server.port", String.valueOf(DEFAULT_PORT));
            try {
                return Integer.parseInt(portProp);
            } catch (Exception e) {
                return DEFAULT_PORT;
            }
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_SERVER_PORT, DEFAULT_PORT);
    }

    public static void setServerPort(Context context, int port) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_SERVER_PORT, port).apply();
        Environment.setProperty("server.port", String.valueOf(port));
    }

    public static boolean isLocalTunnelEnabled(Context context) {
        if (context == null) {
            return Boolean.parseBoolean(Environment.getProperty("nano.localtunnel.enabled", String.valueOf(DEFAULT_LT_ENABLED)));
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_LT_ENABLED, DEFAULT_LT_ENABLED);
    }

    public static void setLocalTunnelEnabled(Context context, boolean enabled) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_LT_ENABLED, enabled).apply();
        Environment.setProperty("nano.localtunnel.enabled", String.valueOf(enabled));
    }

    public static String getLocalTunnelSubdomain(Context context) {
        if (context == null) {
            return Environment.getProperty("nano.localtunnel.subdomain", DEFAULT_LT_SUBDOMAIN);
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LT_SUBDOMAIN, DEFAULT_LT_SUBDOMAIN);
    }

    public static void setLocalTunnelSubdomain(Context context, String subdomain) {
        if (context == null) return;
        String clean = subdomain != null ? subdomain.trim().toLowerCase() : "";
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LT_SUBDOMAIN, clean).apply();
        Environment.setProperty("nano.localtunnel.subdomain", clean);
    }

    public static void syncWithEnvironment(Context context) {
        int port = getServerPort(context);
        Environment.setProperty("server.port", String.valueOf(port));
        boolean ltEnabled = isLocalTunnelEnabled(context);
        Environment.setProperty("nano.localtunnel.enabled", String.valueOf(ltEnabled));
        String ltSubdomain = getLocalTunnelSubdomain(context);
        Environment.setProperty("nano.localtunnel.subdomain", ltSubdomain);
    }

    public static String getMdnsHostname() {
        String name = Environment.getProperty("nano.nsd.name", "s4");
        return name.toLowerCase() + ".local";
    }

    public static boolean isSslEnabled() {
        return Boolean.parseBoolean(Environment.getProperty("server.ssl.enabled", "false"));
    }

    public static String getScheme() {
        return isSslEnabled() ? "https" : "http";
    }

    public static String getMdnsUrl(Context context) {
        int port = getServerPort(context);
        return getScheme() + "://" + getMdnsHostname() + ":" + port;
    }
}
