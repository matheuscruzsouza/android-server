package com.github.matheuscruzsouza.androidserver.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
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

import java.util.List;

public class SettingsFragment extends Fragment implements ServerLogBus.LogListener {

    private ImageView ivSettingsStatusIcon;
    private TextView tvSettingsServerStatus;
    private TextView tvSettingsServerIp;
    private Button btnSettingsStartServer;
    private Button btnSettingsStopServer;

    private ScrollView svSettingsLogs;
    private TextView tvSettingsLogsOutput;
    private TextView btnSettingsClearLogs;
    private TextView tvPort;
    private TextView tvMdns;
    private TextView tvLocalTunnelStatus;
    private TextView btnLocalTunnelToggle;
    private TextView tvLocalTunnelSubdomain;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        ivSettingsStatusIcon = view.findViewById(R.id.ivSettingsStatusIcon);
        tvSettingsServerStatus = view.findViewById(R.id.tvSettingsServerStatus);
        tvSettingsServerIp = view.findViewById(R.id.tvSettingsServerIp);
        btnSettingsStartServer = view.findViewById(R.id.btnSettingsStartServer);
        btnSettingsStopServer = view.findViewById(R.id.btnSettingsStopServer);

        svSettingsLogs = view.findViewById(R.id.svSettingsLogs);
        tvSettingsLogsOutput = view.findViewById(R.id.tvSettingsLogsOutput);
        btnSettingsClearLogs = view.findViewById(R.id.btnSettingsClearLogs);

        btnSettingsStartServer.setOnClickListener(v -> startServer());
        btnSettingsStopServer.setOnClickListener(v -> stopServer());

        if (btnSettingsClearLogs != null) {
            btnSettingsClearLogs.setOnClickListener(v -> {
                ServerLogBus.clearLogs();
                tvSettingsLogsOutput.setText("[SYSTEM] Logs limpos.\n[SYSTEM] Aguardando tráfego HTTP...");
            });
        }

        tvPort = view.findViewById(R.id.tvConfigPort);
        tvMdns = view.findViewById(R.id.tvConfigMdns);
        tvLocalTunnelStatus = view.findViewById(R.id.tvConfigLocalTunnelStatus);
        btnLocalTunnelToggle = view.findViewById(R.id.btnConfigLocalTunnelToggle);
        tvLocalTunnelSubdomain = view.findViewById(R.id.tvConfigLocalTunnelSubdomain);

        TextView tvCors = view.findViewById(R.id.tvConfigCors);
        TextView tvConfigSsl = view.findViewById(R.id.tvConfigSsl);
        TextView tvDeviceModel = view.findViewById(R.id.tvDeviceModel);
        TextView tvAndroidVersion = view.findViewById(R.id.tvAndroidVersion);
        View rowPort = view.findViewById(R.id.rowConfigPort);
        View btnEditPort = view.findViewById(R.id.btnConfigEditPort);
        View rowLtToggle = view.findViewById(R.id.rowConfigLocalTunnelToggle);
        View rowLtSubdomain = view.findViewById(R.id.rowConfigLocalTunnelSubdomain);
        View btnEditSubdomain = view.findViewById(R.id.btnConfigEditSubdomain);

        updateConfigViews();

        View.OnClickListener editPortListener = v -> showChangePortDialog();
        if (rowPort != null) rowPort.setOnClickListener(editPortListener);
        if (btnEditPort != null) btnEditPort.setOnClickListener(editPortListener);

        View.OnClickListener toggleLtListener = v -> toggleLocalTunnel();
        if (rowLtToggle != null) rowLtToggle.setOnClickListener(toggleLtListener);
        if (btnLocalTunnelToggle != null) btnLocalTunnelToggle.setOnClickListener(toggleLtListener);

        View.OnClickListener editSubdomainListener = v -> showChangeSubdomainDialog();
        if (rowLtSubdomain != null) rowLtSubdomain.setOnClickListener(editSubdomainListener);
        if (btnEditSubdomain != null) btnEditSubdomain.setOnClickListener(editSubdomainListener);

        String corsAllowed = Environment.getProperty("nano.cors.allowed-origins", "*");
        tvCors.setText("Permitido: " + corsAllowed);

        boolean sslOn = Boolean.parseBoolean(Environment.getProperty("server.ssl.enabled", "false"));
        String alias = Environment.getProperty("server.ssl.key-alias", "s4-ssl");
        if (tvConfigSsl != null) {
            tvConfigSsl.setText(sslOn ? "Ativo (" + alias + " • PKCS12)" : "Desabilitado");
        }

