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
import com.github.matheuscruzsouza.androidserver.model.User;
import com.github.matheuscruzsouza.androidserver.service.UserService;

import java.util.List;

public class UsersFragment extends Fragment {

    private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private RecyclerView rvUsers;
    private TextView tvEmptyUsers;
    private UserAdminAdapter adapter;
    private UserService userService;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_users, container, false);

        rvUsers = view.findViewById(R.id.rvUsers);
        tvEmptyUsers = view.findViewById(R.id.tvEmptyUsers);
        Button btnCreateUser = view.findViewById(R.id.btnCreateUser);

        if (getContext() != null) {
            userService = new UserService(getContext());
        } else {
            userService = new UserService();
        }

        rvUsers.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new UserAdminAdapter(this::confirmDeleteUser);
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
        if (userService == null) return;
        executor.execute(() -> {
            List<User> list = userService.getUsers();
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

    private void confirmDeleteUser(User user) {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Excluir Usuário")
                .setMessage("Deseja realmente remover @" + user.getUsername() + "? Todos os arquivos deste usuário também serão removidos.")
                .setPositiveButton("Excluir", (dialog, which) -> {
                    executor.execute(() -> {
                        userService.deleteUser(user);
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (!isAdded()) return;
                                Toast.makeText(getContext(), "Usuário removido", Toast.LENGTH_SHORT).show();
                                loadUsers();
                            });
                        }
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
