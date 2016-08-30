package net.twisterrob.inventory.android.test;

import java.io.File;
import java.lang.reflect.Field;

import org.slf4j.*;

import android.app.Activity;
import android.content.Context;
import android.support.test.InstrumentationRegistry;

import com.bumptech.glide.Glide;

import net.twisterrob.android.test.junit.SensibleActivityTestRule;
import net.twisterrob.android.utils.tools.IOTools;
import net.twisterrob.inventory.android.App;
import net.twisterrob.inventory.android.content.Database;

import static net.twisterrob.android.app.BaseApp.*;

public class InventoryActivityRule<T extends Activity> extends SensibleActivityTestRule<T> {
	private static final Logger LOG = LoggerFactory.getLogger(InventoryActivityRule.class);

	public InventoryActivityRule(Class<T> activityClass) {
		super(activityClass);
	}
	public InventoryActivityRule(Class<T> activityClass, boolean initialTouchMode) {
		super(activityClass, initialTouchMode);
	}
	public InventoryActivityRule(Class<T> activityClass, boolean initialTouchMode, boolean launchActivity) {
		super(activityClass, initialTouchMode, launchActivity);
	}

	@Override protected void beforeActivityLaunched() {
		reset();
		super.beforeActivityLaunched();
	}

	@Override protected void afterActivityFinished() {
		super.afterActivityFinished();
		reset();
	}

	public void reset() {
		resetGlide();
		resetDB();
		resetPreferences();
		resetFiles();
	}

	protected void resetFiles() {
		Context context = InstrumentationRegistry.getTargetContext();
		File intDir = context.getFilesDir();
		LOG.info("Deleting {}", intDir);
		if (!IOTools.delete(intDir)) {
			throw new IllegalStateException("Cannot delete " + intDir);
		}
		File extDir = context.getExternalFilesDir(null);
		LOG.info("Deleting {}", extDir);
		if (!IOTools.delete(extDir)) {
			throw new IllegalStateException("Cannot delete " + extDir);
		}
	}

	protected void resetPreferences() {
		LOG.info("Clearing preferences");
		prefs().edit().clear().apply();
	}

	protected void resetDB() {
		Database db = App.db();
		File dbFile = db.getFile();
		LOG.info("Closing and deleting DB {}", dbFile);
		db.getHelper().close();
		if (dbFile.exists() && !dbFile.delete()) {
			throw new IllegalStateException("Cannot delete Database");
		}
	}

	protected void resetGlide() {
		LOG.info("Resetting Glide");
		try {
			Glide.get(InstrumentationRegistry.getTargetContext()).clearDiskCache();
			Field glide = Glide.class.getDeclaredField("glide");
			glide.setAccessible(true);
			glide.set(null, null);
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}
}
