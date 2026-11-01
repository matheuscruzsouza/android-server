package com.github.matheuscruzsouza.androidserver;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.github.matheuscruzsouza.androidserver.service.IamService;
import com.github.matheuscruzsouza.nanospring.server.Environment;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserRegistrationActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private IamService iamService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_registration);

        Environment.init(this);
        iamService = new IamService(this);

        EditText etName = findViewById(R.id.etName);
        EditText etAccessKey = findViewById(R.id.etAccessKey);
        EditText etSecretKey = findViewById(R.id.etSecretKey);
        Spinner spPolicy = findViewById(R.id.spPolicy);
        Button btnSubmit = findViewById(R.id.btnSubmit);
        Button btnBack = findViewById(R.id.btnBack);
        TextView tvResult = findViewById(R.id.tvResult);

        String[] policies = new String[]{"ReadOnly", "WriteOnly", "Admin"};
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, policies);
        spPolicy.setAdapter(spinnerAdapter);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        btnSubmit.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String accessKey = etAccessKey.getText().toString().trim();
            String secretKey = etSecretKey.getText().toString().trim();
            String policy = spPolicy.getSelectedItem() != null ? spPolicy.getSelectedItem().toString() : "ReadOnly";

            if (name.isEmpty() || accessKey.isEmpty()) {
                Toast.makeText(this, "Preencha o nome e o usuário de login", Toast.LENGTH_SHORT).show();
                return;
            }

            final String finalSecretKey = secretKey.isEmpty()
                    ? "secret_" + accessKey.toLowerCase().replaceAll("[^a-z0-9]", "") + "_" + System.currentTimeMillis()
                    : secretKey;

            executor.execute(() -> {
                boolean success = iamService.createUser(name, accessKey, finalSecretKey, policy);
                runOnUiThread(() -> {
                    if (success) {
                        tvResult.setText("Sucesso! Credencial IAM criada com sucesso:\nAccessKey: " + accessKey + " (" + policy + ")");
                        etName.setText("");
                        etAccessKey.setText("");
                        etSecretKey.setText("");
                    } else {
                        tvResult.setText("Erro ao salvar credencial. Verifique se o AccessKey já existe.");
                    }
                });
            });
        });
    }
}
