package com.matburt.mobileorg2.Services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.matburt.mobileorg2.OrgData.MobileOrgApplication;
import com.matburt.mobileorg2.Synchronizers.Synchronizer;

import java.util.HashSet;

public class SyncService extends Service implements
		SharedPreferences.OnSharedPreferenceChangeListener {
	private static final String ACTION = "action";
	private static final String START_ALARM = "START_ALARM";
	private static final String STOP_ALARM = "STOP_ALARM";

	private SharedPreferences appSettings;
	private MobileOrgApplication appInst;

	private AlarmManager alarmManager;
	private PendingIntent alarmIntent;
	private boolean alarmScheduled = false;

	private boolean syncRunning;

	public static void stopAlarm(Context context) {
		Intent intent = new Intent(context, SyncService.class);
		intent.putExtra(ACTION, SyncService.STOP_ALARM);
		context.startService(intent);
	}

	public static void startAlarm(Context context) {
		Intent intent = new Intent(context, SyncService.class);
		intent.putExtra(ACTION, SyncService.START_ALARM);
		context.startService(intent);
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		this.appSettings = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		this.appSettings.registerOnSharedPreferenceChangeListener(this);
		this.appInst = (MobileOrgApplication) this.getApplication();
		this.alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

		Log.v("trace", "sync service");
	}

	@Override
	public void onDestroy() {
		unsetAlarm();
		this.appSettings.unregisterOnSharedPreferenceChangeListener(this);
		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		String action = intent.getStringExtra(ACTION);
		if (action != null && action.equals(START_ALARM))
			setAlarm();
		else if (action != null && action.equals(STOP_ALARM))
			unsetAlarm();
		else if(!this.syncRunning) {
			this.syncRunning = true;
			runSynchronizer();
		}
		return 0;
	}



	private void runSynchronizer() {
		unsetAlarm();
		final Synchronizer synchronizer = Synchronizer.getInstance();

		final boolean calendarEnabled = appSettings.getBoolean("calendarEnabled", false);

		Thread syncThread = new Thread() {
			public void run() {
				HashSet<String> changedFiles = synchronizer.runSynchronizer();
				String[] files = changedFiles.toArray(new String[changedFiles.size()]);
				if(calendarEnabled) {
					Intent calIntent = new Intent(getBaseContext(), CalendarSyncService.class);
					calIntent.putExtra(CalendarSyncService.PUSH, true);
					calIntent.putExtra(CalendarSyncService.FILELIST, files);
					getBaseContext().startService(calIntent);
				}
				Synchronizer.getInstance().postSynchronize();
				syncRunning = false;
				setAlarm();
			}
		};

		syncThread.start();
	}


	private void setAlarm() {
		boolean doAutoSync = this.appSettings.getBoolean("doAutoSync", false);
		if (!this.alarmScheduled && doAutoSync) {

			int interval = Integer.parseInt(
					this.appSettings.getString("autoSyncInterval", "1800000"),
					10);

			this.alarmIntent = PendingIntent.getService(appInst, 0, new Intent(
					this, SyncService.class), 0);
			alarmManager.setRepeating(AlarmManager.RTC,
					System.currentTimeMillis() + interval, interval,
					alarmIntent);

			this.alarmScheduled = true;
		}
	}

	private void unsetAlarm() {
		if (this.alarmScheduled) {
			this.alarmManager.cancel(this.alarmIntent);
			this.alarmScheduled = false;
		}
	}

	private void resetAlarm() {
		unsetAlarm();
		setAlarm();
	}


	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (key.equals("doAutoSync")) {
			if (sharedPreferences.getBoolean("doAutoSync", false)
					&& !this.alarmScheduled)
				setAlarm();
			else if (!sharedPreferences.getBoolean("doAutoSync", false)
					&& this.alarmScheduled)
				unsetAlarm();
		} else if (key.equals("autoSyncInterval"))
			resetAlarm();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
