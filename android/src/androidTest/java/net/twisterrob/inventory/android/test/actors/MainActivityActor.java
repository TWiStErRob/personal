package net.twisterrob.inventory.android.test.actors;

import org.hamcrest.*;

import static org.hamcrest.Matchers.*;

import android.support.annotation.*;
import android.support.design.internal.NavigationMenuItemView;
import android.support.design.widget.NavigationView;
import android.support.test.espresso.Espresso;
import android.support.test.espresso.matcher.BoundedMatcher;
import android.view.*;

import static android.support.test.espresso.Espresso.*;
import static android.support.test.espresso.action.ViewActions.*;
import static android.support.test.espresso.assertion.ViewAssertions.*;
import static android.support.test.espresso.matcher.RootMatchers.*;
import static android.support.test.espresso.matcher.ViewMatchers.*;

import net.twisterrob.inventory.android.R;
import net.twisterrob.inventory.android.activity.MainActivity;

import static net.twisterrob.android.test.espresso.DialogMatchers.*;
import static net.twisterrob.android.test.espresso.DrawerMatchers.*;
import static net.twisterrob.android.test.espresso.EspressoExtensions.*;
import static net.twisterrob.android.test.matchers.AndroidMatchers.*;

public class MainActivityActor extends ActivityActor {
	public MainActivityActor() {
		super(MainActivity.class);
	}

	public Navigator assertHomeScreen() {
		return new Navigator();
	}

	public WelcomeDialogActor assertWelcomeShown() {
		onView(withText(R.string.welcome_title)).inRoot(isDialog()).check(matches(isCompletelyDisplayed()));
		return new WelcomeDialogActor();
	}

	public PropertiesNavigator openProperties() {
		return open(new PropertiesNavigator());
	}
	public RoomsNavigator openRooms() {
		return open(new RoomsNavigator());
	}
	public ItemsNavigator openItems() {
		return open(new ItemsNavigator());
	}
	public CategoriesNavigator openCategories() {
		return open(new CategoriesNavigator());
	}
	public SunburstNavigator openSunburst() {
		return open(new SunburstNavigator());
	}
	public BackupNavigator openBackup() {
		return open(new BackupNavigator());
	}
	public SettingsNavigator openSettings() {
		return open(new SettingsNavigator());
	}
	public AboutNavigator openAbout() {
		return open(new AboutNavigator());
	}
	public SearchActor openSearch() {
		SearchActor actor = new SearchActor();
		actor.open();
		return actor;
	}

	private <T extends DrawerNavigator> T open(T actor) {
		actor.open();
		actor.checkOpened();
		return actor;
	}
	public void openMenu() {
		onView(isDrawer()).perform(openDrawer());
		onView(isDrawerLayout()).check(matches(isAnyDrawerOpen()));
	}
	public void assertMenuClosed() {
		onView(isDrawerLayout()).check(matches(areBothDrawersClosed()));
	}

	public static class WelcomeDialogActor {
		public void dontPopulateDemo() {
			clickNegativeInDialog();
			checkDismissed();
		}
		public void populateDemo() {
			clickPositiveInDialog();
			checkDismissed();
		}
		public BackupActivityActor invokeBackup() {
			clickNeutralInDialog();
			checkDismissed();
			return new BackupActivityActor();
		}
		private void checkDismissed() {
			assertNoDialogIsDisplayed();
		}
	}

	public static class Navigator extends DrawerNavigator {
		@Override public void checkOpened() {
			assertOpened(R.string.home_title, R.string.home_title, R.id.properties);
			assertOpened(R.string.home_title, R.string.home_title, R.id.rooms);
			assertOpened(R.string.home_title, R.string.home_title, R.id.items);
			assertOpened(R.string.home_title, R.string.home_title, R.id.lists);
		}
		@Override protected void open() {
			selectDrawerItem(R.string.home_title);
		}
	}

	public static class PropertiesNavigator extends DrawerNavigator {
		@Override public void checkOpened() {
			assertOpened(R.string.property_list, R.string.property_list, android.R.id.list);
		}
		@Override protected void open() {
			selectDrawerItem(R.string.property_list);
		}
		public PropertyEditActivityActor addProperty() {
			onView(withId(R.id.fab)).perform(click());
			return new PropertyEditActivityActor();
		}
		public void hasProperty(String propertyName) {
			onRecyclerItem(withText(propertyName))
					.inAdapterView(withId(android.R.id.list))
					.check(matches(isCompletelyDisplayed()));
		}
		public void hasNoProperties() {
			onView(withId(android.R.id.list)).check(itemDoesNotExists(anyView()));
		}
	}

