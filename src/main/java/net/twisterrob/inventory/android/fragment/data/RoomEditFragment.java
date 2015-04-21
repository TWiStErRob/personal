package net.twisterrob.inventory.android.fragment.data;

import org.slf4j.*;

import android.database.Cursor;
import android.os.Bundle;
import android.view.View;

import net.twisterrob.android.content.loader.DynamicLoaderManager;
import net.twisterrob.android.content.loader.DynamicLoaderManager.Dependency;
import net.twisterrob.inventory.android.R;
import net.twisterrob.inventory.android.content.Database;
import net.twisterrob.inventory.android.content.contract.*;
import net.twisterrob.inventory.android.content.model.RoomDTO;
import net.twisterrob.inventory.android.fragment.data.RoomEditFragment.RoomEditEvents;
import net.twisterrob.inventory.android.view.CursorSwapper;
import net.twisterrob.java.utils.ObjectTools;

import static net.twisterrob.inventory.android.content.Loaders.*;

public class RoomEditFragment extends BaseEditFragment<RoomEditEvents> {
	private static final Logger LOG = LoggerFactory.getLogger(RoomEditFragment.class);

	public interface RoomEditEvents {
		void roomLoaded(RoomDTO room);
		void roomSaved(long roomID);
	}

	public RoomEditFragment() {
		setDynamicResource(DYN_EventsClass, RoomEditEvents.class);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setKeepNameInSync(true);
	}

	@Override public void onViewCreated(View view, Bundle bundle) {
		super.onViewCreated(view, bundle);
		name.setHint(R.string.room_name_hint);
		description.setHint(R.string.room_description_hint);
	}

	@Override
	protected void onStartLoading() {
		long id = getArgRoomID();

		DynamicLoaderManager manager = new DynamicLoaderManager(getLoaderManager());
		CursorSwapper typeCursorSwapper = new CursorSwapper(getContext(), typeAdapter);
		Dependency<Cursor> populateTypes = manager.add(RoomTypes.id(), null, typeCursorSwapper);

		if (id != Room.ID_ADD) {
			Dependency<Cursor> loadRoomData = manager.add(SingleRoom.id(),
					ExtrasFactory.bundleFromRoom(id), new SingleRowLoaded());
			loadRoomData.dependsOn(populateTypes); // type is auto-selected when a room is loaded
		}

		manager.startLoading();
	}

	@Override
	protected void onSingleRowLoaded(Cursor cursor) {
		RoomDTO room = RoomDTO.fromCursor(cursor);
		onSingleRowLoaded(room);
		eventsListener.roomLoaded(room);
	}

	@Override
	protected void doSave() {
		new SaveTask().execute(getCurrentRoom());
	}

	private RoomDTO getCurrentRoom() {
		RoomDTO room = new RoomDTO();
		room.propertyID = getArgPropertyID();
		room.id = getArgRoomID();
		room.name = name.getText().toString();
		room.description = description.getText().toString();
		room.type = type.getSelectedItemId();
		room.tempImageUri = getCurrentImage();
		return room;
	}

	private long getArgPropertyID() {
		return getArguments().getLong(Extras.PROPERTY_ID, Property.ID_ADD);
	}

	private long getArgRoomID() {
		return getArguments().getLong(Extras.ROOM_ID, Room.ID_ADD);
	}

	private final class SaveTask extends BaseSaveTask<RoomDTO> {
		@Override protected RoomDTO saveInTransaction(Database db, RoomDTO param) throws Exception {
			if (param.id == Room.ID_ADD) {
				param.id = db.createRoom(param.propertyID, param.type, param.name, param.description);
			} else {
				db.updateRoom(param.id, param.type, param.name, param.description);
			}
			if (!ObjectTools.equals(param.getImageUri(), param.tempImageUri)) {
				db.setRoomImage(param.id, param.getImage(getContext()), null);
			}
			return param;
		}

		@Override protected void onResult(RoomDTO result, RoomDTO param) {
			eventsListener.roomSaved(result.id);
		}
	}

	public static RoomEditFragment newInstance(long propertyID, long roomID) {
		if (propertyID == Property.ID_ADD && roomID == Room.ID_ADD) {
			throw new IllegalArgumentException("Property ID / room ID must be provided (new room / edit room)");
		}
		if (roomID != Room.ID_ADD) { // no need to know which property when editing
			propertyID = Property.ID_ADD;
		}

		RoomEditFragment fragment = new RoomEditFragment();

		Bundle args = new Bundle();
		args.putLong(Extras.PROPERTY_ID, propertyID);
		args.putLong(Extras.ROOM_ID, roomID);

		fragment.setArguments(args);
		return fragment;
	}
}
