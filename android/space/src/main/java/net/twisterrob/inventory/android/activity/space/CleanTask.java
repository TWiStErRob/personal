package net.twisterrob.inventory.android.activity.space;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;

import androidx.annotation.NonNull;

import net.twisterrob.android.utils.concurrent.SimpleSafeAsyncTask;
import net.twisterrob.android.utils.tools.PackageManagerTools;

abstract class CleanTask extends SimpleSafeAsyncTask<Activity, Void, Void> implements NoProgressTaskExecutor.UITask {
	private static final Logger LOG = LoggerFactory.getLogger(CleanTask.class);
	private TaskEndListener callbacks;

	@Override public void setCallbacks(TaskEndListener callbacks) {
		this.callbacks = callbacks;
	}

	@SuppressWarnings("deprecation")
	@Override public void execute(@NonNull Activity activity) {
		super.execute(activity);
	}

	protected abstract void doClean() throws Exception;

	@Override protected Void doInBackground(Activity activity) throws Exception {
		killProcessesAround(activity);
		doClean();
		killProcessesAround(activity);
		return null;
	}

	@Override protected void onResult(Void aVoid, Activity activity) {
		callbacks.taskDone();
	}

	@Override protected void onError(@NonNull Exception ex, Activity activity) {
		LOG.warn("Could not finish task", ex);
		callbacks.taskDone();
	}

	static void killProcessesAround(@NonNull Activity activity) {
		ActivityManager am = (ActivityManager)activity.getSystemService(Context.ACTIVITY_SERVICE);
		String myProcessPrefix = activity.getApplicationInfo().processName;
		String myProcessName = PackageManagerTools.getActivityInfo(activity, 0).processName;
		for (ActivityManager.RunningAppProcessInfo proc : am.getRunningAppProcesses()) {
			if (proc.processName.startsWith(myProcessPrefix) && !proc.processName.equals(myProcessName)) {
				android.os.Process.killProcess(proc.pid);
			}
		}
	}
}
