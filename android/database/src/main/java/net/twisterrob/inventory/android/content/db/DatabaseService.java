package net.twisterrob.inventory.android.content.db;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.slf4j.*;

import android.app.*;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import net.twisterrob.android.utils.tools.DatabaseTools;
import net.twisterrob.android.utils.tools.IntentTools;
import net.twisterrob.inventory.android.BaseComponent;
import net.twisterrob.inventory.android.categories.cache.CategoryCacheImpl;
import net.twisterrob.inventory.android.content.Database;
import net.twisterrob.inventory.android.content.VariantIntentService;

import static net.twisterrob.inventory.android.content.BroadcastTools.getLocalBroadcastManager;

public class DatabaseService extends VariantIntentService {
	private static final Logger LOG = LoggerFactory.getLogger(DatabaseService.class);
	private static final long SLEEP_BETWEEN_VACUUMS = TimeUnit.SECONDS.toMillis(10);
	public static final String ACTION_OPEN_DATABASE = "net.twisterrob.inventory.action.OPEN_DATABASE";
	public static final String ACTION_VACUUM_INCREMENTAL = "net.twisterrob.inventory.action.VACUUM_INCREMENTAL";
	public static final String ACTION_PRELOAD_CATEGORIES = "net.twisterrob.inventory.action.PRELOAD_CATEGORIES";
	public static final String ACTION_UPDATE_LANGUAGE = "net.twisterrob.inventory.action.UPDATE_LANGUAGE";
	public static final String ACTION_SERVICE_SHUTDOWN = "net.twisterrob.inventory.action.SERVICE_SHUTDOWN";
	public static final String EXTRA_LOCALE = "net.twisterrob.inventory.extra.update_language_locale";
	private static final int CODE_INCREMENTAL_VACUUM = 16336;

	@Override protected void onHandleWork(@NonNull Intent intent) {
		super.onHandleWork(intent);
		String action = String.valueOf(intent.getAction()); // null becomes "null", so we can switch on it
		switch (action) {
			case ACTION_OPEN_DATABASE:
				SQLiteDatabase db = Database.get(this).getWritableDatabase();
				LOG.trace("Database opened: {}", DatabaseTools.dbToString(db));
				break;
			case ACTION_UPDATE_LANGUAGE:
				updateLanguage(intent);
				break;
			case ACTION_PRELOAD_CATEGORIES:
				preloadCategoryCache();
				break;
			case ACTION_VACUUM_INCREMENTAL:
				incrementalVacuum();
				break;
			default:
				throw new UnsupportedOperationException("Action " + action + " is not implemented for " + intent);
		}
	}

	@Override public void onDestroy() {
		super.onDestroy();
		getLocalBroadcastManager(getApplicationContext())
				.sendBroadcast(new Intent(ACTION_SERVICE_SHUTDOWN).setClass(getApplicationContext(), getClass()));
	}

	private void updateLanguage(@NonNull Intent intent) {
		Locale locale = IntentTools.getSerializableExtra(intent, EXTRA_LOCALE, Locale.class);
		if (locale == null) {
			LOG.warn("Missing locale from {}", intent);
			locale = Locale.getDefault();
		}
		BaseComponent inject = BaseComponent.get(this);
		new LanguageUpdater(getApplicationContext(), inject.prefs(), Database.get(this), inject.toaster())
				.updateLanguage(locale);
	}

	private void preloadCategoryCache() {
		try {
			CategoryCacheImpl.getCache(getApplicationContext());
		} catch (Exception ex) {
			// if fails we'll crash later when used, but at least let the app start up
			LOG.error("Failed to initialize Category cache", ex);
		}
	}

	private void incrementalVacuum() {
		try {
			SQLiteDatabase database = Database.get(this).getWritableDatabase();
			if (Boolean.TRUE.equals(new IncrementalVacuumer(database).call())) {
				scheduleNextIncrementalVacuum();
			}
		} catch (Exception ex) {
			if (!(ex instanceof RuntimeException)) {
				throw new IllegalStateException("Incremental vacuum failed.", ex);
			} else {
				throw (RuntimeException)ex;
			}
		}
	}

	private void scheduleNextIncrementalVacuum() {
		long next = System.currentTimeMillis() + SLEEP_BETWEEN_VACUUMS;
		LOG.trace("Scheduling another incremental vacuuming at {}", String.format(Locale.ROOT, "%tFT%<tT", next));
		AlarmManager am = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
		PendingIntent pendingIntent = createVacuumIntent(this);
		am.set(AlarmManager.RTC, next, pendingIntent);
	}
	private static PendingIntent createVacuumIntent(Context context) {
		Intent intent = new Intent(ACTION_VACUUM_INCREMENTAL).setPackage(context.getPackageName());
		return PendingIntent.getService(context, CODE_INCREMENTAL_VACUUM, intent,
				PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);
	}

	public static void clearVacuumAlarm(Context context) {
		AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
		PendingIntent pendingIntent = createVacuumIntent(context);
		pendingIntent.cancel();
		am.cancel(pendingIntent);
	}
}