        tvDeviceModel.setText("Modelo: " + Build.MANUFACTURER + " " + Build.MODEL);
        tvAndroidVersion.setText("Android OS: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        ServerLogBus.addListener(this);
        renderCurrentLogs();
        if (ServerLogBus.getServerStartTime() > 0) {
            setOnlineUi();
        } else {
            setOfflineUi();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        ServerLogBus.removeListener(this);
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
        if (tvSettingsServerStatus == null) return;
        tvSettingsServerStatus.setText("Servidor Online");
        tvSettingsServerStatus.setTextColor(Color.parseColor("#30D158"));
        String mdnsUrl = getContext() != null ? ServerConfig.getMdnsUrl(getContext()) : "http://s4.local:9090";
        tvSettingsServerIp.setText(mdnsUrl);
        ivSettingsStatusIcon.setColorFilter(Color.parseColor("#30D158"));
        btnSettingsStartServer.setVisibility(View.GONE);
        btnSettingsStopServer.setVisibility(View.VISIBLE);
        updateConfigViews();
    }

    private void setOfflineUi() {
        if (tvSettingsServerStatus == null) return;
        tvSettingsServerStatus.setText("Servidor Desligado");
        tvSettingsServerStatus.setTextColor(Color.parseColor("#FF453A"));
        tvSettingsServerIp.setText("Toque abaixo para ligar");
        ivSettingsStatusIcon.setColorFilter(Color.parseColor("#FF453A"));
        btnSettingsStopServer.setVisibility(View.GONE);
        btnSettingsStartServer.setVisibility(View.VISIBLE);
        updateConfigViews();
    }

    private void updateConfigViews() {
        Context context = getContext();
        int port = ServerConfig.getServerPort(context);
        if (tvPort != null) {
            tvPort.setText(port + " (TCP) • Toque para alterar");
        }
        if (tvMdns != null) {
            String mdnsName = Environment.getProperty("nano.nsd.name", "s4");
            String mdnsType = Environment.getProperty("nano.nsd.type", "_http._tcp.");
            tvMdns.setText(mdnsName + ".local (" + mdnsType + ")");
        }

        boolean ltEnabled = ServerConfig.isLocalTunnelEnabled(context);
        String ltSubdomain = ServerConfig.getLocalTunnelSubdomain(context);
        MyBackendService backend = MyBackendService.getInstance();
        String publicUrl = (backend != null && backend.getLocalTunnel() != null)
                ? backend.getLocalTunnel().getPublicUrl()
                : null;

        if (tvLocalTunnelStatus != null && btnLocalTunnelToggle != null) {
            if (ltEnabled) {
                btnLocalTunnelToggle.setText("ON");
                btnLocalTunnelToggle.setTextColor(Color.parseColor("#30D158"));
                if (publicUrl != null && !publicUrl.isEmpty()) {
                    tvLocalTunnelStatus.setText("Ativo: " + publicUrl);
                } else if (ServerLogBus.getServerStartTime() > 0) {
                    tvLocalTunnelStatus.setText("Ativado • Conectando ao broker...");
                } else {
                    tvLocalTunnelStatus.setText("Ativado • Conectará ao ligar o servidor");
                }
            } else {
                btnLocalTunnelToggle.setText("OFF");
                btnLocalTunnelToggle.setTextColor(Color.parseColor("#8E8E93"));
                tvLocalTunnelStatus.setText("Desativado • Toque para ativar");
            }
        }

        if (tvLocalTunnelSubdomain != null) {
            if (ltSubdomain == null || ltSubdomain.trim().isEmpty()) {
                tvLocalTunnelSubdomain.setText("Aleatório (loca.lt) • Toque para alterar");
            } else {
                tvLocalTunnelSubdomain.setText(ltSubdomain + ".loca.lt • Toque para alterar");
            }
        }
    }

    private void toggleLocalTunnel() {
        Context context = getContext();
        if (context == null) return;
        boolean newState = !ServerConfig.isLocalTunnelEnabled(context);
        ServerConfig.setLocalTunnelEnabled(context, newState);
        updateConfigViews();

        MyBackendService backend = MyBackendService.getInstance();
        if (backend != null && ServerLogBus.getServerStartTime() > 0) {
            backend.restartLocalTunnel();
            Toast.makeText(context, newState ? "Iniciando LocalTunnel..." : "LocalTunnel desativado.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, newState ? "LocalTunnel ativado (iniciará com o servidor)." : "LocalTunnel desativado.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showChangeSubdomainDialog() {
        Context context = getContext();
        if (context == null) return;
        String currentSubdomain = ServerConfig.getLocalTunnelSubdomain(context);

        EditText input = createDarkDialogEditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(currentSubdomain);
        input.setSelection(input.getText().length());
        input.setHint("Ex: meu-s4-server (vazio = aleatório)");

        new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Subdomínio LocalTunnel")
                .setMessage("Informe o subdomínio desejado (letras, números e hífens). Deixe em branco para gerar um subdomínio aleatório:")
                .setView(wrapDialogInput(context, input))
                .setPositiveButton("Salvar", (dialog, which) -> {
                    String raw = input.getText().toString().trim().toLowerCase();
                    if (!raw.isEmpty() && !raw.matches("^[a-z0-9-]+$")) {
                        Toast.makeText(context, "Subdomínio inválido! Use apenas letras, números e hífens.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    ServerConfig.setLocalTunnelSubdomain(context, raw);
                    updateConfigViews();

                    MyBackendService backend = MyBackendService.getInstance();
                    if (backend != null && ServerLogBus.getServerStartTime() > 0 && ServerConfig.isLocalTunnelEnabled(context)) {
                        Toast.makeText(context, "Reconectando LocalTunnel com novo subdomínio...", Toast.LENGTH_SHORT).show();
                        backend.restartLocalTunnel();
                    } else {
                        Toast.makeText(context, "Subdomínio salvo: " + (raw.isEmpty() ? "Aleatório" : raw), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showChangePortDialog() {
        Context context = getContext();
        if (context == null) return;
        int currentPort = ServerConfig.getServerPort(context);

        EditText input = createDarkDialogEditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(currentPort));
        input.setSelection(input.getText().length());
        input.setHint("Ex: 9090");

        new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Definir Porta do Servidor")
                .setMessage("Informe a nova porta TCP desejada (1024 a 65535):")
                .setView(wrapDialogInput(context, input))
                .setPositiveButton("Salvar", (dialog, which) -> {
                    String val = input.getText().toString().trim();
                    try {
                        int newPort = Integer.parseInt(val);
                        if (newPort < 1024 || newPort > 65535) {
                            Toast.makeText(context, "Porta inválida! Escolha entre 1024 e 65535.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        ServerConfig.setServerPort(context, newPort);
                        updateConfigViews();

                        if (ServerLogBus.getServerStartTime() > 0) {
                            Toast.makeText(context, "Reiniciando servidor na porta " + newPort + "...", Toast.LENGTH_SHORT).show();
                            stopServer();
                            startServer();
                        } else {
                            Toast.makeText(context, "Porta alterada para " + newPort + ". Será aplicada ao ligar.", Toast.LENGTH_SHORT).show();
                            setOfflineUi();
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(context, "Valor numérico inválido!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private EditText createDarkDialogEditText(Context context) {
        EditText input = new EditText(context);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.parseColor("#8E8E93"));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.parseColor("#2C2C2E"));
        float density = context.getResources().getDisplayMetrics().density;
        bg.setCornerRadius(10 * density);
        bg.setStroke((int) (1 * density), Color.parseColor("#3A3A3C"));
        input.setBackground(bg);
        int padH = (int) (14 * density);
        int padV = (int) (12 * density);
        input.setPadding(padH, padV, padH, padV);
        return input;
    }

    private View wrapDialogInput(Context context, EditText input) {
        android.widget.FrameLayout container = new android.widget.FrameLayout(context);
        float density = context.getResources().getDisplayMetrics().density;
        int marginH = (int) (20 * density);
        int marginT = (int) (8 * density);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(marginH, marginT, marginH, 0);
        input.setLayoutParams(params);
        container.addView(input);
        return container;
    }

    private void renderCurrentLogs() {
        if (tvSettingsLogsOutput == null) return;
        List<String> list = ServerLogBus.getAllLogs();
        if (list.isEmpty()) {
            tvSettingsLogsOutput.setText("[SYSTEM] S4 Server Logger ativo...\n[SYSTEM] Aguardando tráfego HTTP...");
        } else {
            StringBuilder sb = new StringBuilder();
            for (String s : list) {
                sb.append(s).append("\n");
            }
            tvSettingsLogsOutput.setText(sb.toString());
            scrollToBottom();
        }
    }

    @Override
    public void onNewLog(String logEntry) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (tvSettingsLogsOutput != null) {
                tvSettingsLogsOutput.append(logEntry + "\n");
                scrollToBottom();
            }
            if (logEntry != null && logEntry.contains("[LOCALTUNNEL]")) {
                updateConfigViews();
            }
        });
    }

    private void scrollToBottom() {
        if (svSettingsLogs != null) {
            svSettingsLogs.post(() -> svSettingsLogs.fullScroll(View.FOCUS_DOWN));
        }
    }
}
