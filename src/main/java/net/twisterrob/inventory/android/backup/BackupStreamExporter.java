package net.twisterrob.inventory.android.backup;

import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.CancellationException;

import org.slf4j.*;

import android.database.Cursor;
import android.support.annotation.*;

import net.twisterrob.android.utils.tools.IOTools;
import net.twisterrob.inventory.android.App;
import net.twisterrob.inventory.android.backup.Exporter.ExportCallbacks.Progress;
import net.twisterrob.inventory.android.backup.Exporter.ExportCallbacks.Progress.Phase;
import net.twisterrob.inventory.android.content.contract.*;

// FIXME convert to Service
@WorkerThread
public class BackupStreamExporter {
	private static final Logger LOG = LoggerFactory.getLogger(BackupStreamExporter.class);
	public static final String IMAGE_NAME = "imageName";

	private Cursor cursor;
	private Progress progress;
	private final Exporter exporter;
	private final ProgressDispatcher dispatcher;

	public BackupStreamExporter(Exporter exporter, ProgressDispatcher dispatcher) {
		this.exporter = exporter;
		this.dispatcher = dispatcher;
	}

	protected void publishStart() {
		progress.pending = false;
		progress.done = 0;
		publishProgress();
	}
	protected void publishIncrement() {
		progress.pending = false;
		++progress.done;
		publishProgress();
	}
	protected void publishFinishing() {
		progress.pending = true;
		publishProgress();
	}

	protected void publishProgress() throws CancellationException {
		dispatcher.dispatchProgress(progress.clone());
	}

	/**
	 * @throws Throwable never
	 */
	@SuppressWarnings("JavaDoc")
	public @NonNull Progress export(OutputStream os) {
		Progress progress = this.progress = new Progress();

		try {
			progress.phase = Phase.Init;
			publishProgress();
			cursor = App.db().export();
			progress.total = cursor.getCount();
			publishStart();
			exporter.initExport(os);

			saveData();
			saveImages();

			exporter.finishExport();
		} catch (Throwable ex) {
			LOG.warn("Export failed", ex);
			progress.failure = ex;
		} finally {
			IOTools.ignorantClose(cursor);
			exporter.finalizeExport();
			this.progress = null;
		}
		return progress;
	}

	// CONSIDER exporting Google Spreadsheet: http://stackoverflow.com/q/13229294/253468
	protected void saveData() throws Throwable {
		progress.phase = Phase.Data;
		exporter.initData(cursor);

		cursor.moveToPosition(-1);
		publishStart();
		while (cursor.moveToNext()) {
			if (!cursor.isNull(cursor.getColumnIndexOrThrow(IMAGE_NAME))) {
				progress.imagesTotal++;
			}
			exporter.writeData(cursor);
			publishIncrement();
		}
		publishFinishing();
		exporter.finishData(cursor);
	}

	private void saveImages() throws Throwable {
		progress.phase = Phase.Images;
		exporter.initImages(cursor);

		cursor.moveToPosition(-1);
		publishStart();
		while (cursor.moveToNext()) {
			String imageFileName = cursor.getString(cursor.getColumnIndexOrThrow(IMAGE_NAME));
			if (imageFileName != null) {
				exporter.saveImage(cursor);
				progress.imagesDone++;
			} else {
				exporter.noImage(cursor);
			}
			publishIncrement();
		}
		publishFinishing();
		exporter.finishImages(cursor);
	}

	public static String buildComment(Cursor cursor) {
		long id = cursor.getLong(cursor.getColumnIndexOrThrow(CommonColumns.ID));
		String property = cursor.getString(cursor.getColumnIndexOrThrow(Item.PROPERTY_NAME));
		String room = cursor.getString(cursor.getColumnIndexOrThrow(Item.ROOM_NAME));
		String item = cursor.getString(cursor.getColumnIndexOrThrow("itemName"));
		String parentName = cursor.getString(cursor.getColumnIndexOrThrow(ParentColumns.PARENT_NAME));
		Type type = Type.from(cursor, "type");
		String format = null;
		switch (type) {
			case Property:
				format = "%2$s #%1$d: %3$s";
				break;
			case Room:
				format = "%2$s #%1$d: %4$s in %3$s";
				break;
			case Item:
				if (Type.from(cursor, "parentType") == Type.Room) {
					format = "%2$s #%1$d: %5$s in %4$s in %3$s";
				} else {
					format = "%2$s #%1$d: %5$s in %6$s in %4$s in %3$s";
				}
				break;
		}
		if (format != null) {
			return String.format(Locale.ROOT, format, id, type, property, room, item, parentName);
		} else {
			return null;
		}
	}

	public interface ProgressDispatcher {
		/**
		 * @throws CancellationException is a good place to notify the exporter to quit immediately
		 */
		@WorkerThread
		void dispatchProgress(@NonNull Progress progress) throws CancellationException;

		ProgressDispatcher IGNORE = new ProgressDispatcher() {
			@Override public void dispatchProgress(@NonNull Progress progress) throws CancellationException {

			}
		};
	}
}