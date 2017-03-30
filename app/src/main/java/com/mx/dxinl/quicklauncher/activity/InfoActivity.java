package com.mx.dxinl.quicklauncher.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.mx.dxinl.quicklauncher.R;

/**
 * Created by Deng Xinliang on 2016/8/5.
 *
 * Activity that start by service and show mDialog.
 */
public class InfoActivity extends AppCompatActivity {

	private AlertDialog mDialog;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_info);

		mDialog = new AlertDialog.Builder(this)
				.setMessage(getString(R.string.accessibility_request))
				.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int i) {
						startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
						mDialog.dismiss();
						finish();
					}
				})
				.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int i) {
						mDialog.dismiss();
						finish();
					}
				})
				.setOnCancelListener(new DialogInterface.OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialogInterface) {
						mDialog.dismiss();
						finish();
					}
				})
				.create();
		mDialog.show();
	}
}