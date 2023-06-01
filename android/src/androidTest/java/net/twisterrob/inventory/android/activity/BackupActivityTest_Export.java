package net.twisterrob.inventory.android.activity;

import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.rules.*;
import org.junit.runner.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import net.twisterrob.android.test.automators.DocumentsUiAutomator;
import net.twisterrob.android.test.automators.UiAutomatorExtensions;
import net.twisterrob.inventory.android.R;
import net.twisterrob.inventory.android.test.ExternalAppKiller;
import net.twisterrob.inventory.android.test.InventoryActivityRule;
import net.twisterrob.inventory.android.test.actors.BackupActivityActor;
import net.twisterrob.inventory.android.test.actors.BackupActivityActor.BackupExportPickerActor;
import net.twisterrob.inventory.android.test.actors.BackupActivityActor.BackupExportPickerActor.BackupExportResultActor;
import net.twisterrob.inventory.android.test.categories.*;

import static net.twisterrob.android.test.automators.UiAutomatorExtensions.UI_AUTOMATOR_VERSION;

@RunWith(AndroidJUnit4.class)
@Category({On.Export.class})
public class BackupActivityTest_Export {

	@Rule(order = 0) public final TestRule docsKiller =
			new ExternalAppKiller(DocumentsUiAutomator.PACKAGE_DOCUMENTS_UI);

	@SuppressWarnings("deprecation")
	@Rule(order = 1) public final androidx.test.rule.ActivityTestRule<BackupActivity> activity =
			new InventoryActivityRule<>(BackupActivity.class);

	@Rule(order = 2) public final TestRule backupService =
			new BackupServiceInBackupActivityIdlingRule(activity);

	@Rule(order = 3) public final TemporaryFolder temp = new TemporaryFolder();

	private final BackupActivityActor backup = new BackupActivityActor();

	@Before public void assertBackupActivityIsClean() {
		backup.assertEmptyState();
	}

	@Test public void test() throws Exception {
		// Make a failure, ensure takeScreenshot in defaultfailurehandler, and then look for additional files
//		https://github.com/android/android-test/issues/1584
//		https://www.repeato.app/espresso-how-to-take-a-screenshot-when-a-test-fails/
//		https://issuetracker.google.com/issues/156065464

		onView(withId(R.id.progress)).check(matches(isDisplayed()));
	}

	@SdkSuppress(minSdkVersion = UI_AUTOMATOR_VERSION)
	@Test public void testExportCompletes() throws Exception {
		BackupExportPickerActor.assumeFunctional();
		BackupExportPickerActor pickerActor = backup.exportBackup();
		pickerActor.selectInDrawer("Downloads");

		BackupExportResultActor resultActor = pickerActor.save();

		resultActor.dismiss();
		// check progress also disappeared TODO check it was ever displayed
		backup.assertNoProgressDisplayed();
	}

	@SdkSuppress(minSdkVersion = UI_AUTOMATOR_VERSION)
	@Category({Op.Rotates.class, On.External.class})
	@Test public void testExportCompletesWhenPickerRotated() throws Exception {
		BackupExportPickerActor.assumeFunctional();
		BackupExportPickerActor pickerActor = backup.exportBackup();
		pickerActor.selectInDrawer("Downloads");

		try {
			UiAutomatorExtensions.rotateDevice();
			// On API 26 Google APIs emulator the IME comes back.
			UiAutomatorExtensions.ensureNoSoftKeyboard();
			pickerActor.assertDisplayed();
			pickerActor.save().dismiss();
		} finally {
			UiAutomatorExtensions.stopRotateDevice();
		}
	}

	@SdkSuppress(minSdkVersion = UI_AUTOMATOR_VERSION)
	@Category({Op.Cancels.class, Op.Rotates.class, On.External.class})
	@Test public void testExportPickerDoesNotReappearWhenRotated() throws Exception {
		BackupExportPickerActor pickerActor = backup.exportBackup();
		pickerActor.cancel();

		backup.rotate();

		pickerActor.assertNotDisplayed();
	}

	@SdkSuppress(minSdkVersion = UI_AUTOMATOR_VERSION)
	@Category({Op.Cancels.class, Op.Rotates.class, On.External.class})
	@Test public void testExportProgressDoesNotAppearWhenRotated() throws Exception {
		BackupExportPickerActor pickerActor = backup.exportBackup();
		pickerActor.cancel();

		backup.rotate();

		backup.assertNoProgressDisplayed();
	}
}