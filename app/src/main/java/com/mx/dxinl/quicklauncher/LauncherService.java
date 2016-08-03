package com.mx.dxinl.quicklauncher;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dxinl on 2016/8/2.
 */
public class LauncherService extends Service {
	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		performCreateLauncher();
		return super.onStartCommand(intent, flags, startId);
	}

	private void performCreateLauncher() {
		Context context = getApplicationContext();
		LayoutInflater inflater = LayoutInflater.from(context);
		final View launcher = inflater.inflate(R.layout.service_launcher, null);

		DisplayMetrics metrics = getResources().getDisplayMetrics();
		final int screenWidth = metrics.widthPixels;
		final int screenHeight = metrics.heightPixels;

		final int launcherWidth = screenWidth / 6;
		final int launcherHeight = screenHeight / 5 * 3;
		LayoutParams params = generateLayoutParams(launcherHeight);

		final WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
		wm.addView(launcher, params);

		initLauncherChildren(launcher, launcherWidth, launcherHeight);
	}

	private void initLauncherChildren(View launcher, int launcherWidth, int launcherHeight) {
		final RecyclerView icList = (RecyclerView) launcher.findViewById(R.id.ic_list);
		final View trigger = launcher.findViewById(R.id.touch_trigger);

		final float triggerThreshold = getResources().getDimension(R.dimen.trigger_threshold);
		initIconList(launcherWidth, launcherHeight, icList, triggerThreshold);
		initTrigger(icList, trigger, triggerThreshold);
	}

	private void initTrigger(final RecyclerView icList, View trigger, final float triggerThreshold) {
		trigger.setOnTouchListener(new View.OnTouchListener() {
			private float lastX;

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						lastX = event.getRawX();
						return true;

					case MotionEvent.ACTION_MOVE:
						float tmpX = event.getRawX();
						if (tmpX - lastX <- triggerThreshold) {
							showIconList(icList, View.GONE);
							lastX = tmpX;
						} else if (tmpX - lastX > triggerThreshold) {
							showIconList(icList, View.VISIBLE);
							lastX = tmpX;
						}
						return true;
				}
				return false;
			}
		});
	}

	private void initIconList(int launcherWidth, int launcherHeight,
	                          final RecyclerView icList, final float triggerThreshold) {
		LinearLayout.LayoutParams icListParams =
				new LinearLayout.LayoutParams(launcherWidth, launcherHeight);
		icList.setLayoutParams(icListParams);
		final ItemClickCallback callback = new ItemClickCallback() {
			@Override
			public void onClicked() {
				icList.setVisibility(View.GONE);
			}
		};
		icList.setAdapter(new IconListAdapter(getAppsInfo(), callback));
		icList.setLayoutManager(new LinearLayoutManager(getApplicationContext()));

		icList.setOnTouchListener(new View.OnTouchListener() {
			private float downX, downY;

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						downX = event.getRawX();
						downY = event.getRawY();
						return true;

					case MotionEvent.ACTION_MOVE:
						float rawX = event.getRawX();
						float distX = rawX - downX;
						float distY = event.getRawY() - downY;
						if (distX <- triggerThreshold
								&& rawX < triggerThreshold / 2f
								&& Math.abs(distX) > Math.abs(distY)) {
							showIconList(icList, View.GONE);
							return true;
						}
						return false;
				}
				return false;
			}
		});
	}

	private void showIconList(RecyclerView icList, int visible) {
		icList.setVisibility(visible);
		IconListAdapter adapter = (IconListAdapter) icList.getAdapter();
		adapter.updateData(getAppsInfo());
	}

	private List<ApplicationInfo> getAppsInfo() {
		return getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA);
	}

	@NonNull
	private LayoutParams generateLayoutParams(int launcherHeight) {
		LayoutParams params = new LayoutParams();
		params.type = LayoutParams.TYPE_PHONE;
		params.flags = LayoutParams.FLAG_NOT_FOCUSABLE | LayoutParams.FLAG_NOT_TOUCH_MODAL;
		params.width = LayoutParams.WRAP_CONTENT;
		params.height = launcherHeight;
		params.format = LayoutParams.LAYOUT_CHANGED;
		params.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
		return params;
	}

	private static final class IconListAdapter extends RecyclerView.Adapter<ViewHolder> {
		private List<ApplicationInfo> appsInfo = new ArrayList<>();
		private ItemClickCallback callback;

		public IconListAdapter(List<ApplicationInfo> appsInfo, ItemClickCallback callback) {
			this.appsInfo.addAll(appsInfo);
			this.callback = callback;
		}

		public void updateData(List<ApplicationInfo> appsInfo) {
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
			ApplicationInfo info = appsInfo.get(position);
			holder.initItemView(info);
			holder.setOnClickListener(info, callback);
		}

		@Override
		public int getItemCount() {
			return appsInfo.size();
		}
	}

	private static class ViewHolder extends RecyclerView.ViewHolder {
		private Context context;
		private ImageView appIcon;
		private TextView appName;
		private final PackageManager packageManager;

		public ViewHolder(View itemView) {
			super(itemView);
			context = itemView.getContext();
			appIcon = (ImageView) itemView.findViewById(R.id.icon_app);
			appName = (TextView) itemView.findViewById(R.id.name_app);
			packageManager = context.getPackageManager();
		}

		public void initItemView(ApplicationInfo info) {
			appIcon.setImageDrawable(info.loadIcon(packageManager));
			appName.setText(info.loadLabel(packageManager));
		}

		public void setOnClickListener(final ApplicationInfo info, final ItemClickCallback callback) {
			itemView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent launchIntent = packageManager.getLaunchIntentForPackage(info.packageName);
					if (launchIntent != null) {
						context.startActivity(launchIntent);
					}
					callback.onClicked();
				}
			});
		}
	}

	interface ItemClickCallback {
		void onClicked();
	}
}
