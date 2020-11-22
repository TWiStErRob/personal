package net.twisterrob.inventory.android.activity;

import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.slf4j.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import net.twisterrob.inventory.android.App;
import net.twisterrob.inventory.android.test.InventoryActivityRule;
import net.twisterrob.inventory.android.test.actors.*;
import net.twisterrob.inventory.android.test.actors.MainActivityActor.Navigator.HomeRoomsActor;
import net.twisterrob.inventory.android.test.categories.*;

@RunWith(AndroidJUnit4.class)
public class ImageLoadingTest {
	private static final Logger LOG = LoggerFactory.getLogger(ImageLoadingTest.class);
	@Rule public final ActivityTestRule<MainActivity> activity
			= new InventoryActivityRule<MainActivity>(MainActivity.class) {
		@Override protected void setDefaults() {
			super.setDefaults();
			App.db().resetToTest();
		}
	};
	private final MainActivityActor main = new MainActivityActor();

	@Category({UseCase.Complex.class, On.Main.class, On.Item.class})
	@Test public void test() {
		MainActivityActor.Navigator home = main.assertHomeScreen();
		HomeRoomsActor rooms = home.rooms();
		rooms.assertExists("!All Categories");
		RoomViewActivityActor room = rooms.open("!All Categories");
		GridBelongingActor item = room.item("Vehicle water");
		item.assertHasImage();
		item.assertHasTypeImage();
		item.changeCategory().cancel();
//		room.close();
	}
}
