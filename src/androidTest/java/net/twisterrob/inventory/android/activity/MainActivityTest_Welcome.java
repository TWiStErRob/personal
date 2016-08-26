package net.twisterrob.inventory.android.activity;

import org.junit.*;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.*;

import android.support.test.espresso.ViewAssertion;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;

import static android.support.test.espresso.Espresso.*;
import static android.support.test.espresso.assertion.ViewAssertions.*;
import static android.support.test.espresso.intent.Intents.*;
import static android.support.test.espresso.intent.matcher.IntentMatchers.*;
import static android.support.test.espresso.matcher.RootMatchers.isDialog;
import static android.support.test.espresso.matcher.ViewMatchers.*;

import net.twisterrob.inventory.android.R;
import net.twisterrob.inventory.android.test.InventoryActivityTestRule;

import static net.twisterrob.android.test.DialogMatchers.*;

@RunWith(AndroidJUnit4.class)
public class MainActivityTest_Welcome {

	private static final ViewAssertion NON_EMPTY =
			matches(allOf(isDisplayed(), hasDescendant(notNullValue(View.class))));
	private static final ViewAssertion EMPTY =
			matches(allOf(isDisplayed(), not(hasDescendant(notNullValue(View.class)))));

	@Rule public ActivityTestRule<MainActivity> activity = new InventoryActivityTestRule<>(MainActivity.class);

	@Test public void testWelcomeDontPopulateDemo() {
		onView(withText(R.string.welcome_title)).inRoot(isDialog()).check(matches(isDisplayed()));
		clickNegativeInDialog();

		onView(withId(R.id.properties)).check(EMPTY);
		onView(withId(R.id.rooms)).check(EMPTY);
		onView(withId(R.id.lists)).check(EMPTY);
		onView(withId(R.id.items)).check(EMPTY);
	}

	@Test public void testWelcomePopulateDemo() {
		onView(withText(R.string.welcome_title)).inRoot(isDialog()).check(matches(isDisplayed()));
		clickPositiveInDialog();

		onView(withId(R.id.properties)).check(NON_EMPTY);
		onView(withId(R.id.rooms)).check(NON_EMPTY);
		onView(withId(R.id.lists)).check(NON_EMPTY);
		onView(withId(R.id.items)).check(EMPTY);
	}

	@Test public void testWelcomeBackup() {
		onView(withText(R.string.welcome_title)).inRoot(isDialog()).check(matches(isDisplayed()));
		clickNeutralInDialog();
		intended(allOf(isInternal(), hasComponent(BackupActivity.class.getName())));
	}
}