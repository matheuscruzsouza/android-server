package com.github.matheuscruzsouza.androidserver.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.matheuscruzsouza.androidserver.R;
import com.github.matheuscruzsouza.androidserver.UserRegistrationActivity;
import com.github.matheuscruzsouza.androidserver.adapter.UserAdminAdapter;
import com.github.matheuscruzsouza.androidserver.model.IamUser;
import com.github.matheuscruzsouza.androidserver.service.IamService;

import java.util.List;

public class UsersFragment extends Fragment {

    private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private RecyclerView rvUsers;
    private TextView tvEmptyUsers;
    private UserAdminAdapter adapter;
    private IamService iamService;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_users, container, false);

        rvUsers = view.findViewById(R.id.rvUsers);
        tvEmptyUsers = view.findViewById(R.id.tvEmptyUsers);
        Button btnCreateUser = view.findViewById(R.id.btnCreateUser);

        if (getContext() != null) {
            iamService = new IamService(getContext());
        } else {
            iamService = new IamService();
        }

        rvUsers.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new UserAdminAdapter(new UserAdminAdapter.OnUserActionListener() {
            @Override
            public void onDeleteUser(IamUser user) {
                confirmDeleteUser(user);
            }

            @Override
            public void onManageUser(IamUser user) {
                showManageUserDialog(user);
            }
        });
        rvUsers.setAdapter(adapter);

        btnCreateUser.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), UserRegistrationActivity.class);
            startActivity(intent);
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUsers();
    }

    private void loadUsers() {
        if (iamService == null) return;
        executor.execute(() -> {
            List<IamUser> list = iamService.listUsers();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    if (list == null || list.isEmpty()) {
                        tvEmptyUsers.setVisibility(View.VISIBLE);
                        rvUsers.setVisibility(View.GONE);
                    } else {
                        tvEmptyUsers.setVisibility(View.GONE);
                        rvUsers.setVisibility(View.VISIBLE);
                        adapter.updateData(list);
                    }
                });
            }
        });
    }

    private void confirmDeleteUser(IamUser user) {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Excluir Credencial IAM")
                .setMessage("Deseja realmente remover o usuário \"" + user.getName() + "\" (" + user.getAccessKey() + ")?")
                .setPositiveButton("Excluir", (dialog, which) -> {
                    executor.execute(() -> {
                        iamService.deleteUser(user.getAccessKey());
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (!isAdded()) return;
                                Toast.makeText(getContext(), "Credencial removida", Toast.LENGTH_SHORT).show();
                                loadUsers();
                            });
                        }
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showManageUserDialog(IamUser user) {
        if (getContext() == null) return;

        String[] actions = new String[]{
                "Alterar Política (" + user.getPolicy() + ")",
                user.isEnabled() ? "Desativar Credencial" : "Ativar Credencial",
                "Rotacionar Secret Key",
                "Copiar Secret Key"
        };

        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Configurar IAM: " + user.getName())
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showChangePolicyDialog(user);
                            break;
                        case 1:
                            toggleUserStatus(user);
                            break;
                        case 2:
                            showRotateSecretDialog(user);
                            break;
                        case 3:
                            copySecretToClipboard(user);
                            break;
                    }
                })
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void showChangePolicyDialog(IamUser user) {
        if (getContext() == null) return;
        String[] policies = new String[]{"ReadOnly", "WriteOnly", "Admin"};
        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Selecionar Nova Política")
                .setItems(policies, (dialog, which) -> {
                    String selected = policies[which];
                    executor.execute(() -> {
                        iamService.updatePolicy(user.getAccessKey(), selected);
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (!isAdded()) return;
                                Toast.makeText(getContext(), "Política alterada para " + selected, Toast.LENGTH_SHORT).show();
                                loadUsers();
                            });
                        }
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void toggleUserStatus(IamUser user) {
        boolean newState = !user.isEnabled();
        executor.execute(() -> {
            iamService.updateEnabled(user.getAccessKey(), newState);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), newState ? "Credencial ativada" : "Credencial desativada", Toast.LENGTH_SHORT).show();
                    loadUsers();
                });
            }
        });
    }

    private void showRotateSecretDialog(IamUser user) {
        if (getContext() == null) return;
        android.widget.EditText input = new android.widget.EditText(getContext());
        input.setHint("Nova Secret Key");
        input.setTextColor(android.graphics.Color.WHITE);
        input.setHintTextColor(android.graphics.Color.parseColor("#8E8E93"));
        input.setText("secret_" + java.util.UUID.randomUUID().toString().substring(0, 12));

        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Rotacionar Chave Secreta")
                .setMessage("Defina a nova Secret Key para " + user.getAccessKey() + ":")
                .setView(input)
                .setPositiveButton("Salvar", (dialog, which) -> {
                    String newSecret = input.getText().toString().trim();
                    if (!newSecret.isEmpty()) {
                        executor.execute(() -> {
                            iamService.updateSecretKey(user.getAccessKey(), newSecret);
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    if (!isAdded()) return;
                                    Toast.makeText(getContext(), "Secret Key rotacionada com sucesso", Toast.LENGTH_SHORT).show();
                                    loadUsers();
                                });
                            }
                        });
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void copySecretToClipboard(IamUser user) {
        if (getContext() == null) return;
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("IAM Secret Key", user.getSecretKey());
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "Secret Key copiada para a área de transferência", Toast.LENGTH_SHORT).show();
        }
    }
}
