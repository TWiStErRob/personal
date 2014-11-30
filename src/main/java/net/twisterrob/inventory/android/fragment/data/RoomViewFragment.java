package net.twisterrob.inventory.android.fragment.data;

import java.util.Arrays;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.MenuItem;

import net.twisterrob.android.utils.tools.TextTools.DescriptionBuilder;
import net.twisterrob.inventory.android.*;
import net.twisterrob.inventory.android.activity.data.*;
import net.twisterrob.inventory.android.content.contract.*;
import net.twisterrob.inventory.android.content.model.RoomDTO;
import net.twisterrob.inventory.android.fragment.data.RoomViewFragment.RoomEvents;
import net.twisterrob.inventory.android.tasks.*;
import net.twisterrob.inventory.android.view.Dialogs;

import static net.twisterrob.inventory.android.content.Loaders.*;

public class RoomViewFragment extends BaseViewFragment<RoomDTO, RoomEvents> {
	private static final int MOVE_REQUEST = 0;

	public interface RoomEvents {
		void roomLoaded(RoomDTO room);
		void roomDeleted(RoomDTO room);
	}

	public RoomViewFragment() {
		setDynamicResource(DYN_EventsClass, RoomEvents.class);
		setDynamicResource(DYN_OptionsMenu, R.menu.room);
	}

	@Override
	protected void onRefresh() {
		super.onRefresh();
		getLoaderManager().getLoader(SingleRoom.ordinal()).forceLoad();
	}

	@Override
	protected void onStartLoading() {
		super.onStartLoading();
		Bundle args = new Bundle();
		args.putLong(Extras.ROOM_ID, getArgRoomID());
		getLoaderManager().initLoader(SingleRoom.ordinal(), args, new SingleRowLoaded());
	}

	@Override
	protected void onSingleRowLoaded(Cursor cursor) {
		RoomDTO room = RoomDTO.fromCursor(cursor);
		super.onSingleRowLoaded(room);
		eventsListener.roomLoaded(room);
	}

	@Override
	protected CharSequence getDetailsString(RoomDTO entity) {
		return new DescriptionBuilder()
				.append("Room ID", entity.id, BuildConfig.DEBUG)
				.append("Room Name", entity.name)
				.append("Room Type", entity.type, BuildConfig.DEBUG)
				.append("Room Root", entity.rootItemID)
				.append("Property ID", entity.propertyID, BuildConfig.DEBUG)
				.append("In property", entity.propertyName)
				.append("# of items in the room", entity.numDirectItems)
				.append("# of items inside", entity.numAllItems)
				.append("image", entity.image, BuildConfig.DEBUG)
				.build();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_room_edit:
				startActivity(RoomEditActivity.edit(getArgRoomID()));
				return true;
			case R.id.action_room_delete:
				delete(getArgRoomID());
				return true;
			case R.id.action_room_move:
				startActivityForResult(MoveTargetActivity.pick(MoveTargetActivity.PROPERTY), MOVE_REQUEST);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == MOVE_REQUEST && resultCode == MoveTargetActivity.PROPERTY) {
			long propertyID = data.getLongExtra(Extras.PROPERTY_ID, Property.ID_ADD);
			move(getArgRoomID(), propertyID);
			return;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	private void move(final long roomID, final long propertyID) {
		new MoveRoomTask(propertyID, Arrays.asList(roomID), new Dialogs.Callback() {
			public void dialogFailed() {
				App.toast("Cannot move room #" + roomID + " to property #" + propertyID);
			}
			public void dialogSuccess() {
				// TODO move event
				startActivity(PropertyViewActivity.show(propertyID));
				getActivity().finish();
			}
		}).displayDialog(getActivity());
	}

	private void delete(final long roomID) {
		new DeleteRoomTask(Arrays.asList(roomID), new Dialogs.Callback() {
			public void dialogSuccess() {
				RoomDTO room = new RoomDTO();
				room.id = roomID;
				eventsListener.roomDeleted(room);
			}

			public void dialogFailed() {
				App.toast("Cannot delete room #" + roomID);
			}
		}).displayDialog(getActivity());
	}

	private long getArgRoomID() {
		return getArguments().getLong(Extras.ROOM_ID, Room.ID_ADD);
	}

	public static RoomViewFragment newInstance(long roomID) {
		if (roomID == Room.ID_ADD) {
			throw new IllegalArgumentException("Must be an existing room");
		}

		RoomViewFragment fragment = new RoomViewFragment();

		Bundle args = new Bundle();
		args.putLong(Extras.ROOM_ID, roomID);

		fragment.setArguments(args);
		return fragment;
	}
}
