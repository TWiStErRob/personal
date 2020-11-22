package net.twisterrob.inventory.android.backup.importers;

import java.io.*;

import org.junit.*;
import org.junit.function.ThrowingRunnable;
import org.mockito.*;
import org.robolectric.annotation.Config;
import org.slf4j.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import android.content.*;
import android.net.Uri;

import net.twisterrob.android.test.LoggingAnswer;
import net.twisterrob.inventory.android.TestIgnoreApp;
import net.twisterrob.inventory.android.backup.ImportProgressHandler;
import net.twisterrob.test.frameworks.RobolectricTestBase;

import static net.twisterrob.test.hamcrest.Matchers.*;
import static net.twisterrob.test.mockito.ArgumentMatchers.*;

@Config(application = TestIgnoreApp.class)
public class BackupZipUriImporterTest extends RobolectricTestBase {
	private static final Logger LOG = LoggerFactory.getLogger(BackupZipUriImporterTest.class);

	public static final File FILE = new File("a/b");
	public static final Uri NON_FILE = Uri.parse("files:///non-existent-protocol");

	@Mock Context context;
	@Mock ImportProgressHandler dispatcher;
	@Mock BackupZipStreamImporter streamImporter;
	@Mock BackupZipFileImporter fileImporter;
	@InjectMocks private BackupZipUriImporter importer;

	@Before public void defaultStubbing() {
		when(context.getContentResolver()).thenReturn(mock(ContentResolver.class));
		doAnswer(new LoggingAnswer<>(LOG)).when(dispatcher).warning(anyString());
		doAnswer(new LoggingAnswer<>(LOG)).when(dispatcher).error(anyString());
	}

	@Test public void testImportFromFileCallsFileImporter() throws Exception {
		Uri input = Uri.fromFile(FILE);

		importer.importFrom(input);

		verify(fileImporter).importFrom(argThat(pointsTo(FILE)));
		verifyNoInteractions(streamImporter);
	}

	@Test public void testImportFromNonFileCallsStreamImporter() throws Exception {
		when(context.getContentResolver().openInputStream(any(Uri.class))).thenReturn(mock(InputStream.class));

		importer.importFrom(NON_FILE);

		verify(streamImporter).importFrom(isA(InputStream.class));
		verifyNoInteractions(fileImporter);
	}

	@Test public void testImportFromFileFails() throws Exception {
		NullPointerException ex = new NullPointerException("test");
		doThrow(ex).when(fileImporter).importFrom(any(File.class));

		Exception expectedFailure = assertThrows(NullPointerException.class, new ThrowingRunnable() {
			@Override public void run() throws Exception {
				importer.importFrom(Uri.fromFile(FILE));
			}
		});
		assertThat(expectedFailure, containsCause(ex));
	}

	@Test public void testImportFromNonFileFails() throws Exception {
		when(context.getContentResolver().openInputStream(any(Uri.class))).thenReturn(mock(InputStream.class));
		NullPointerException ex = new NullPointerException("test");
		doThrow(ex).when(streamImporter).importFrom(any(InputStream.class));

		Throwable expectedFailure = assertThrows(Throwable.class, new ThrowingRunnable() {
			@Override public void run() throws Exception {
				importer.importFrom(NON_FILE);
			}
		});
		assertThat(expectedFailure, containsCause(ex));
	}

	@Test public void testImportFromNonFileFailsToOpen() throws FileNotFoundException {
		FileNotFoundException ex = new FileNotFoundException("test");
		when(context.getContentResolver().openInputStream(NON_FILE)).thenThrow(new IllegalArgumentException(
				new IllegalStateException(new IOException(ex))));

		Throwable expectedFailure = assertThrows(Throwable.class, new ThrowingRunnable() {
			@Override public void run() throws Exception {
				importer.importFrom(NON_FILE);
			}
		});
		assertThat(expectedFailure, containsCause(ex));
	}
}
