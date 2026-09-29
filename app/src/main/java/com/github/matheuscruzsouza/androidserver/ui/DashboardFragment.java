package com.github.matheuscruzsouza.androidserver.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.matheuscruzsouza.androidserver.NetworkUtils;
import com.github.matheuscruzsouza.androidserver.R;
import com.github.matheuscruzsouza.androidserver.log.ServerLogBus;
import com.github.matheuscruzsouza.androidserver.service.MyBackendService;
import com.github.matheuscruzsouza.nanospring.server.Environment;

import java.util.Locale;

public class DashboardFragment extends Fragment {

    private TextView tvServerInfo;
    private TextView tvServerAddress;
    private ImageView ivStatusIcon;
    private TextView tvTotalRequestsCount;
    private TextView tvUptimeText;
    private Button btnStart;
    private Button btnStop;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            updateMetrics();
            timerHandler.postDelayed(this, 1000);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        tvServerInfo = view.findViewById(R.id.tvServerInfo);
        tvServerAddress = view.findViewById(R.id.tvServerAddress);
        ivStatusIcon = view.findViewById(R.id.ivStatusIcon);
        tvTotalRequestsCount = view.findViewById(R.id.tvTotalRequestsCount);
        tvUptimeText = view.findViewById(R.id.tvUptimeText);
        btnStart = view.findViewById(R.id.btnStartServer);
        btnStop = view.findViewById(R.id.btnStopServer);

        btnStart.setOnClickListener(v -> startServer());
        btnStop.setOnClickListener(v -> stopServer());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        timerHandler.post(timerRunnable);
        if (ServerLogBus.getServerStartTime() > 0) {
            setOnlineUi();
        } else {
            // Se ainda não iniciou, inicia automaticamente
            startServer();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        timerHandler.removeCallbacks(timerRunnable);
    }

    private void startServer() {
        if (getActivity() == null) return;
        Intent serviceIntent = new Intent(getActivity(), MyBackendService.class);
        getActivity().startService(serviceIntent);
        ServerLogBus.startServerTimer();
        setOnlineUi();
    }

    private void stopServer() {
        if (getActivity() == null) return;
        Intent intent = new Intent(getActivity(), MyBackendService.class);
        getActivity().stopService(intent);
        ServerLogBus.stopServerTimer();
        setOfflineUi();
    }

    private void setOnlineUi() {
        String ip = NetworkUtils.getLocalIpAddress();
        tvServerInfo.setText("Servidor Online");
        tvServerInfo.setTextColor(Color.parseColor("#30D158"));
        String port = Environment.getProperty("server.port", "8080");
        tvServerAddress.setText("Acesse: http://" + ip + ":" + port);
        ivStatusIcon.setColorFilter(Color.parseColor("#30D158"));
        btnStart.setVisibility(View.GONE);
        btnStop.setVisibility(View.VISIBLE);
    }

    private void setOfflineUi() {
        tvServerInfo.setText("Servidor Desligado");
        tvServerInfo.setTextColor(Color.parseColor("#FF453A"));
        tvServerAddress.setText("Toque abaixo para ligar");
        ivStatusIcon.setColorFilter(Color.parseColor("#FF453A"));
        btnStop.setVisibility(View.GONE);
        btnStart.setVisibility(View.VISIBLE);
    }

    private void updateMetrics() {
        if (tvTotalRequestsCount != null) {
            tvTotalRequestsCount.setText(String.valueOf(ServerLogBus.getRequestCount()));
        }
        if (tvUptimeText != null) {
            long startTime = ServerLogBus.getServerStartTime();
            if (startTime > 0) {
                long diff = (System.currentTimeMillis() - startTime) / 1000;
                long hours = diff / 3600;
                long minutes = (diff % 3600) / 60;
                long seconds = diff % 60;
                tvUptimeText.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
            } else {
                tvUptimeText.setText("00:00:00");
            }
        }
    }
}
