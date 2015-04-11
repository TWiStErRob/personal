package net.twisterrob.inventory.android.content.io.xml;

import java.io.*;
import java.util.*;

import org.slf4j.*;
import org.xml.sax.*;

import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.sax.*;
import android.util.Xml;

import net.twisterrob.inventory.android.*;
import net.twisterrob.inventory.android.content.contract.*;
import net.twisterrob.inventory.android.content.io.*;
import net.twisterrob.inventory.android.content.model.Types;

public class XMLImporter implements Importer {
	private static final Logger LOG = LoggerFactory.getLogger(XMLImporter.class);
	private final ImportProgressHandler progress;
	private final Types types = new Types();
	private final Map<Long, Long> itemMap = new TreeMap<>();

	public XMLImporter(ImportProgressHandler progress) {
		this.progress = progress;
	}

	public void doImport(InputStream stream) throws Throwable {
		RootElement structure = getStructure();
		Xml.parse(stream, Xml.Encoding.UTF_8, structure.getContentHandler());
	}

	public RootElement getStructure() throws IOException, SAXException {
		RootElement root = new RootElement("inventory");
		Element propertyElement = root.getChild("property");
		final Element roomElement = propertyElement.getChild("room");
		final Element listElement = root.getChild("list");
		Element listEntryElement = listElement.getChild("item-ref");

		final PropertyElementListener propertyListener = new PropertyElementListener();
		RoomElementListener roomListener = new RoomElementListener(propertyListener, new TraverseFactory() {
			private Element element = roomElement;
			private int deepestLevel = propertyListener.getLevel();
			public void onNewLevel(Parent parent) {
				if (parent.getLevel() > deepestLevel) {
					element = element.getChild("item");
					ItemElementListener childListener = new ItemElementListener(parent, this);
					element.setElementListener(childListener);
					element.getChild("description").setEndTextElementListener(childListener);
					deepestLevel = parent.getLevel();
				}
			}
		});

		propertyElement.setElementListener(propertyListener);
		propertyElement.getChild("description").setEndTextElementListener(propertyListener);

		roomElement.setElementListener(roomListener);
		roomElement.getChild("description").setEndTextElementListener(roomListener);

		final ThreadLocal<Long> currentListID = new ThreadLocal<>();
		listElement.setElementListener(new ElementListener() {
			@Override public void start(Attributes attributes) {
				String name = attributes.getValue("name");
				Long id = App.db().findList(name);
				if (id == null) {
					id = App.db().createList(name);
				}
				currentListID.set(id);
			}
			@Override public void end() {
				currentListID.remove();
			}
		});
		listEntryElement.setElementListener(new ElementListener() {
			@Override public void start(Attributes attributes) {
				long id = currentListID.get();
				long itemID = Long.parseLong(attributes.getValue("id"));
				Long dbItemID = itemMap.get(itemID);
				if (dbItemID == null) {
					throw new IllegalArgumentException("Invalid item reference to id=" + itemID);
				}
				try {
					App.db().addListEntry(id, dbItemID);
				} catch (SQLiteConstraintException ex) {
					if (!ex.getMessage().contains("19")) {
						throw ex;
					} else {
						progress.warning(R.string.backup_import_invalid_image, "", "", "");
					}
				}
			}
			@Override public void end() {

			}
		});

		root.setStartElementListener(new StartElementListener() {
			@Override public void start(Attributes attributes) {
				String count = attributes.getValue("approximateCount");
				if (count != null) {
					progress.publishStart(Long.parseLong(count));
				}
			}
		});

		return root;
	}

	private abstract class BaseElementListener implements ElementListener, EndTextElementListener, Parent {
		protected final Parent parent;
		private TraverseFactory factory;
		private int level;

		protected Long id;

		protected String name;
		protected String type;
		protected String image;
		protected String description;

		public BaseElementListener(Parent parent, TraverseFactory factory) {
			LOG.trace("{}", getClass().getSimpleName());
			this.parent = parent;
			this.factory = factory;
			this.level = parent.getLevel() + 1;
		}

		@Override
		public void start(Attributes attributes) {
			type = attributes.getValue("type");
			name = attributes.getValue("name");
			image = attributes.getValue("image");
			factory.onNewLevel(this);
			process();
		}

		@Override public void end(String body) {
			description = body;
		}

		@Override public void end() {
			process();
			id = null;
			name = null;
			type = null;
			image = null;
			description = null;
		}

		private void process() {
			if (id != null) {
				return;
			}
			doProcess();
			progress.publishIncrement();
		}
		protected abstract void doProcess();

		@Override public long getID() {
			if (id == null) {
				process();
			}
			return id;
		}

		public int getLevel() {
			return level;
		}
	}

	private interface Parent {
		long getID();
		int getLevel();
	}

	private class PropertyElementListener extends BaseElementListener {
		public PropertyElementListener() {
			super(new Parent() {
				@Override public long getID() {
					return -1;
				}
				@Override public int getLevel() {
					return 0;
				}
			}, new TraverseFactory() {
				@Override public void onNewLevel(Parent parent) {
					// no op
				}
			});
		}

		@Override protected void doProcess() {
			id = App.db().findProperty(name);
			if (id == null) {
				Long typeID = types.getID(type);
				if (typeID == null) {
					progress.warning(R.string.backup_import_invalid_type, type, name);
					typeID = PropertyType.DEFAULT;
				}
				id = App.db().createProperty(typeID, name, description, image);
			} else {
				progress.warning(R.string.backup_import_conflict_property, name);
			}
		}
	}

	private class RoomElementListener extends BaseElementListener {
		public RoomElementListener(Parent parent, TraverseFactory factory) {
			super(parent, factory);
		}

		@Override protected void doProcess() {
			Long propertyID = parent.getID();
			id = App.db().findRoom(propertyID, name);
			if (id == null) {
				Long typeID = types.getID(type);
				if (typeID == null) {
					progress.warning(R.string.backup_import_invalid_type, type, name);
					typeID = RoomType.DEFAULT;
				}
				id = App.db().createRoom(propertyID, typeID, name, description, image);
			} else {
				progress.warning(R.string.backup_import_conflict_room, null, name);
			}
			rootID = getRoot(id);
		}

		private long getRoot(long roomID) {
			Cursor room = App.db().getRoom(roomID);
			try {
				room.moveToFirst();
				return room.getLong(room.getColumnIndexOrThrow(Room.ROOT_ITEM));
			} finally {
				room.close();
			}
		}

		long rootID;

		@Override public void end() {
			super.end();
			rootID = 0;
		}
		@Override public long getID() {
			super.getID();
			return rootID;
		}
	}

	public interface TraverseFactory {
		void onNewLevel(Parent parent);
	}

	private class ItemElementListener extends BaseElementListener {
		long refID;

		public ItemElementListener(Parent parent, TraverseFactory factory) {
			super(parent, factory);
		}

		@Override public void start(Attributes attributes) {
			refID = Long.parseLong(attributes.getValue("id"));
			super.start(attributes);
		}
		@Override protected void doProcess() {
			Long parentID = parent.getID();
			id = App.db().findItem(parentID, name);
			if (id == null) {
				Long typeID = types.getID(type);
				if (typeID == null) {
					progress.warning(R.string.backup_import_invalid_type, type, name);
					typeID = Category.DEFAULT;
				}
				id = App.db().createItem(parentID, typeID, name, description, image);
			} else {
				progress.warning(R.string.backup_import_conflict_item, null, null, name);
			}
			itemMap.put(refID, id);
		}
	}
}
