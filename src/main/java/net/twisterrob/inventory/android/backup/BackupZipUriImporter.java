package net.twisterrob.inventory.android.backup;

import java.io.*;

import android.content.*;
import android.net.Uri;
import android.support.annotation.VisibleForTesting;

import net.twisterrob.android.utils.tools.IOTools;

public class BackupZipUriImporter implements ZipImporter<Uri> {
	private final Context context;
	private final BackupZipStreamImporter streamImporter;
	private final BackupZipFileImporter fileImporter;

	public BackupZipUriImporter(Context context, ImportProgressHandler progress) {
		this(context,
				new BackupZipStreamImporter(context.getResources(), progress),
				new BackupZipFileImporter(context.getResources(), progress));
	}
	@VisibleForTesting BackupZipUriImporter(Context context,
			BackupZipStreamImporter streamImporter, BackupZipFileImporter fileImporter) {
		this.context = context;
		this.streamImporter = streamImporter;
		this.fileImporter = fileImporter;
	}

	@Override public void importFrom(Uri uri) throws Exception {
		if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
			importFile(uri);
		} else {
			importStream(uri);
		}
	}

	private void importFile(Uri uri) throws Exception {
		fileImporter.importFrom(new File(uri.getPath()));
	}

	private void importStream(Uri uri) throws Exception {
		InputStream stream = null;
		try {
			stream = context.getContentResolver().openInputStream(uri);
			streamImporter.importFrom(stream);
		} finally {
			IOTools.ignorantClose(stream);
		}
	}
}
