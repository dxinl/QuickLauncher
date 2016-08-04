package com.mx.dxinl.quicklauncher;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mx.dxinl.quicklauncher.model.Utils;
import com.mx.dxinl.quicklauncher.model.DatabaseHelper;
import com.mx.dxinl.quicklauncher.model.DatabaseUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by dxinl on 2016/8/2.
 */
public class LauncherService extends Service {
	private static final int UPDATE_IC_LIST = 0x0408;
	private static final String CONFIG_CHANGE_ACTION = "android.intent.action.CONFIGURATION_CHANGED";

	private final QuickLauncherHandler handler = new QuickLauncherHandler(this);
	private final QuickLauncherAidlInterface.Stub binder = new QuickLauncherAidlInterface.Stub() {
		@Override
		public void updateLauncherList() throws RemoteException {
			handler.sendEmptyMessage(UPDATE_IC_LIST);
		}
	};
	private final BroadcastReceiver configChangedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(CONFIG_CHANGE_ACTION)) {
				onScreenOrientationChanged();
			}
		}
	};
	private final View.OnTouchListener TRIGGER_TOUCH_LISTENER = new View.OnTouchListener() {
		private boolean dragState = false;
		private float downX, downY, lastX, lastY;

		@Override
		public boolean onTouch(View view, MotionEvent event) {
			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					lastX = downX = event.getRawX();
					lastY = downY = event.getRawY();
					return true;

				case MotionEvent.ACTION_MOVE:
					float moveX = event.getRawX();
					float moveY = event.getRawY();
					if (!dragState && Math.abs(moveX - downX) > dragThreshold
							&& Math.abs(moveY - downY) > dragThreshold) {
						changeLauncherPosition((int) (moveX - lastX), (int) (moveY - lastY));
						lastX = moveX;
						lastY = moveY;
						dragState = true;
					} else if (dragState) {
						changeLauncherPosition((int) (moveX - lastX), (int) (moveY - lastY));
						lastX = moveX;
						lastY = moveY;
					}
					return true;

				case MotionEvent.ACTION_UP:
					float upX = event.getRawX();
					float upY = event.getRawY();
					if (!dragState && Math.abs(upX - downX) < dragThreshold
							&& Math.abs(upY - downY) < dragThreshold) {
						if (icList.getVisibility() == View.GONE) {
							showIconList();
						} else {
							hideIconList();
						}
					} else if (dragState) {
						stickScreenEdge();
						dragState = false;
					}
					return true;
			}
			return false;
		}
	};

	private float dragThreshold;
	private View launcher;
	private ImageView trigger;
	private RecyclerView icList;
	private WindowManager windowManager;

	@Override
	public void onCreate() {
		super.onCreate();

		IntentFilter filter = new IntentFilter();
		filter.addAction(CONFIG_CHANGE_ACTION);
		registerReceiver(configChangedReceiver, filter);
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.e("test", "onStart");
		performCreateLauncher();
		return super.onStartCommand(intent, flags, startId);
	}

	private void performCreateLauncher() {
		if (launcher == null) {
			Context context = getApplicationContext();
			LayoutInflater inflater = LayoutInflater.from(context);

			dragThreshold = ViewConfiguration.get(context).getScaledTouchSlop();


			launcher = inflater.inflate(R.layout.service_launcher, null);
			LayoutParams params = generateLayoutParams();

			windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
			windowManager.addView(launcher, params);

			initLauncherChildren(launcher);
		}
	}

	@NonNull
	private LayoutParams generateLayoutParams() {
		LayoutParams params = new LayoutParams();
		//noinspection WrongConstant
		params.type = getParamsType();
		params.flags = LayoutParams.FLAG_NOT_FOCUSABLE | LayoutParams.FLAG_NOT_TOUCH_MODAL;
		params.width = LayoutParams.WRAP_CONTENT;
		params.height = LayoutParams.WRAP_CONTENT;
		params.format = LayoutParams.LAYOUT_CHANGED;
		params.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
		return params;
	}

	private int getParamsType() {
		int type;
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
			type = LayoutParams.TYPE_PHONE;
		} else {
			type = LayoutParams.TYPE_TOAST;
		}
		return type;
	}

	private void initLauncherChildren(View launcher) {
		icList = (RecyclerView) launcher.findViewById(R.id.ic_list);
		trigger = (ImageView) launcher.findViewById(R.id.touch_trigger);

		initIconList();
		initTrigger();
	}

	private void initIconList() {
		int screenWidth = Utils.getScreenWidth(getApplicationContext());
		int screenHeight = Utils.getScreenHeight(getApplicationContext());
		final int icListWidth;
		final int icListHeight;
		if (screenHeight < screenWidth) {
			icListWidth = screenHeight / 6;
			icListHeight = screenHeight / 4 * 3;
		} else {
			icListWidth = screenWidth / 6;
			icListHeight = screenHeight / 5 * 3;
		}

		ViewGroup.LayoutParams params = icList.getLayoutParams();
		if (params == null) {
			params = new LinearLayout.LayoutParams(icListWidth, icListHeight);
		} else {
			params.width = icListWidth;
			params.height = icListHeight;
		}
		icList.setLayoutParams(params);
		icList.setAdapter(new IconListAdapter(getAppsInfo()));
		icList.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
	}

	private void initTrigger() {
		changeTriggerSize(false);
		trigger.setOnTouchListener(TRIGGER_TOUCH_LISTENER);
	}

	private void changeLauncherPosition(int x, int y) {
		LayoutParams params = (LayoutParams) launcher.getLayoutParams();
		params.x += x;
		params.y += y;

		windowManager.updateViewLayout(launcher, params);
	}

	private void stickScreenEdge() {
		final LayoutParams params = (LayoutParams) launcher.getLayoutParams();
		int screenWidth = Utils.getScreenWidth(getApplicationContext());

		if (params.x > screenWidth / 2) {
			params.x = screenWidth;
		} else {
			params.x = 0;
		}

		windowManager.updateViewLayout(launcher, params);
	}

	private void changeTriggerSize(boolean icListVisible) {
		int screenWidth = Utils.getScreenWidth(getApplicationContext());
		int screenHeight = Utils.getScreenHeight(getApplicationContext());
		final int size;
		if (screenHeight < screenWidth) {
			size = screenHeight / 6;
		} else {
			size = screenWidth / 6;
		}

		int width, height = size / 2;
		if (icListVisible) {
			width = size;
		} else {
			width = size / 2;
		}
		width -= 2 * getResources().getDimensionPixelSize(R.dimen.trigger_margin);
		ViewGroup.LayoutParams params = trigger.getLayoutParams();
		if (params == null) {
			params = new LinearLayout.LayoutParams(width, height);
		} else {
			params.width = width;
			params.height = height;
		}

		trigger.setLayoutParams(params);
	}

	private void showIconList() {
		launcher.setAlpha(1f);
		icList.setVisibility(View.VISIBLE);
		updateIcList();
		changeTriggerSize(true);
		trigger.setImageResource(R.mipmap.arrow_reverse);
	}

	private void updateIcList() {
		IconListAdapter adapter = (IconListAdapter) icList.getAdapter();
		adapter.updateData(getAppsInfo());
	}

	private void hideIconList() {
		launcher.setAlpha(0.5f);
		icList.setVisibility(View.GONE);
		changeTriggerSize(false);
		trigger.setImageResource(R.mipmap.quick_launcher);
	}

	private List<ResolveInfo> getAppsInfo() {
		List<ResolveInfo> allInfo = Utils.getAppsInfo(this);
		List<ResolveInfo> showInfo = new ArrayList<>();

		DatabaseUtil dbUtil = DatabaseUtil.createDbUtil(getApplicationContext());
		Cursor c = dbUtil.query(DatabaseUtil.QUERY_PKG_NAME_SQL, null);
		if (c == null) {
			return showInfo;
		}
		while (c.moveToNext()) {
			String name = c.getString(c.getColumnIndex(DatabaseHelper.COLUMN_PKG_NAME));
			for (ResolveInfo info : allInfo) {
				if (info.activityInfo.packageName.equals(name)) {
					showInfo.add(info);
				}
			}
		}
		c.close();

		return showInfo;
	}

	private void onScreenOrientationChanged() {
		windowManager.removeView(launcher);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unregisterReceiver(configChangedReceiver);
	}

	private static final class QuickLauncherHandler extends Handler {
		private WeakReference<LauncherService> ref;

		public QuickLauncherHandler(LauncherService service) {
			ref = new WeakReference<>(service);
		}

		@Override
		public void handleMessage(Message msg) {
			LauncherService service = ref.get();
			if (service == null) {
				return;
			}

			if (msg.what == UPDATE_IC_LIST) {
				if (service.icList.getVisibility() == View.VISIBLE) {
					service.updateIcList();
				} else {
					service.showIconList();
				}
			}
		}
	}

	/**
	 * Created by Deng Xinliang on 2016/8/3.
	 */
	private final class ViewHolder extends RecyclerView.ViewHolder {
		private final PackageManager packageManager;
		private Context context;
		private ImageView appIcon;
		private TextView appName;

		public ViewHolder(View itemView) {
			super(itemView);
			context = itemView.getContext();

			appIcon = (ImageView) itemView.findViewById(R.id.icon_app);
			int screenWidth = Utils.getScreenWidth(getApplicationContext());
			int screenHeight = Utils.getScreenHeight(getApplicationContext());
			int size;
			if (screenHeight < screenWidth) {
				size = screenHeight;
			} else {
				size = screenWidth;
			}

			final int appIconMargin = getResources().getDimensionPixelSize(R.dimen.ic_list_item_margin);
			final int appIconSize = size / 6 - 2 * appIconMargin;
			ViewGroup.LayoutParams params = appIcon.getLayoutParams();
			if (params == null) {
				params = new LinearLayout.LayoutParams(appIconSize, appIconSize);
			} else {
				params.width = appIconSize;
				params.height = appIconSize;
			}
			appIcon.setLayoutParams(params);

			appName = (TextView) itemView.findViewById(R.id.name_app);
			packageManager = context.getPackageManager();
		}

		public void initItemView(ResolveInfo info) {
			appIcon.setImageDrawable(info.loadIcon(packageManager));
			appName.setText(info.loadLabel(packageManager));
			setOnClickListener(info);
		}

		public void setOnClickListener(final ResolveInfo info) {
			itemView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent launchIntent = packageManager
							.getLaunchIntentForPackage(info.activityInfo.packageName);
					if (launchIntent != null) {
						context.startActivity(launchIntent);
					}
					hideIconList();
				}
			});
		}
	}

	/**
	 * Created by Deng Xinliang on 2016/8/3.
	 */
	private final class IconListAdapter extends RecyclerView.Adapter<ViewHolder> {
		private List<ResolveInfo> appsInfo = new ArrayList<>();

		public IconListAdapter(List<ResolveInfo> appsInfo) {
			this.appsInfo.addAll(appsInfo);
		}

		public void updateData(List<ResolveInfo> appsInfo) {
			this.appsInfo.clear();
			this.appsInfo.addAll(appsInfo);
			notifyDataSetChanged();
		}

		@Override
		public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			LayoutInflater inflater = LayoutInflater.from(parent.getContext());
			return new ViewHolder(inflater.inflate(R.layout.item_icon_list, parent, false));
		}

		@Override
		public void onBindViewHolder(ViewHolder holder, int position) {
			ResolveInfo info = appsInfo.get(position);
			holder.initItemView(info);
		}

		@Override
		public int getItemCount() {
			return appsInfo.size();
		}
	}
}
