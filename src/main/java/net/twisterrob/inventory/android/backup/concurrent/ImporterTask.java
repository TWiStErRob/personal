package net.twisterrob.inventory.android.backup.concurrent;

import java.util.concurrent.CancellationException;

import org.slf4j.*;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.*;

import net.twisterrob.android.utils.concurrent.SimpleAsyncTask;
import net.twisterrob.inventory.android.backup.*;

// FIXME convert to Service
public class ImporterTask extends SimpleAsyncTask<Uri, Progress, Progress> implements ProgressDispatcher {
	private static final Logger LOG = LoggerFactory.getLogger(ImporterTask.class);

	private Importer.ImportCallbacks callbacks = DUMMY_CALLBACK;
	private final BackupZipUriImporter importer;

	public ImporterTask(Context context) {
		this.importer = new BackupZipUriImporter(context, this);
	}

	public void setCallbacks(Importer.ImportCallbacks callbacks) {
		this.callbacks = callbacks != null? callbacks : DUMMY_CALLBACK;
	}

	@Override protected void onPreExecute() {
		callbacks.importStarting();
	}

	@Override protected void onProgressUpdate(Progress progress) {
		callbacks.importProgress(progress);
	}

	@Override protected void onPostExecute(Progress progress) {
		if (progress.failure != null) {
			LOG.warn("Import failed", progress.failure);
		}
		callbacks.importFinished(progress);
	}

	@Override public void dispatchProgress(@NonNull Progress progress) throws CancellationException {
		if (isCancelled()) {
			throw new CancellationException();
		}
		publishProgress(progress);
	}

	@Override protected @Nullable Progress doInBackground(@Nullable Uri source) {
		try {
			return importer.importFrom(source);
		} catch (Throwable ex) {
			return new Progress(ex);
		}
	}

	/** To prevent NullPointerException and null-checks in code */
	private static final Importer.ImportCallbacks DUMMY_CALLBACK = new Importer.ImportCallbacks() {
		@Override public void importStarting() {
		}
		@Override public void importProgress(@NonNull Progress progress) {
		}
		@Override public void importFinished(@NonNull Progress progress) {
		}
	};
}

