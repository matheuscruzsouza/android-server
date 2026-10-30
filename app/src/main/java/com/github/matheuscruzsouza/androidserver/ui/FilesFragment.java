package com.github.matheuscruzsouza.androidserver.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.matheuscruzsouza.androidserver.R;
import com.github.matheuscruzsouza.androidserver.adapter.FileAdminAdapter;
import com.github.matheuscruzsouza.androidserver.model.S3ObjectItem;
import com.github.matheuscruzsouza.androidserver.storage.StorageManager;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class FilesFragment extends Fragment {

    private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private RecyclerView rvFiles;
    private TextView tvEmptyFiles;
    private TextView tvStorageUsage;
    private TextView tvTotalFilesCount;
    private FileAdminAdapter adapter;
    private StorageManager storageManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_files, container, false);

        rvFiles = view.findViewById(R.id.rvFiles);
        tvEmptyFiles = view.findViewById(R.id.tvEmptyFiles);
        tvStorageUsage = view.findViewById(R.id.tvStorageUsage);
        tvTotalFilesCount = view.findViewById(R.id.tvTotalFilesCount);
        Button btnRefresh = view.findViewById(R.id.btnRefreshFiles);

        if (getContext() != null) {
            storageManager = new StorageManager(getContext());
        }

        rvFiles.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new FileAdminAdapter(new FileAdminAdapter.OnFileActionListener() {
            @Override
            public void onOpenFile(S3ObjectItem file) {
                openPhysicalFile(file);
            }

            @Override
            public void onDeleteFile(S3ObjectItem file) {
                confirmDeleteFile(file);
            }
        });
        rvFiles.setAdapter(adapter);

        btnRefresh.setOnClickListener(v -> loadFiles());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadFiles();
    }

    private void loadFiles() {
        if (storageManager == null) return;
        executor.execute(() -> {
            List<S3ObjectItem> list = storageManager.listAllObjects();
            long totalBytes = 0;
            if (list != null && !list.isEmpty()) {
                for (S3ObjectItem item : list) {
                    totalBytes += item.getSize();
                }
            }
            final long finalBytes = totalBytes;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    if (list != null && !list.isEmpty()) {
                        tvStorageUsage.setText(formatStorage(finalBytes));
                        tvTotalFilesCount.setText(list.size() + " objetos no S3");
                        tvEmptyFiles.setVisibility(View.GONE);
                        rvFiles.setVisibility(View.VISIBLE);
                        adapter.updateData(list);
                    } else {
                        tvStorageUsage.setText("0.0 MB");
                        tvTotalFilesCount.setText("0 objetos no S3");
                        tvEmptyFiles.setVisibility(View.VISIBLE);
                        rvFiles.setVisibility(View.GONE);
                        adapter.updateData(java.util.Collections.emptyList());
                    }
                });
            }
        });
    }

    private String formatStorage(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.getDefault(), "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private void openPhysicalFile(S3ObjectItem item) {
        File f = new File(item.getAbsolutePath());
        if (!f.exists()) {
            Toast.makeText(getContext(), "Arquivo não existe no disco", Toast.LENGTH_SHORT).show();
            return;
        }

        if (getContext() == null) return;

        try {
            Uri uri = FileProvider.getUriForFile(
                    getContext(),
                    getContext().getPackageName() + ".fileprovider",
                    f
            );
            String mimeType = com.github.matheuscruzsouza.androidserver.storage.FileUtils.getMimeType(f.getName());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Abrir Arquivo S3"));
        } catch (Exception e) {
            Toast.makeText(getContext(), "Não foi possível abrir o arquivo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDeleteFile(S3ObjectItem item) {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Excluir Objeto S3")
                .setMessage("Deseja apagar '" + item.getKey() + "' do bucket '" + item.getBucket() + "'?")
                .setPositiveButton("Excluir", (dialog, which) -> {
                    executor.execute(() -> {
                        File f = new File(item.getAbsolutePath());
                        boolean deleted = f.exists() && f.delete();
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (!isAdded()) return;
                                Toast.makeText(getContext(), deleted ? "Objeto S3 removido" : "Erro ao remover arquivo", Toast.LENGTH_SHORT).show();
                                loadFiles();
                            });
                        }
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
