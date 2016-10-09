package net.twisterrob.inventory.android.activity;

import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import static android.support.test.espresso.action.ViewActions.*;
import static android.support.test.espresso.matcher.ViewMatchers.*;

import net.twisterrob.inventory.android.R;
import net.twisterrob.inventory.android.activity.data.ItemViewActivity;
import net.twisterrob.inventory.android.content.*;
import net.twisterrob.inventory.android.test.InventoryActivityRule;
import net.twisterrob.inventory.android.test.actors.*;
import net.twisterrob.inventory.android.test.categories.*;

import static net.twisterrob.android.test.espresso.EspressoExtensions.*;
import static net.twisterrob.inventory.android.content.Constants.*;

@RunWith(AndroidJUnit4.class)
@Category({On.Item.class, UseCase.Selection.class})
public class ItemViewActivityTest_Selection {
	@Rule public final ActivityTestRule<ItemViewActivity> activity
			= new InventoryActivityRule<ItemViewActivity>(ItemViewActivity.class) {
		@Override protected void setDefaults() {
			super.setDefaults();
			itemID = db.create(TEST_PROPERTY, TEST_ROOM, TEST_ITEM);
			getStartIntent().putExtras(Intents.bundleFromParent(itemID));
		}
	};
	@Rule public final DataBaseActor db = new DataBaseActor();
	private final ItemViewActivityActor itemView = new ItemViewActivityActor();
	private long itemID;

	@Category({UseCase.InitialCondition.class})
	@Test public void testSelectionModeNotStarted() {
		createItems(3);
		SelectionActor selection = new SelectionActor();
		selection.assertNothingSelected();
		selection.assertInactive();
	}

	@Test public void testSelectionModeStarts() {
		SelectionActor selection = createItemsAndSelect(1, 1);
		selection.hasSelection(subItem(1));
		selection.assertSelectionCount(1);
	}

	@Category({Op.Cancels.class})
	@Test public void testSelectionModeExits() {
		SelectionActor selection = createItemsAndSelect(1, 1);
		selection.close();
		selection.assertNothingSelected();
	}

	@Category({Op.Cancels.class})
	@Test public void testSelectionModeExitsWhenLastDeselectedSingle() {
		SelectionActor selection = createItemsAndSelect(1, 1);
		selection.deselect(subItem(1));
		selection.assertNothingSelected();
		selection.assertInactive();
	}

	@Category({Op.Cancels.class})
	@Test public void testSelectionModeExitsWhenLastDeselected() {
		SelectionActor selection = createItemsAndSelect(2, 1);
		selection.deselect(subItem(1));
		selection.assertNothingSelected();
		selection.assertInactive();
	}

	@Category({Op.Cancels.class})
	@Test public void testSelectionModeExitsWhenLastDeselectedMultiple() {
		SelectionActor selection = createItemsAndSelect(9, 1);
		selection.select(subItem(3));
		selection.select(subItem(6));
		selection.deselect(subItem(6));
		selection.deselect(subItem(3));
		selection.deselect(subItem(1));
		selection.assertNothingSelected();
		selection.assertInactive();
	}

	@Category({Op.Cancels.class, UseCase.Complex.class})
	@Test public void testSelectionModeExitsRandomSelectionAll() {
		final int count = 9;
		SelectionActor selection = createItemsAndSelect(count, 1);
		for (int i = 2; i <= count; i++) {
			selection.select(subItem(i));
		}
		for (int i = 1; i <= count; i++) {
			selection.deselect(subItem(i));
		}
		selection.assertNothingSelected();
		selection.assertInactive();
	}

	@Test public void testSelectionModeSelectAll() {
		SelectionActor selection = createItemsAndSelect(4, 2);
		selection.selectAll();
		selection.assertSelectionCount(4);
	}

	@Category({Op.Cancels.class})
	@Test public void testSelectionModeSelectNone() {
		SelectionActor selection = createItemsAndSelect(4, 2);
		selection.selectAll();
		selection.invertSelection();
		selection.assertInactive();
	}

	@Test public void testSelectionDisablesTypeChange() {
		SelectionActor selection = createItemsAndSelect(2, 1);
		selection.hasSelection(subItem(1));
		selection.assertSelectionCount(1);

		selection.hasNoSelection(subItem(2));
		onRecyclerItem(withText(subItem(2))).onChildView(withId(R.id.type)).perform(click());
		selection.hasSelection(subItem(2));
		selection.assertSelectionCount(2);

		selection.hasSelection(subItem(1));
		onRecyclerItem(withText(subItem(1))).onChildView(withId(R.id.type)).perform(click());
		selection.hasNoSelection(subItem(1));
		selection.assertSelectionCount(1);
	}

	@Category({UseCase.Complex.class})
	@Test public void testSelectionTypeChange() {
		createItems(3);
		db.assertItemHasType(subItem(1), TEST_ITEM_CATEGORY_DEFAULT);
		db.setItemCategory(subItem(2), TEST_ITEM_CATEGORY);
		db.setItemCategory(subItem(3), TEST_ITEM_CATEGORY_OTHER);
		itemView.refresh();
		SelectionActor select = itemView.select(subItem(2));
		select.select(subItem(3));
		select.select(subItem(1));
		ChangeTypeDialogActor typeDialog = select.changeType();
		typeDialog.assertNoneSelected();
		typeDialog.select(TEST_ITEM_CATEGORY_OTHER);
		typeDialog.assertClosed();
		select.assertInactive();
		db.assertItemHasType(subItem(1), TEST_ITEM_CATEGORY_OTHER);
		db.assertItemHasType(subItem(2), TEST_ITEM_CATEGORY_OTHER);
		db.assertItemHasType(subItem(3), TEST_ITEM_CATEGORY_OTHER);
	}

	private SelectionActor createItemsAndSelect(int count, int selected) {
		createItems(count);
		return itemView.select(subItem(selected));
	}
	private void createItems(int count) {
		for (int i = 1; i <= count; i++) {
			db.createItem(itemID, subItem(i));
		}
		itemView.refresh();
	}
	private static String subItem(int i) {
		return TEST_SUBITEM + " " + i;
	}
}
