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
import com.github.matheuscruzsouza.androidserver.model.UserFile;
import com.github.matheuscruzsouza.androidserver.service.FileService;

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
    private FileService fileService;

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
            fileService = new FileService(getContext());
        } else {
            fileService = new FileService();
        }

        rvFiles.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new FileAdminAdapter(new FileAdminAdapter.OnFileActionListener() {
            @Override
            public void onOpenFile(UserFile file) {
                openPhysicalFile(file);
            }

            @Override
            public void onDeleteFile(UserFile file) {
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
        if (fileService == null) return;
        executor.execute(() -> {
            List<UserFile> list = fileService.getAllFiles();
            long totalBytes = 0;
            if (list != null && !list.isEmpty()) {
                for (UserFile uf : list) {
                    File f = new File(uf.getFilePath());
                    if (f.exists()) totalBytes += f.length();
                }
            }
            final long finalBytes = totalBytes;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    if (list != null && !list.isEmpty()) {
                        tvStorageUsage.setText(formatStorage(finalBytes));
                        tvTotalFilesCount.setText(list.size() + " arquivos salvos");
                        tvEmptyFiles.setVisibility(View.GONE);
                        rvFiles.setVisibility(View.VISIBLE);
                        adapter.updateData(list);
                    } else {
                        tvStorageUsage.setText("0.0 MB");
                        tvTotalFilesCount.setText("0 arquivos salvos");
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

    private void openPhysicalFile(UserFile userFile) {
        File f = new File(userFile.getFilePath());
        if (!f.exists()) {
            Toast.makeText(getContext(), "Arquivo não existe no disco", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.fromFile(f);
            intent.setDataAndType(uri, "*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Abrir Arquivo"));
        } catch (Exception e) {
            Toast.makeText(getContext(), "Não foi possível abrir o arquivo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDeleteFile(UserFile userFile) {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Excluir Arquivo")
                .setMessage("Deseja apagar o arquivo '" + userFile.getFileName() + "' do armazenamento e do banco de dados?")
                .setPositiveButton("Excluir", (dialog, which) -> {
                    executor.execute(() -> {
                        File f = new File(userFile.getFilePath());
                        if (f.exists()) {
                            f.delete();
                        }
                        fileService.deleteFile(userFile);
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (!isAdded()) return;
                                Toast.makeText(getContext(), "Arquivo removido", Toast.LENGTH_SHORT).show();
                                loadFiles();
                            });
                        }
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
