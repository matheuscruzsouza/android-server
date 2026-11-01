package com.github.matheuscruzsouza.androidserver;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.github.matheuscruzsouza.androidserver.ui.DashboardFragment;
import com.github.matheuscruzsouza.androidserver.ui.FilesFragment;
import com.github.matheuscruzsouza.androidserver.ui.LogsFragment;
import com.github.matheuscruzsouza.androidserver.ui.SettingsFragment;
import com.github.matheuscruzsouza.androidserver.ui.UsersFragment;
import com.github.matheuscruzsouza.nanospring.server.Environment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private final DashboardFragment dashboardFragment = new DashboardFragment();
    private final UsersFragment usersFragment = new UsersFragment();
    private final FilesFragment filesFragment = new FilesFragment();
    private final LogsFragment logsFragment = new LogsFragment();
    private final SettingsFragment settingsFragment = new SettingsFragment();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Environment.init(this);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);

        // Carrega o Dashboard inicialmente
        if (savedInstanceState == null) {
            loadFragment(dashboardFragment);
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_dashboard) {
                loadFragment(dashboardFragment);
                return true;
            } else if (id == R.id.nav_users) {
                loadFragment(usersFragment);
                return true;
            } else if (id == R.id.nav_files) {
                loadFragment(filesFragment);
                return true;
            } else if (id == R.id.nav_logs) {
                loadFragment(logsFragment);
                return true;
            } else if (id == R.id.nav_settings) {
                loadFragment(settingsFragment);
                return true;
            }
            return false;
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }
}
