package com.dubaisolve.speakmate;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.view.View;

import com.dubaisolve.speakmate.databinding.ActivityHelpBinding;

import java.util.List;

public class HelpActivity extends AppCompatActivity {

    private ActivityHelpBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityHelpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Toolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        CollapsingToolbarLayout toolBarLayout = binding.toolbarLayout;
        toolBarLayout.setTitle(getTitle());

        FloatingActionButton fab = binding.fab;
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String toEmail = "dubaisolve@gmail.com"; // recipient's email
                String subject = "Help Request"; // email subject
                String body = "Dear Support,\n\nPlease send your response to: " + toEmail; // email body with recipient's email

                Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + toEmail));
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
                emailIntent.putExtra(Intent.EXTRA_TEXT, body);

                // Try to open with the default email client
                if (emailIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(emailIntent);
                } else {
                    // If that fails, try to open specifically with Gmail
                    emailIntent.setPackage("com.google.android.gm");
                    if (emailIntent.resolveActivity(getPackageManager()) != null) {
                        startActivity(emailIntent);
                    } else {
                        // If that also fails, use the standard Android share dialog
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("text/plain");
                        shareIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
                        shareIntent.putExtra(Intent.EXTRA_TEXT, body);
                        if (shareIntent.resolveActivity(getPackageManager()) != null) {
                            startActivity(Intent.createChooser(shareIntent, "Share via"));
                        } else {
                            // If no suitable apps are found, show an error message
                            Snackbar.make(view, "No suitable apps found for sharing", Snackbar.LENGTH_LONG).show();
                        }
                    }
                }
            }
        });

    }
}