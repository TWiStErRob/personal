package net.twisterrob.inventory.android;

import java.util.*;

import org.slf4j.*;

import android.content.Intent;
import android.content.res.*;
import android.database.sqlite.SQLiteConstraintException;
import android.graphics.Color;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.support.annotation.*;
import android.text.TextUtils;

import net.twisterrob.android.app.BaseApp;
import net.twisterrob.android.utils.tools.*;
import net.twisterrob.inventory.android.content.Database;
import net.twisterrob.inventory.android.content.db.DatabaseService;

public class App extends BaseApp {
	private static final Logger LOG = LoggerFactory.getLogger(App.class);

	public App() {
		super(BuildConfig.DEBUG, R.xml.preferences);
	}

	public static @NonNull App getInstance() {
		return (App)BaseApp.getInstance();
	}

	public static Database db() {
		return getInstance().getDatabase();
	}

	@Override protected Database createDatabase() {
		return new Database(this);
	}

	@Override public void onStart() {
		super.onStart();
		// open a database first, this should lock any other accesses, so it's clearer when the DB open fails
		startService(new Intent(DatabaseService.ACTION_OPEN_DATABASE).setPackage(getPackageName()));
		// run vacuum next, it's quick and most of the time does nothing anyway
		startService(new Intent(DatabaseService.ACTION_VACUUM_INCREMENTAL).setPackage(getPackageName()));
		// this may take a while, but it's necessary for correct display of searc reults
		updateLanguage(Locale.getDefault());
		// last, preload categories, this would happen when editing and suggestions kick in
		// so, prevent a delay on first suggestion, load it in the background
		startService(new Intent(DatabaseService.ACTION_PRELOAD_CATEGORIES).setPackage(getPackageName()));
	}

	@Override protected void initPreferences() {
		super.initPreferences();
		new PreferencesMigrator(this, prefs()).migrate();
	}

	@Override public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		updateLanguage(AndroidTools.getLocale(newConfig));
		// FIXME private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance();
	}

	private void updateLanguage(@NonNull Locale newLocale) {
		startService(new Intent(DatabaseService.ACTION_UPDATE_LANGUAGE)
				.setPackage(getPackageName())
				.putExtra(DatabaseService.EXTRA_LOCALE, newLocale));
	}

	@SuppressWarnings("WrongThread") // TODEL http://b.android.com/207302
	@Override public void onTerminate() {
		ThreadPolicy originalPolicy = StrictMode.allowThreadDiskWrites();
		try {
			db().getWritableDatabase().close();
		} finally {
			StrictMode.setThreadPolicy(originalPolicy);
		}
		super.onTerminate();
	}

	public static CharSequence getError(@NonNull Throwable ex, @StringRes int errorResource, Object... args) {
		return getError(ex, getAppContext().getString(errorResource, args));
	}
	public static CharSequence getError(@NonNull Throwable ex, @NonNull CharSequence message) {
		CharSequence errorMessage = ex.toString();
		Resources res = getAppContext().getResources();
		String msg = ex.getMessage();
		if (ex instanceof SQLiteConstraintException && NAME_ERRORS.contains(msg)) {
			errorMessage = res.getString(R.string.generic_error_unique_name);
		} else if (ex instanceof SQLiteConstraintException && EMPTY_ERRORS.contains(msg)) {
			errorMessage = res.getString(R.string.generic_error_length_name);
		}
		return TextUtils.concat(message, "\n", TextTools.color(Color.GRAY, errorMessage));
	}

	// 2013-03-18 (SQLite 3.7.16): Added new extended error codes for all SQLITE_CONSTRAINT errors
	private static final Collection<String> EMPTY_ERRORS = new HashSet<>(Arrays.asList(
			// Galaxy S4 4.4.2 3.7.11
			"constraint failed (code 19)",
			// Galaxy S5 5.0.1 3.8.6.1
			"CHECK constraint failed: Property (code 275)", // should be guarded by BaseEditFragment
			"CHECK constraint failed: Room (code 275)", // should be guarded by BaseEditFragment
			"CHECK constraint failed: Item (code 275)", // should be guarded by BaseEditFragment
			"CHECK constraint failed: List (code 275)"
	));

	// SQLite 3.7.16 released 2013-03-18: Added new extended error codes for all SQLITE_CONSTRAINT errors
	private static final Collection<String> NAME_ERRORS = new HashSet<>(Arrays.asList(
			// Galaxy S2 2.3.7 3.6.22
			"error code 19: constraint failed",
			// Galaxy S4 4.4.2 3.7.11
			"column name is not unique (code 19)", // property, list
			"columns property, name are not unique (code 19)", // room
			"columns parent, name are not unique (code 19)", // item
			// Galaxy S5 5.0.1 3.8.6.1 
			"UNIQUE constraint failed: Property.name (code 2067)",
			"UNIQUE constraint failed: Room.property, Room.name (code 2067)",
			"UNIQUE constraint failed: Item.parent, Item.name (code 2067)"
	));
}
