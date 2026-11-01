package com.github.matheuscruzsouza.androidserver;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.matheuscruzsouza.androidserver.adapter.FileAdminAdapter;
import com.github.matheuscruzsouza.androidserver.model.IamUser;
import com.github.matheuscruzsouza.androidserver.model.S3ObjectItem;
import com.github.matheuscruzsouza.androidserver.service.IamService;
import com.github.matheuscruzsouza.androidserver.storage.FileUtils;
import com.github.matheuscruzsouza.androidserver.storage.StorageManager;
import com.github.matheuscruzsouza.nanospring.server.Environment;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_ACCESS_KEY = "extra_access_key";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private IamService iamService;
    private StorageManager storageManager;

    private String userAccessKey;
    private IamUser currentUser;

    private TextView tvUserDetailsTitle;
    private TextView tvDetailUserName;
    private TextView tvDetailUserAccess;
    private TextView tvDetailUserToken;
    private TextView tvUserFilesCount;
    private TextView tvEmptyUserFiles;
    private Button btnCopyToken;
    private Button btnRotateToken;
    private Button btnChangePolicy;
    private Button btnToggleEnable;
    private Button btnBackFromDetails;
    private RecyclerView rvUserDetailsFiles;
    private FileAdminAdapter fileAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_details);

        Environment.init(this);
        iamService = new IamService(this);
        storageManager = new StorageManager(this);

        userAccessKey = getIntent().getStringExtra(EXTRA_ACCESS_KEY);

        tvUserDetailsTitle = findViewById(R.id.tvUserDetailsTitle);
        tvDetailUserName = findViewById(R.id.tvDetailUserName);
        tvDetailUserAccess = findViewById(R.id.tvDetailUserAccess);
        tvDetailUserToken = findViewById(R.id.tvDetailUserToken);
        tvUserFilesCount = findViewById(R.id.tvUserFilesCount);
        tvEmptyUserFiles = findViewById(R.id.tvEmptyUserFiles);
        btnCopyToken = findViewById(R.id.btnCopyToken);
        btnRotateToken = findViewById(R.id.btnRotateToken);
        btnChangePolicy = findViewById(R.id.btnChangePolicy);
        btnToggleEnable = findViewById(R.id.btnToggleEnable);
        btnBackFromDetails = findViewById(R.id.btnBackFromDetails);
        rvUserDetailsFiles = findViewById(R.id.rvUserDetailsFiles);

        btnBackFromDetails.setOnClickListener(v -> finish());

        rvUserDetailsFiles.setLayoutManager(new LinearLayoutManager(this));
        fileAdapter = new FileAdminAdapter(new FileAdminAdapter.OnFileActionListener() {
            @Override
            public void onOpenFile(S3ObjectItem file) {
                openPhysicalFile(file);
            }

            @Override
            public void onDeleteFile(S3ObjectItem file) {
                confirmDeleteFile(file);
            }
        });
        rvUserDetailsFiles.setAdapter(fileAdapter);

        btnCopyToken.setOnClickListener(v -> copyTokenToClipboard());
        btnRotateToken.setOnClickListener(v -> rotateToken());
        btnChangePolicy.setOnClickListener(v -> showChangePolicyDialog());
        btnToggleEnable.setOnClickListener(v -> toggleEnable());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserDetailsAndFiles();
    }

    private void loadUserDetailsAndFiles() {
        if (userAccessKey == null) return;
        executor.execute(() -> {
            List<IamUser> all = iamService.listUsers();
            IamUser found = null;
            if (all != null) {
                for (IamUser u : all) {
                    if (userAccessKey.equals(u.getAccessKey())) {
                        found = u;
                        break;
                    }
                }
            }
            currentUser = found;

            if (currentUser != null && currentUser.getResetToken() == null) {
                iamService.rotateResetToken(currentUser.getAccessKey());
                List<IamUser> refreshed = iamService.listUsers();
                if (refreshed != null) {
                    for (IamUser u : refreshed) {
                        if (userAccessKey.equals(u.getAccessKey())) {
                            currentUser = u;
                            break;
                        }
                    }
                }
            }

            final IamUser user = currentUser;
            final List<S3ObjectItem> userFiles = (user != null && storageManager != null)
                    ? storageManager.listObjectsForUser(user, storageManager.getUserDefaultBucket(user))
                    : null;

            runOnUiThread(() -> {
                if (user == null) {
                    Toast.makeText(UserDetailsActivity.this, "Usuário não encontrado", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                tvUserDetailsTitle.setText(user.getName());
                tvDetailUserName.setText(user.getName());
                tvDetailUserAccess.setText(user.getAccessKey() + " • " + user.getPolicy() + (user.isEnabled() ? " (Ativo)" : " (Desativado)"));
                tvDetailUserToken.setText(user.getResetToken() != null ? user.getResetToken() : "------");
                btnToggleEnable.setText(user.isEnabled() ? "Desativar" : "Ativar");

                if (userFiles != null && !userFiles.isEmpty()) {
                    tvEmptyUserFiles.setVisibility(View.GONE);
                    rvUserDetailsFiles.setVisibility(View.VISIBLE);
                    tvUserFilesCount.setText(userFiles.size() + " arquivos");
                    fileAdapter.updateData(userFiles);
                } else {
                    tvEmptyUserFiles.setVisibility(View.VISIBLE);
                    rvUserDetailsFiles.setVisibility(View.GONE);
                    tvUserFilesCount.setText("0 arquivos");
                    fileAdapter.updateData(java.util.Collections.emptyList());
                }
            });
        });
    }

    private void copyTokenToClipboard() {
        if (currentUser == null || currentUser.getResetToken() == null) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("S4 Token", currentUser.getResetToken());
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Token '" + currentUser.getResetToken() + "' copiado!", Toast.LENGTH_SHORT).show();
        }
    }

    private void rotateToken() {
        if (currentUser == null) return;
        executor.execute(() -> {
            iamService.rotateResetToken(currentUser.getAccessKey());
            runOnUiThread(() -> {
                Toast.makeText(UserDetailsActivity.this, "Novo token gerado com sucesso!", Toast.LENGTH_SHORT).show();
                loadUserDetailsAndFiles();
            });
        });
    }

    private void showChangePolicyDialog() {
        if (currentUser == null) return;
        String[] policies = new String[]{"ReadOnly", "WriteOnly", "Admin"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Definir Política IAM")
                .setItems(policies, (dialog, which) -> {
                    String selected = policies[which];
                    executor.execute(() -> {
                        iamService.updatePolicy(currentUser.getAccessKey(), selected);
                        runOnUiThread(this::loadUserDetailsAndFiles);
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void toggleEnable() {
        if (currentUser == null) return;
        boolean newState = !currentUser.isEnabled();
        executor.execute(() -> {
            iamService.updateEnabled(currentUser.getAccessKey(), newState);
            runOnUiThread(this::loadUserDetailsAndFiles);
        });
    }

    private void openPhysicalFile(S3ObjectItem item) {
        File f = new File(item.getAbsolutePath());
        if (!f.exists()) {
            Toast.makeText(this, "Arquivo não existe no disco", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    f
            );
            String mimeType = FileUtils.getMimeType(f.getName());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Abrir Arquivo"));
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir o arquivo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDeleteFile(S3ObjectItem item) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Excluir Arquivo")
                .setMessage("Deseja apagar '" + item.getKey() + "'?")
                .setPositiveButton("Excluir", (dialog, which) -> {
                    executor.execute(() -> {
                        File f = new File(item.getAbsolutePath());
                        if (f.exists()) {
                            f.delete();
                        }
                        runOnUiThread(this::loadUserDetailsAndFiles);
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
