package net.twisterrob.inventory.android.backup;

import org.slf4j.*;

import android.support.annotation.VisibleForTesting;

import net.twisterrob.inventory.android.backup.Importer.ImportProgress;
import net.twisterrob.java.utils.ObjectTools;

@SuppressWarnings("deprecation")
public class ImportProgressHandler implements ImportProgress {
	private static final Logger LOG = LoggerFactory.getLogger(ImportProgressHandler.class);
	private final ProgressDispatcher dispatcher;

	/** @deprecated don't use it directly */
	final Progress progress = new Progress();

	@VisibleForTesting ImportProgressHandler() {
		this(ProgressDispatcher.IGNORE);
	}
	public ImportProgressHandler(ProgressDispatcher dispatcher) {
		this.dispatcher = ObjectTools.checkNotNull(dispatcher);
	}

	public void publishStart(int size) {
		progress.done = 0;
		progress.total = size;
		publishProgress();
	}

	public void publishIncrement() {
		progress.done++;
		publishProgress();
	}
	public void imageIncrement() {
		progress.imagesDone++;
	}
	public void imageTotalIncrement() {
		progress.imagesTotal++;
	}

	protected void publishProgress() {
		dispatcher.dispatchProgress(progress.clone());
	}

	@Override public void warning(String message) {
		LOG.warn("Warning: {}", message);
		progress.warnings.add(message);
	}

	@Override public void error(String message) {
		LOG.warn("Error: {}", message);
		progress.warnings.add(message);
	}

	public Progress finalProgress() {
		return progress;
	}

	public void fail(Throwable ex) {
		if (progress.failure != null) {
			LOG.warn("Exception suppressed by {}", progress.failure, ex);
			error(ex.toString());
		} else {
			progress.failure = ex;
		}
	}
}
