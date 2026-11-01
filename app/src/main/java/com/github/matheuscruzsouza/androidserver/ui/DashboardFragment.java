package com.github.matheuscruzsouza.androidserver.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.matheuscruzsouza.androidserver.R;
import com.github.matheuscruzsouza.androidserver.config.ServerConfig;
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
    private TextView tvCopyUrl;
    private TextView tvMdnsAccessUrl;

    private TextView tvMemoryUsedLabel;
    private TextView tvMemoryPercent;
    private android.widget.ProgressBar pbMemoryUsage;
    private TextView tvMemoryFreeLabel;
    private TextView tvMemoryTotalLabel;

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
        tvCopyUrl = view.findViewById(R.id.tvCopyUrl);
        tvMdnsAccessUrl = view.findViewById(R.id.tvMdnsAccessUrl);

        tvMemoryUsedLabel = view.findViewById(R.id.tvMemoryUsedLabel);
        tvMemoryPercent = view.findViewById(R.id.tvMemoryPercent);
        pbMemoryUsage = view.findViewById(R.id.pbMemoryUsage);
        tvMemoryFreeLabel = view.findViewById(R.id.tvMemoryFreeLabel);
        tvMemoryTotalLabel = view.findViewById(R.id.tvMemoryTotalLabel);

        if (tvCopyUrl != null) {
            tvCopyUrl.setOnClickListener(v -> {
                if (getContext() != null) {
                    String mdnsUrl = ServerConfig.getMdnsUrl(getContext());
                    ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Server URL", mdnsUrl);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(getContext(), "URL copiada: " + mdnsUrl, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        timerHandler.post(timerRunnable);
        if (ServerLogBus.getServerStartTime() > 0) {
            setOnlineUi();
        } else {
            // Se o servidor ainda não estiver em execução, inicia automaticamente
            startServer();
        }
    }

    private void startServer() {
        if (getActivity() == null) return;
        Intent serviceIntent = new Intent(getActivity(), MyBackendService.class);
        getActivity().startService(serviceIntent);
        ServerLogBus.startServerTimer();
        setOnlineUi();
    }

    @Override
    public void onPause() {
        super.onPause();
        timerHandler.removeCallbacks(timerRunnable);
    }

    private void setOnlineUi() {
        if (tvServerInfo == null) return;
        tvServerInfo.setText("Servidor Online");
        tvServerInfo.setTextColor(Color.parseColor("#30D158"));
        String mdnsUrl = getContext() != null ? ServerConfig.getMdnsUrl(getContext()) : "http://s4.local:9090";
        tvServerAddress.setText("Acesse: " + mdnsUrl);
        if (tvMdnsAccessUrl != null) {
            tvMdnsAccessUrl.setText("🌐 " + mdnsUrl);
        }
        ivStatusIcon.setColorFilter(Color.parseColor("#30D158"));
    }

    private void setOfflineUi() {
        if (tvServerInfo == null) return;
        tvServerInfo.setText("Servidor Desligado");
        tvServerInfo.setTextColor(Color.parseColor("#FF453A"));
        tvServerAddress.setText("Ligue nas Configurações");
        if (tvMdnsAccessUrl != null) {
            String mdnsUrl = getContext() != null ? ServerConfig.getMdnsUrl(getContext()) : "http://s4.local:9090";
            tvMdnsAccessUrl.setText("🌐 " + mdnsUrl);
        }
        ivStatusIcon.setColorFilter(Color.parseColor("#FF453A"));
    }

    private void updateMetrics() {
        if (ServerLogBus.getServerStartTime() > 0) {
            setOnlineUi();
        } else {
            setOfflineUi();
        }

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

        updateMemoryMetrics();
    }

    private void updateMemoryMetrics() {
        Runtime rt = Runtime.getRuntime();
        long totalMemory = rt.totalMemory();
        long freeMemory = rt.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        long usedMb = usedMemory / (1024 * 1024);
        long freeMb = freeMemory / (1024 * 1024);
        long totalMb = totalMemory / (1024 * 1024);

        int percent = totalMb > 0 ? (int) ((usedMb * 100) / totalMb) : 0;
        if (percent > 100) percent = 100;

        if (tvMemoryUsedLabel != null) {
            tvMemoryUsedLabel.setText(usedMb + " MB usados");
        }
        if (tvMemoryPercent != null) {
            tvMemoryPercent.setText(percent + "%");
        }
        if (pbMemoryUsage != null) {
            pbMemoryUsage.setProgress(percent);
            if (percent > 85) {
                pbMemoryUsage.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF453A")));
            } else if (percent > 65) {
                pbMemoryUsage.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF9F0A")));
            } else {
                pbMemoryUsage.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#0A84FF")));
            }
        }
        if (tvMemoryFreeLabel != null) {
            tvMemoryFreeLabel.setText("Livre: " + freeMb + " MB");
        }
        if (tvMemoryTotalLabel != null) {
            tvMemoryTotalLabel.setText("Total Alocado: " + totalMb + " MB");
        }
    }
}
