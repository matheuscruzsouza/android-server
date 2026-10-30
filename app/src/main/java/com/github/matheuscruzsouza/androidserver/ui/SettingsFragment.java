package com.github.matheuscruzsouza.androidserver.ui;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.matheuscruzsouza.androidserver.R;
import com.github.matheuscruzsouza.nanospring.server.Environment;

public class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        TextView tvPort = view.findViewById(R.id.tvConfigPort);
        TextView tvMdns = view.findViewById(R.id.tvConfigMdns);
        TextView tvCors = view.findViewById(R.id.tvConfigCors);
        TextView tvDeviceModel = view.findViewById(R.id.tvDeviceModel);
        TextView tvAndroidVersion = view.findViewById(R.id.tvAndroidVersion);

        String port = Environment.getProperty("server.port", "9090");
        tvPort.setText(port + " (TCP)");

        String mdnsName = Environment.getProperty("nano.nsd.name", "AndroidServer");
        String mdnsType = Environment.getProperty("nano.nsd.type", "_http._tcp.");
        tvMdns.setText(mdnsName + ".local (" + mdnsType + ")");

        String corsAllowed = Environment.getProperty("nano.cors.allowed-origins", "*");
        tvCors.setText("Permitido: " + corsAllowed);

        tvDeviceModel.setText("Modelo: " + Build.MANUFACTURER + " " + Build.MODEL);
        tvAndroidVersion.setText("Android OS: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");

        return view;
    }
}
