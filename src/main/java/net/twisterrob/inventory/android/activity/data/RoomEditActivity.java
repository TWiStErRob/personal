package net.twisterrob.inventory.android.activity.data;

import android.content.Intent;
import android.os.Bundle;

import net.twisterrob.inventory.android.*;
import net.twisterrob.inventory.android.content.contract.*;
import net.twisterrob.inventory.android.content.model.RoomDTO;
import net.twisterrob.inventory.android.fragment.data.RoomEditFragment;

public class RoomEditActivity extends BaseEditActivity<RoomEditFragment>
		implements RoomEditFragment.RoomEditEvents {
	@Override
	protected RoomEditFragment onCreateFragment(Bundle savedInstanceState) {
		setActionBarTitle(getString(R.string.room_new));
		return RoomEditFragment.newInstance(getExtraPropertyID(), getExtraRoomID());
	}

	@Override public void roomLoaded(RoomDTO room) {
		setActionBarTitle(room.name);
	}

	@Override public void roomSaved(long roomID) {
		Intent data = ExtrasFactory.intentFromRoom(roomID);
		data.putExtra(Extras.PROPERTY_ID, getExtraPropertyID());
		setResult(RESULT_OK, data);
		finish();
	}

	private long getExtraRoomID() {
		return getIntent().getLongExtra(Extras.ROOM_ID, Room.ID_ADD);
	}

	private long getExtraPropertyID() {
		return getIntent().getLongExtra(Extras.PROPERTY_ID, Property.ID_ADD);
	}

	public static Intent add(long propertyID) {
		Intent intent = new Intent(App.getAppContext(), RoomEditActivity.class);
		intent.putExtra(Extras.PROPERTY_ID, propertyID);
		return intent;
	}

	public static Intent edit(long roomID) {
		Intent intent = new Intent(App.getAppContext(), RoomEditActivity.class);
		intent.putExtra(Extras.ROOM_ID, roomID);
		return intent;
	}
}
