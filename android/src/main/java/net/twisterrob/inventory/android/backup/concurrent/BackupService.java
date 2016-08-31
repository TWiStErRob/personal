package net.twisterrob.inventory.android.backup.concurrent;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.concurrent.ThreadSafe;

import org.slf4j.*;

import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.support.annotation.*;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;

import net.twisterrob.inventory.android.*;
import net.twisterrob.inventory.android.activity.BackupActivity;
import net.twisterrob.inventory.android.backup.*;
import net.twisterrob.inventory.android.backup.exporters.*;
import net.twisterrob.inventory.android.backup.importers.*;
import net.twisterrob.inventory.android.backup.xml.ZippedXMLExporter;
import net.twisterrob.java.utils.ObjectTools;

import static net.twisterrob.inventory.android.Constants.*;
import static net.twisterrob.inventory.android.backup.ProgressDisplayer.*;

public class BackupService extends NotificationProgressService<Progress> {
	private static final Logger LOG = LoggerFactory.getLogger(BackupService.class);

	private static final String ACTION_EXPORT_PFD_WORKAROUND = "net.twisterrob.inventory.intent.action.EXPORT_PFD";
	public static final String ACTION_EXPORT = "net.twisterrob.inventory.intent.action.EXPORT";
	public static final String ACTION_EXPORT_DIR = "net.twisterrob.inventory.intent.action.EXPORT_DIR";
	public static final String ACTION_IMPORT = "net.twisterrob.inventory.intent.action.IMPORT";
	// TODO Parcelable or ProgressDisplayer?
	public static final String EXTRA_PROGRESS = "net.twisterrob.inventory:backup_progress";

	private /*final*/ ProgressDisplayer displayer;
	private final BackupListeners listeners = new BackupListeners();
	/**
	 * Not all parcelables can be put into Extras of an Intent.
	 * Specifically {@link ParcelFileDescriptor} can't: <q>"Not allowed to write file descriptors here"</q>.
	 * This means that the {@link BackupService} can't be just started nicely with an {@link Intent}
	 * from {@link net.twisterrob.inventory.android.content.InventoryProvider},
	 * but it needs to bind and pass the target differently.
	 * @see <a href="http://stackoverflow.com/q/18706062/253468">Exception with sending ParcelFileDescriptor via Intent</a>
	 */
	private final Queue<ParcelFileDescriptor> queue = new LinkedBlockingDeque<>();
	private final ProgressDispatcher dispatcher = new ProgressDispatcher();

	public BackupService() {
		super(BackupService.class.getSimpleName());
		setDebugMode(DISABLE && BuildConfig.DEBUG);
	}

	@Override public void onCreate() {
		displayer = new ProgressDisplayer(this);
		super.onCreate();
	}

	@Override protected @NonNull Builder createOnGoingNotification(Intent intent) {
		return new android.support.v7.app.NotificationCompat.Builder(this)
				.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
				.setSmallIcon(android.R.drawable.stat_sys_download)
				.setTicker(isImport(intent)
						? getString(R.string.backup_import_progress_background)
						: getString(R.string.backup_export_progress_background)
				)
				;
	}
	@Override protected boolean fillNotification(@NonNull Builder notification, @NonNull Progress progress) {
		super.fillNotification(notification, progress);
		displayer.setProgress(progress);
		notification.setContentTitle(displayer.getTitle());
		notification.setContentText(displayer.getMessage());
		notification.setProgress(displayer.getTotal(), displayer.getDone(), displayer.isIndeterminate());
		return isVeryDifferentFrom(getLastProgressSentToNotification(), progress);
	}

	@Override protected @NonNull Builder createFinishedNotification(@NonNull Progress result) {
		displayer.setProgress(result);
		String title = displayer.getTitle();
		String message = displayer.getMessage();
		return new android.support.v7.app.NotificationCompat.Builder(this)
				.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
				.setTicker(title)
				.setContentTitle(title)
				.setContentText(message)
				.setStyle(new NotificationCompat.BigTextStyle().bigText(message))
				.setSmallIcon(android.R.drawable.stat_sys_download_done)
				;
	}

	@Override protected @Nullable Intent createInProgressIntent() {
		return new Intent(getApplicationContext(), BackupActivity.class);
	}

	@Override protected @Nullable Intent createFinishedIntent(@NonNull Progress progress) {
		Intent intent = new Intent(getApplicationContext(), BackupActivity.class);
		intent.putExtra(EXTRA_PROGRESS, progress);
		return intent;
	}

	@Override protected void fillBroadcast(@NonNull Intent intent, @NonNull Progress progress) {
		super.fillBroadcast(intent, progress);
		intent.putExtra(EXTRA_PROGRESS, progress);
	}

	private boolean isImport(Intent intent) {
		return ACTION_IMPORT.equals(intent.getAction());
	}

