package com.github.matheuscruzsouza.androidserver;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.github.matheuscruzsouza.nanospring.server.Environment;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import com.google.gson.Gson;
import com.github.matheuscruzsouza.androidserver.model.User;

public class UserRegistrationActivity extends AppCompatActivity {

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_registration);

        EditText etUsername = findViewById(R.id.etUsername);
        EditText etName = findViewById(R.id.etName);
        Button btnSubmit = findViewById(R.id.btnSubmit);
        Button btnBack = findViewById(R.id.btnBack);
        Environment.init(this);
        TextView tvResult = findViewById(R.id.tvResult);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        btnSubmit.setOnClickListener(v -> {
            String username = etUsername.getText().toString();
            String name = etName.getText().toString();

            if (username.isEmpty() || name.isEmpty()) {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show();
                return;
            }

            User user = new User(username, name);
            String jsonPayload = gson.toJson(user);

            executor.execute(() -> {
                try {
                    String ip = NetworkUtils.getLocalIpAddress();
                    String port = Environment.getProperty("server.port", "8080");
                    URL url = new URL("http://" + ip + ":" + port + "/api/users");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);

                    try (OutputStream os = conn.getOutputStream()) {
                        byte[] input = jsonPayload.getBytes("utf-8");
                        os.write(input, 0, input.length);
                    }

                    int code = conn.getResponseCode();
                    boolean isSuccess = (code >= 200 && code < 300);
                    
                    StringBuilder responseStr = new StringBuilder();
                    java.io.InputStream stream = isSuccess ? conn.getInputStream() : conn.getErrorStream();
                    if (stream != null) {
                        try (BufferedReader br = new BufferedReader(
                                new InputStreamReader(stream, "utf-8"))) {
                            String responseLine;
                            while ((responseLine = br.readLine()) != null) {
                                responseStr.append(responseLine.trim());
                            }
                        }
                    }

                    runOnUiThread(() -> {
                        if (isSuccess) {
                            tvResult.setText("Sucesso! Usuário cadastrado:\n" + responseStr.toString());
                            etUsername.setText("");
                            etName.setText("");
                        } else {
                            tvResult.setText("Erro HTTP " + code + ":\n" + responseStr.toString());
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        tvResult.setText("Falha na conexão: " + e.getMessage());
                    });
                }
            });
        });
    }
}
