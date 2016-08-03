package com.mx.dxinl.quicklauncher;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
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

        final int icListWidth = screenWidth / 6;
		final int launcherHeight = screenHeight / 5 * 3;
		LayoutParams params = generateLayoutParams();

		final WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
		wm.addView(launcher, params);

		initLauncherChildren(launcher, icListWidth, launcherHeight);
	}

    @NonNull
    private LayoutParams generateLayoutParams() {
        LayoutParams params = new LayoutParams();
        params.type = LayoutParams.TYPE_PHONE;
        params.flags = LayoutParams.FLAG_NOT_FOCUSABLE | LayoutParams.FLAG_NOT_TOUCH_MODAL;
        params.width = LayoutParams.WRAP_CONTENT;
        params.height = LayoutParams.WRAP_CONTENT;
        params.format = LayoutParams.LAYOUT_CHANGED;
        params.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        return params;
    }

    private void initLauncherChildren(View launcher, int icListWidth, int launcherHeight) {
		final RecyclerView icList = (RecyclerView) launcher.findViewById(R.id.ic_list);
		final ImageView trigger = (ImageView) launcher.findViewById(R.id.touch_trigger);

		initIconList(icListWidth, launcherHeight, icList, trigger);
		initTrigger(icList, trigger);
	}

    private void initIconList(int icListWidth, int launcherHeight,
                              final RecyclerView icList, final ImageView trigger) {
        LinearLayout.LayoutParams icListParams =
                new LinearLayout.LayoutParams(icListWidth, launcherHeight);
        icList.setLayoutParams(icListParams);
        final ItemClickCallback callback = new ItemClickCallback() {
            @Override
            public void onClicked() {
                hideIconList(icList, trigger);
            }
        };
        icList.setAdapter(new IconListAdapter(getAppsInfo(), callback));
        icList.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
    }

    private void initTrigger(final RecyclerView icList, final ImageView trigger) {
		trigger.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (icList.getVisibility() == View.GONE) {
                    showIconList(icList, trigger);
				} else {
                    hideIconList(icList, trigger);
                }
			}
		});
	}

    private void showIconList(RecyclerView icList, ImageView trigger) {
        icList.setVisibility(View.VISIBLE);
        IconListAdapter adapter = (IconListAdapter) icList.getAdapter();
        adapter.updateData(getAppsInfo());
        trigger.setImageResource(R.mipmap.arrow_reverse);
    }

    private List<ResolveInfo> getAppsInfo() {
        List<ResolveInfo> allInfo = Util.getAppsInfo(this);
        List<ResolveInfo> showInfo = new ArrayList<>();

        Uri pkgName = Uri.parse(PkgContentProvider.URI);
        Cursor c = getContentResolver().query(pkgName, null, null, null, PkgContentProvider.PKG_NAME);
        if (c == null) {
            return showInfo;
        }

        while (c.moveToNext()) {
            String name = c.getString(c.getColumnIndex(PkgContentProvider.PKG_NAME));
            for (ResolveInfo info : allInfo) {
                if (info.activityInfo.packageName.equals(name)) {
                    showInfo.add(info);
                }
            }
        }
        c.close();

        return showInfo;
    }

    private void hideIconList(RecyclerView icList, ImageView trigger) {
        icList.setVisibility(View.GONE);
        trigger.setImageResource(R.mipmap.arrow);
    }

    interface ItemClickCallback {
		void onClicked();
	}

    /**
     * Created by Deng Xinliang on 2016/8/3.
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        private Context context;
        private ImageView appIcon;
        private TextView appName;
        private final PackageManager packageManager;

        public ViewHolder(View itemView) {
            super(itemView);
            context = itemView.getContext();

            appIcon = (ImageView) itemView.findViewById(R.id.icon_app);
            final int screenWidth = context.getApplicationContext().getResources()
                    .getDisplayMetrics().widthPixels;
            final int icListWidth = screenWidth / 6;
            final int icListItemMargin = context.getResources()
                    .getDimensionPixelSize(R.dimen.ic_list_item_margin);
            final int appIconSize = icListWidth - 2 * icListItemMargin;
            appIcon.setLayoutParams(new LinearLayout.LayoutParams(appIconSize, appIconSize));

            appName = (TextView) itemView.findViewById(R.id.name_app);
            packageManager = context.getPackageManager();
        }

        public void initItemView(ResolveInfo info) {
            appIcon.setImageDrawable(info.loadIcon(packageManager));
            appName.setText(info.loadLabel(packageManager));
        }

        public void setOnClickListener(final ResolveInfo info, final ItemClickCallback callback) {
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent launchIntent = packageManager
                            .getLaunchIntentForPackage(info.activityInfo.packageName);
                    if (launchIntent != null) {
                        context.startActivity(launchIntent);
                    }
                    callback.onClicked();
                }
            });
        }
    }

    /**
     * Created by Deng Xinliang on 2016/8/3.
     */
    static final class IconListAdapter extends RecyclerView.Adapter<ViewHolder> {
        private List<ResolveInfo> appsInfo = new ArrayList<>();
        private ItemClickCallback callback;

        public IconListAdapter(List<ResolveInfo> appsInfo, ItemClickCallback callback) {
            this.appsInfo.addAll(appsInfo);
            this.callback = callback;
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
            holder.setOnClickListener(info, callback);
        }

        @Override
        public int getItemCount() {
            return appsInfo.size();
        }
    }
}
