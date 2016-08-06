package com.mx.dxinl.quicklauncher.activity;

import android.app.Dialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.mx.dxinl.quicklauncher.R;
import com.mx.dxinl.quicklauncher.model.DatabaseHelper;
import com.mx.dxinl.quicklauncher.model.DatabaseUtil;

/**
 * Created by dxinl on 2016/8/5.
 *
 * Settings activity.
 */
public class SettingActivity extends AppCompatActivity implements View.OnClickListener {
	public static final String ACTION_HOME = "HOME";
	public static final String ACTION_BACK = "BACK";
	public static final String ACTION_RECENT = "RECENT";
	public static final String ACTION_NOTIFICATION = "NOTIFICATION";

	public static final String LEFT_GESTURE = "left";
	public static final String UP_GESTURE = "up";
	public static final String RIGHT_GESTURE = "right";
	public static final String DOWN_GESTURE = "down";

	public static final String QUERY_ACTION_SQL = "SELECT " + DatabaseHelper.GLOBAL_ACTION
			+ " FROM " + DatabaseHelper.GESTURE_GLOBAL_ACTION_TABLE + " WHERE "
			+ DatabaseHelper.GESTURE + "=?";
	public static final String DEL_WHERE_CLAUSE = DatabaseHelper.GESTURE + "=?";
	public static final String QUERY_ALL_SQL = "SELECT * FROM "
			+ DatabaseHelper.GESTURE_GLOBAL_ACTION_TABLE;

	private TextView leftAction;
	private TextView upAction;
	private TextView rightAction;
	private TextView downAction;
	private TextView currentAction;
	private String currentGesture;
	private String choseAction;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);

		final DatabaseUtil dbUtil = DatabaseUtil.createDbUtil(this);
		leftAction = (TextView) findViewById(R.id.towards_left_action);
		leftAction.setText(ACTION_RECENT);
		upAction = (TextView) findViewById(R.id.upwards_action);
		upAction.setText(ACTION_HOME);
		rightAction = (TextView) findViewById(R.id.towards_right_action);
		rightAction.setText(ACTION_BACK);
		downAction = (TextView) findViewById(R.id.downwards_action);
		downAction.setText(ACTION_NOTIFICATION);

		Cursor c = dbUtil.query(QUERY_ALL_SQL, null);
		choseAction = null;
		while (c.moveToNext()) {
			String gesture = c.getString(c.getColumnIndex(DatabaseHelper.GESTURE));
			String action = c.getString(c.getColumnIndex(DatabaseHelper.GLOBAL_ACTION));
			switch (gesture) {
				case LEFT_GESTURE:
					leftAction.setText(action);
					break;
				case UP_GESTURE:
					upAction.setText(action);
					break;
				case RIGHT_GESTURE:
					rightAction.setText(action);
					break;
				case DOWN_GESTURE:
					downAction.setText(action);
					break;
			}
		}
		c.close();


		View left = findViewById(R.id.left);
		left.setOnClickListener(this);
		View up = findViewById(R.id.up);
		up.setOnClickListener(this);
		View right = findViewById(R.id.right);
		right.setOnClickListener(this);
		View down = findViewById(R.id.down);
		down.setOnClickListener(this);
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
			case R.id.left:
				currentAction = leftAction;
				currentGesture = LEFT_GESTURE;
				break;

			case R.id.up:
				currentAction = upAction;
				currentGesture = UP_GESTURE;
				break;

			case R.id.right:
				currentAction = rightAction;
				currentGesture = RIGHT_GESTURE;
				break;

			case R.id.down:
				currentAction = downAction;
				currentGesture = DOWN_GESTURE;
				break;
		}
		showDialog();
	}

	private void showDialog() {
		final Dialog dialog = new Dialog(this);
		dialog.setContentView(R.layout.dialog_action_select);

		RadioGroup actionsGroup = (RadioGroup) dialog.findViewById(R.id.actions_group);
		final DatabaseUtil dbUtil = DatabaseUtil.createDbUtil(this);
		Cursor c = dbUtil.query(QUERY_ACTION_SQL, new String[]{currentGesture});
		choseAction = null;
		if (c.moveToNext()) {
			choseAction = c.getString(c.getColumnIndex(DatabaseHelper.GLOBAL_ACTION));
		}
		c.close();

		if (choseAction == null || choseAction.equals("")) {
			choseAction = ACTION_HOME;
		}

		switch (choseAction) {
			case ACTION_HOME:
				actionsGroup.check(R.id.home_btn);
				break;
			case ACTION_BACK:
				actionsGroup.check(R.id.back_btn);
				break;
			case ACTION_RECENT:
				actionsGroup.check(R.id.recent_btn);
				break;
			case ACTION_NOTIFICATION:
				actionsGroup.check(R.id.notification_btn);
				break;
		}

		actionsGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
				switch (checkedId) {
					case R.id.home_btn:
						choseAction = ACTION_HOME;
						break;
					case R.id.back_btn:
						choseAction = ACTION_BACK;
						break;
					case R.id.recent_btn:
						choseAction = ACTION_RECENT;
						break;
					case R.id.notification_btn:
						choseAction = ACTION_NOTIFICATION;
						break;
				}
			}
		});

		Button confirm = (Button) dialog.findViewById(R.id.confirm);
		confirm.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				dbUtil.delete(DatabaseHelper.GESTURE_GLOBAL_ACTION_TABLE,
						DEL_WHERE_CLAUSE, new String[]{currentGesture});

				ContentValues values = new ContentValues();
				values.put(DatabaseHelper.GESTURE, currentGesture);
				values.put(DatabaseHelper.GLOBAL_ACTION, choseAction);
				dbUtil.bulkInsert(DatabaseHelper.GESTURE_GLOBAL_ACTION_TABLE,
						new ContentValues[]{values});

				currentAction.setText(choseAction);
				dialog.dismiss();
			}
		});

		dialog.show();
	}
}