	@SuppressWarnings("WrongThread") // TODEL http://b.android.com/207302
	@WorkerThread
	@Override protected void onHandleIntent(Intent intent) {
		super.onHandleIntent(intent);
		dispatcher.reset(); // call before started, so the listener may cancel immediately
		listeners.started();
		try {
			if (ACTION_EXPORT_PFD_WORKAROUND.equals(intent.getAction())) {
				dispatcher.setCancellable(false);
				BackupParcelExporter exporter = new BackupParcelExporter(new ZippedXMLExporter(), dispatcher);
				ParcelFileDescriptor file = queue.remove();
				finish(exporter.exportTo(file));
			} else if (ACTION_EXPORT.equals(intent.getAction())) {
				dispatcher.setCancellable(false);
				BackupUriExporter exporter = new BackupUriExporter(this, new ZippedXMLExporter(), dispatcher);
				Uri uri = intent.getData();
				finish(exporter.exportTo(uri));
			} else if (ACTION_EXPORT_DIR.equals(intent.getAction())) {
				BackupDirExporter exporter = new BackupDirExporter(this, new ZippedXMLExporter(), dispatcher);
				File dir = new File(intent.getData().getPath());
				finish(exporter.exportTo(dir));
			}
		} catch (Throwable ex) {
			finish(new Progress(Progress.Type.Export, ex));
		}
		try {
			if (ACTION_IMPORT.equals(intent.getAction())) {
				Uri uri = intent.getData();
				finish(importFrom(uri));
			}
		} catch (Throwable ex) {
			finish(new Progress(Progress.Type.Import, ex));
		}
		if (isInProgress()) {
			throw new IllegalStateException("Unknown intent action: " + intent.getAction());
		}
		listeners.finished();
		displayer.setProgress(null);
	}

	private void finish(Progress result) {
		LOG.info("Finished with: {}", result.toString(true), result.failure);
		finished(result);
	}

	private Progress importFrom(Uri input) {
		ImportProgressHandler progress = new ImportProgressHandler(dispatcher);
		progress.begin();

		ZipImporter<Uri> importer = new BackupTransactingImporter<>(new BackupZipUriImporter(this, progress), progress);
		try {
			importer.importFrom(input);
		} catch (Exception ex) {
			progress.fail(ex);
		}
		return progress.end();
	}

	@Override public IBinder onBind(Intent intent) {
		super.onBind(intent);
		return new LocalBinder();
	}

	public class LocalBinder extends Binder {
		public void export(@NonNull ParcelFileDescriptor pfd) {
			queue.add(ObjectTools.checkNotNull(pfd));
			startService(new Intent(ACTION_EXPORT_PFD_WORKAROUND, (Uri)null, BackupService.this, BackupService.class));
		}

		public void cancel() {
			dispatcher.cancel();
		}
		public boolean isInProgress() {
			return BackupService.this.isInProgress();
		}
		public Progress getLastProgress() {
			return BackupService.this.getLastProgress();
		}

		public void addBackupListener(@NonNull BackupListener listener) {
			listeners.add(listener);
		}
		public void removeBackupListener(@NonNull BackupListener listener) {
			listeners.remove(ObjectTools.checkNotNull(listener));
		}
		/** Signifies whether the current backup operation can clean up after itself. */
		public boolean isCancellable() {
			return dispatcher.isCancellable();
		}
		public boolean isCancelled() {
			return dispatcher.isCancelled();
		}
	}

	@ThreadSafe
	@AnyThread
	private class ProgressDispatcher implements net.twisterrob.inventory.android.backup.ProgressDispatcher {
		private final AtomicReference<CancellationException> cancelled = new AtomicReference<>(null);
		private volatile boolean cancellable = true;

		public void reset() {
			cancelled.set(null);
			setCancellable(true);
		}

		public void cancel() {
			if (!isInProgress()) {
				throw new IllegalStateException("There's nothing to cancel.");
			}
			// FIXME interrupt/close output, so that XSLT transform dies
			if (!cancelled.compareAndSet(null, new CancellationException("initiating stack trace"))) {
				throw new IllegalStateException("Already cancelled, but cancellation not yet picked up.");
			}
		}

		@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
		public boolean isCancelled() {
			return cancelled.get() != null;
		}

		@Override public void dispatchProgress(@NonNull Progress progress) throws CancellationException {
			CancellationException cancellationCause = cancelled.getAndSet(null);
			if (cancellationCause != null) {
				CancellationException realCancel = new CancellationException("user cancelled");
				realCancel.initCause(cancellationCause);
				throw realCancel;
			}
			reportProgress(progress);
		}
		public boolean isCancellable() {
			return cancellable;
		}
		public void setCancellable(boolean cancellable) {
			this.cancellable = cancellable;
		}
	}

	@UiThread
	public interface BackupListener {
		void started();
		void finished();
	}

	@ThreadSafe
	@AnyThread
	private static class BackupListeners implements BackupListener {
		private final List<BackupListener> listeners = new LinkedList<>();
		private final Handler main = new Handler(Looper.getMainLooper());

		public void add(@NonNull BackupListener listener) {
			synchronized (listeners) {
				listeners.add(ObjectTools.checkNotNull(listener));
			}
		}

		public void remove(@NonNull BackupListener listener) {
			synchronized (listeners) {
				listeners.remove(ObjectTools.checkNotNull(listener));
			}
		}

		@Override public void started() {
			synchronized (listeners) {
				for (final BackupListener listener : listeners) {
					main.post(new Runnable() {
						@Override public void run() {
							listener.started();
						}
					});
				}
			}
		}

		@Override public void finished() {
			synchronized (listeners) {
				for (final BackupListener listener : listeners) {
					main.post(new Runnable() {
						@Override public void run() {
							listener.finished();
						}
					});
				}
			}
		}
	}
}