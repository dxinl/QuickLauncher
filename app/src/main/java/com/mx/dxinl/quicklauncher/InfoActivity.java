package com.mx.dxinl.quicklauncher;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

/**
 * Created by Deng Xinliang on 2016/8/5.
 */
public class InfoActivity extends AppCompatActivity {

    private AlertDialog dialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);

        dialog = new AlertDialog.Builder(this)
                .setMessage("请在设置界面给予本应用权限")
                .setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        finish();
                    }
                })
                .setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialog.dismiss();
                        finish();
                    }
                })
                .setCancelable(false)
                .create();
        dialog.show();
    }
}