	public static class RoomsNavigator extends DrawerNavigator {
		@Override protected void open() {
			selectDrawerItem(R.string.room_list);
		}
		@Override public void checkOpened() {
			assertOpened(R.string.room_list, R.string.room_list, android.R.id.list);
		}
	}

	public static class ItemsNavigator extends DrawerNavigator {
		@Override protected void open() {
			selectDrawerItem(R.string.item_list);
		}
		@Override public void checkOpened() {
			assertOpened(R.string.item_list, R.string.item_list, android.R.id.list);
		}
	}

	public static class CategoriesNavigator extends DrawerNavigator {
		@Override protected void open() {
			selectDrawerItem(R.string.category_list);
		}
		@Override public void checkOpened() {
			assertOpened(R.string.category_list, R.string.category_list, android.R.id.list);
		}
	}

	public static class SunburstNavigator extends DrawerNavigator {
		@Override protected void open() {
			selectDrawerItem(R.string.sunburst_title);
		}
		@Override public void checkOpened() {
			assertOpened(R.string.sunburst_title, R.string.sunburst_title, R.id.diagram);
		}
		public SunburstActivityActor asActor() {
			return new SunburstActivityActor();
		}
	}

	public static class BackupNavigator extends DrawerNavigator {
		@Override protected void open() {
			selectDrawerItem(R.string.backup_title);
		}
		@Override public void checkOpened() {
			onView(isDrawerLayout()).check(doesNotExist());
			onView(isActionBarTitle()).check(matches(allOf(isCompletelyDisplayed(), withText(R.string.backup_title))));
			onView(withId(R.id.backups)).check(matches(isCompletelyDisplayed()));
		}
		public BackupActivityActor asActor() {
			return new BackupActivityActor();
		}
	}

	public static class SettingsNavigator extends DrawerNavigator {
		@Override protected void open() {
			selectDrawerItem(R.string.pref_activity_title);
		}
		@Override public void checkOpened() {
			onView(isDrawerLayout()).check(doesNotExist());
			onView(isActionBarTitle())
					.check(matches(allOf(isCompletelyDisplayed(), withText(R.string.pref_activity_title))));
			onView(withId(R.id.backups)).check(matches(isCompletelyDisplayed()));
		}
		public PreferencesActivityActor asActor() {
			return new PreferencesActivityActor();
		}
	}

	public static class AboutNavigator extends DrawerNavigator {
		@Override protected void open() {
			selectDrawerItem(R.string.about_title);
		}
		@Override public void checkOpened() {
			onView(isDrawerLayout()).check(doesNotExist());
			onView(isActionBarTitle()).check(doesNotExist());
			onView(withId(R.id.about_name)).check(matches(withText(R.string.app_name)));
		}
		public AboutActivityActor asActor() {
			return new AboutActivityActor();
		}
	}

	private static abstract class DrawerNavigator {
		public void tryClose() {
			Espresso.pressBack();
		}
		protected abstract void open();
		public abstract void checkOpened();

		protected void selectDrawerItem(@StringRes int drawerItem) {
			onOpenDrawerDescendant(withText(drawerItem)).perform(click());
		}

		protected void assertOpened(@StringRes int drawerItem, @StringRes int actionBarTitle, @IdRes int checkView) {
			onView(isDrawerLayout()).check(matches(areBothDrawersClosed()));
			onView(isActionBarTitle()).check(matches(allOf(isCompletelyDisplayed(), withText(actionBarTitle))));
			onView(withId(checkView)).check(matches(isCompletelyDisplayed()));
			onDrawerDescendant(withText(drawerItem)).check(matches(navigationItemIsHighlighted()));
		}

		private static Matcher<View> navigationItemIsHighlighted() {
			return new BoundedMatcher<View, View>(View.class) {
				@Override public void describeTo(Description description) {
					description.appendText("navigation item is the only checked one");
				}
				@Override protected boolean matchesSafely(View view) {
					View parent = view;
					while (!(parent instanceof NavigationMenuItemView)) {
						if (parent instanceof NavigationView) {
							throw new IllegalStateException(
									"Went too high in hierarchy, cannot find parent menu item view.");
						}
						parent = (View)parent.getParent();
					}
					MenuItem item = ((NavigationMenuItemView)parent).getItemData();
					Menu menu = ((NavigationView)parent.getParent().getParent()).getMenu();
					for (int i = 0; i < menu.size(); i++) {
						MenuItem subItem = menu.getItem(i);
						if (subItem != item && subItem.isChecked()) {
							return false;
						}
					}
					return item.isChecked();
				}
			};
		}
	}
}
