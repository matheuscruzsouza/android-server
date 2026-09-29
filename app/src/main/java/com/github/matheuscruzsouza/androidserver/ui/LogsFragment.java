package com.github.matheuscruzsouza.androidserver.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.matheuscruzsouza.androidserver.R;
import com.github.matheuscruzsouza.androidserver.log.ServerLogBus;

import java.util.List;

public class LogsFragment extends Fragment implements ServerLogBus.LogListener {

    private TextView tvTerminalOutput;
    private ScrollView svLogs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_logs, container, false);

        tvTerminalOutput = view.findViewById(R.id.tvTerminalOutput);
        svLogs = view.findViewById(R.id.svLogs);
        Button btnClear = view.findViewById(R.id.btnClearLogs);

        btnClear.setOnClickListener(v -> {
            ServerLogBus.clearLogs();
            tvTerminalOutput.setText("[SYSTEM] Logs limpos.\n[SYSTEM] Aguardando tráfego HTTP...");
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        ServerLogBus.addListener(this);
        renderCurrentLogs();
    }

    @Override
    public void onPause() {
        super.onPause();
        ServerLogBus.removeListener(this);
    }

    private void renderCurrentLogs() {
        List<String> list = ServerLogBus.getAllLogs();
        if (list.isEmpty()) {
            tvTerminalOutput.setText("[SYSTEM] Nano-Spring Server Logger ativo...\n[SYSTEM] Aguardando tráfego HTTP...");
        } else {
            StringBuilder sb = new StringBuilder();
            for (String s : list) {
                sb.append(s).append("\n");
            }
            tvTerminalOutput.setText(sb.toString());
            scrollToBottom();
        }
    }

    @Override
    public void onNewLog(String logEntry) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (tvTerminalOutput != null) {
                tvTerminalOutput.append(logEntry + "\n");
                scrollToBottom();
            }
        });
    }

    private void scrollToBottom() {
        if (svLogs != null) {
            svLogs.post(() -> svLogs.fullScroll(View.FOCUS_DOWN));
        }
    }
}
