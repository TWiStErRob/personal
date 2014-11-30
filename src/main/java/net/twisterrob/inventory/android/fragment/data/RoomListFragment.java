package net.twisterrob.inventory.android.fragment.data;

import java.util.Collection;

import org.slf4j.*;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.RecyclerView;
import android.view.*;

import net.twisterrob.inventory.android.*;
import net.twisterrob.inventory.android.activity.data.MoveTargetActivity;
import net.twisterrob.inventory.android.content.Loaders;
import net.twisterrob.inventory.android.content.contract.*;
import net.twisterrob.inventory.android.fragment.data.RoomListFragment.RoomsEvents;
import net.twisterrob.inventory.android.tasks.*;
import net.twisterrob.inventory.android.view.Dialogs;

public class RoomListFragment extends BaseGalleryFragment<RoomsEvents> {
	private static final Logger LOG = LoggerFactory.getLogger(RoomListFragment.class);

	public interface RoomsEvents {
		void newRoom(long propertyID);
		void roomSelected(long roomID);
		void roomActioned(long roomID);
	}

	public RoomListFragment() {
		setDynamicResource(DYN_EventsClass, RoomsEvents.class);
		setDynamicResource(DYN_OptionsMenu, R.menu.room_list);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		menu.findItem(R.id.action_room_add).setVisible(canCreateNew());
	}

	@Override public boolean onCreateActionMode(ActionMode mode, Menu menu) {
		mode.getMenuInflater().inflate(R.menu.room_bulk, menu);
		return super.onCreateActionMode(mode, menu);
	}

	@Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_room_delete:
				delete(getSelectedIDs());
				return true;
			case R.id.action_room_move:
				startActivityForResult(MoveTargetActivity.pick(MoveTargetActivity.PROPERTY), PICK_REQUEST);
				return true;
		}
		return super.onActionItemClicked(mode, item);
	}

	public static final int PICK_REQUEST = 1;

	@Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == PICK_REQUEST && resultCode == MoveTargetActivity.PROPERTY) {
			long propertyID = data.getLongExtra(Extras.PROPERTY_ID, Property.ID_ADD);
			move(propertyID, getSelectedIDs());
			return;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_room_add:
				onCreateNew();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override protected boolean canCreateNew() {
		return getArgPropertyID() != Property.ID_ADD;
	}

	@Override public void onCreateNew() {
		eventsListener.newRoom(getArgPropertyID());
	}

	@Override protected void onListItemLongClick(RecyclerView.ViewHolder holder) {
		eventsListener.roomActioned(holder.getItemId());
	}

	@Override protected void onListItemClick(RecyclerView.ViewHolder holder) {
		eventsListener.roomSelected(holder.getItemId());
	}

	@Override
	protected void onStartLoading() {
		super.onStartLoading();
		Bundle args = new Bundle();
		args.putLong(Extras.PROPERTY_ID, getArgPropertyID());
		getLoaderManager().initLoader(Loaders.Rooms.ordinal(), args, createListLoaderCallbacks());
	}

	@Override
	protected void onRefresh() {
		super.onRefresh();
		getLoaderManager().getLoader(Loaders.Rooms.ordinal()).forceLoad();
	}

	private void delete(final Collection<Long> roomIDs) {
		new DeleteRoomTask(roomIDs, new Dialogs.Callback() {
			public void dialogSuccess() {
				finishActionMode();
				refresh();
			}

			public void dialogFailed() {
				App.toast("Cannot delete rooms: " + roomIDs);
			}
		}).displayDialog(getActivity());
	}
	private void move(final long propertyID, final Collection<Long> roomIDs) {
		if (propertyID == getArgPropertyID()) {
			App.toast("Cannot move rooms to the same property where they are.");
			return;
		}
		new MoveRoomTask(propertyID, roomIDs, new Dialogs.Callback() {
			public void dialogFailed() {
				App.toast("Cannot move " + roomIDs + " to property #" + propertyID);
			}
			public void dialogSuccess() {
				finishActionMode();
				refresh();
			}
		}).displayDialog(getActivity());
	}

	private long getArgPropertyID() {
		return getArguments().getLong(Extras.PROPERTY_ID, Property.ID_ADD);
	}

	public static RoomListFragment newInstance(long propertyID) {
		RoomListFragment fragment = new RoomListFragment();

		Bundle args = new Bundle();
		args.putLong(Extras.PROPERTY_ID, propertyID);

		fragment.setArguments(args);
		return fragment;
	}
}
