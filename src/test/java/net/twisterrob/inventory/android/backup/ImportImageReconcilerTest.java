package net.twisterrob.inventory.android.backup;

import java.io.*;

import org.junit.*;
import org.junit.rules.ExpectedException;
import org.mockito.*;
import org.mockito.junit.*;
import org.slf4j.*;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.junit.internal.matchers.ThrowableMessageMatcher.*;
import static org.mockito.AdditionalMatchers.*;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.anyVararg;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.contains;

import android.content.res.Resources;

import com.shazam.gwen.Gwen;

import static com.github.stefanbirkner.fishbowl.Fishbowl.*;

import net.twisterrob.android.test.*;
import net.twisterrob.inventory.android.R;
import net.twisterrob.inventory.android.content.Database;
import net.twisterrob.inventory.android.content.contract.Type;

import static net.twisterrob.inventory.android.content.contract.Type.*;

@SuppressWarnings("Duplicates")
public class ImportImageReconcilerTest {
	private static final Logger LOG = LoggerFactory.getLogger(ImportImageReconcilerTest.class);

	private static final Long ANY_TIME = null;
	private static final String IMAGE = "mock_image_name";
	private static final String OTHER_IMAGE = "mock_image_other";
	private static final int ITEM_ID = 100;
	private static final int OTHER_ITEM_ID = 101;
	private static final String ITEM_NAME = "test item " + ITEM_ID;
	private static final String OTHER_ITEM_NAME = "other test item " + OTHER_ITEM_ID;

	@Rule public MockitoRule mockito = MockitoJUnit.rule();
	@Rule public ExpectedException thrown = ExpectedException.none();

	@Mock private Database db;
	@Mock private Resources res;
	@Mock private ImportProgressHandler progress;
	@InjectMocks private BackupZipStreamImporter.ImportImageReconciler reconciler;
	@InjectMocks private BackupImageDatabase database;

	@Before public void setUp() {
		when(res.getString(anyInt(), anyVararg())).thenAnswer(new GetStringVarargsAnswer(R.string.class));
		doAnswer(new LoggingAnswer<>(LOG)).when(progress).warning(anyString());
		doAnswer(new LoggingAnswer<>(LOG)).when(progress).error(anyString());
	}

	@Test public void testMatchedImageSaved() throws IOException {
		reconciler.foundImageFile(IMAGE, contents(IMAGE), ANY_TIME);
		reconciler.importImage(Item, ITEM_ID, ITEM_NAME, IMAGE);
		reconciler.hasData();
		reconciler.close();

		Gwen.then(database).matchedImages(IMAGE);
	}

	@Test public void testMatchedImagesSaved() throws IOException {
		reconciler.foundImageFile(IMAGE, contents(IMAGE), ANY_TIME);
		reconciler.foundImageFile(OTHER_IMAGE, contents(OTHER_IMAGE), ANY_TIME);
		reconciler.importImage(Item, ITEM_ID, ITEM_NAME, IMAGE);
		reconciler.importImage(Item, OTHER_ITEM_ID, OTHER_ITEM_NAME, OTHER_IMAGE);
		reconciler.hasData();
		reconciler.close();

		Gwen.then(database).matchedImages(IMAGE, OTHER_IMAGE);
	}

	@Test public void testMatchedImagesReversedSaved() throws IOException {
		reconciler.foundImageFile(IMAGE, contents(IMAGE), ANY_TIME);
		reconciler.foundImageFile(OTHER_IMAGE, contents(OTHER_IMAGE), ANY_TIME);
		reconciler.importImage(Item, OTHER_ITEM_ID, OTHER_ITEM_NAME, OTHER_IMAGE);
		reconciler.importImage(Item, ITEM_ID, ITEM_NAME, IMAGE);
		reconciler.hasData();
		reconciler.close();

		Gwen.then(database).matchedImages(IMAGE, OTHER_IMAGE);
	}

	@Test public void testMatchedImagesAfterDataSaved() throws IOException {
		reconciler.importImage(Item, ITEM_ID, ITEM_NAME, IMAGE);
		reconciler.importImage(Item, OTHER_ITEM_ID, OTHER_ITEM_NAME, OTHER_IMAGE);
		reconciler.hasData();
		reconciler.foundImageFile(IMAGE, contents(IMAGE), ANY_TIME);
		reconciler.foundImageFile(OTHER_IMAGE, contents(OTHER_IMAGE), ANY_TIME);
		reconciler.close();

		Gwen.then(database).matchedImages(IMAGE, OTHER_IMAGE);
	}

	@Test public void testMatchedImagesAfterDataReversedSaved() throws IOException {
		reconciler.importImage(Item, ITEM_ID, ITEM_NAME, IMAGE);
		reconciler.importImage(Item, OTHER_ITEM_ID, OTHER_ITEM_NAME, OTHER_IMAGE);
		reconciler.hasData();
		reconciler.foundImageFile(OTHER_IMAGE, contents(OTHER_IMAGE), ANY_TIME);
		reconciler.foundImageFile(IMAGE, contents(IMAGE), ANY_TIME);
		reconciler.close();

		Gwen.then(database).matchedImages(IMAGE, OTHER_IMAGE);
	}

	@Test public void testMatchedItemSaved() throws IOException {
		reconciler.importImage(Item, ITEM_ID, ITEM_NAME, IMAGE);
		reconciler.hasData();
		reconciler.foundImageFile(IMAGE, contents(IMAGE), ANY_TIME);
		reconciler.close();

		Gwen.then(database).matchedImages(IMAGE);
	}

	@Test public void testUnmatchedImageRemoved() throws IOException {
		reconciler.foundImageFile(IMAGE, contents(IMAGE), ANY_TIME);
		reconciler.importImage(Item, ITEM_ID, ITEM_NAME, OTHER_IMAGE);
		reconciler.hasData();
		reconciler.close();

		Gwen.then(database).redundantImages(IMAGE);
		verify(progress).warning(and(contains(ITEM_NAME), contains(OTHER_IMAGE)));
	}

	@Test public void testRedundantImageRemoved() throws IOException {
		reconciler.foundImageFile(IMAGE, contents(IMAGE), ANY_TIME);
		reconciler.close();

		Gwen.then(database).redundantImages(IMAGE);
	}

	@Test public void testDanglingImageRemoved() throws IOException {
		reconciler.hasData();
		reconciler.foundImageFile(IMAGE, contents(IMAGE), ANY_TIME);
		reconciler.close();

		Gwen.then(database).danglingImages(IMAGE);
	}

	@Test public void testInvalidImageType() throws Throwable {
		reconciler.importImage(Type.Root, 0, "bad item", IMAGE);
		reconciler.hasData();

		Throwable thrown = exceptionThrownBy(() -> {
			reconciler.foundImageFile(IMAGE, contents(IMAGE), ANY_TIME);
		});

		assertThat(thrown, hasMessage(containsString(Type.Root.toString())));
		verify(db, atMost(1)).addImage(Mockito.any(byte[].class), anyLong());
	}

	@Test public void testDuplicateUnmatchedImageFileBefore() throws IOException {
		reconciler.foundImageFile(IMAGE, contents(IMAGE), ANY_TIME);

		Throwable thrown = exceptionThrownBy(() -> {
			reconciler.foundImageFile(IMAGE, contents(IMAGE), ANY_TIME);
		});

		assertDuplicate(IMAGE, thrown);
		Gwen.then(database).redundantImages(IMAGE);
	}

	@Test public void testDuplicateUnmatchedImageFileAfter() throws IOException {
		reconciler.hasData();
		reconciler.foundImageFile(IMAGE, contents(IMAGE), ANY_TIME);
		reconciler.foundImageFile(IMAGE, contents(IMAGE), ANY_TIME);

		Gwen.then(database).danglingImages(IMAGE);
	}

	@Test public void testDuplicateUnmatchedImageFileBetween() throws IOException {
		reconciler.foundImageFile(IMAGE, contents(IMAGE), ANY_TIME);
		reconciler.hasData();

		Throwable thrown = exceptionThrownBy(() -> {
			reconciler.foundImageFile(IMAGE, contents(IMAGE), ANY_TIME);
		});

		assertDuplicate(IMAGE, thrown);
		Gwen.then(database).redundantImages(IMAGE);
	}

	@Test public void testDuplicateMatchedImageFileAfter() throws IOException {
		reconciler.importImage(Item, ITEM_ID, ITEM_NAME, IMAGE);
		reconciler.hasData();
		reconciler.foundImageFile(IMAGE, contents(IMAGE), ANY_TIME);
		reconciler.foundImageFile(IMAGE, contents(IMAGE), ANY_TIME);

		Gwen.then(database).matchedImages(IMAGE);
		verifyNoMoreInteractions(db);
	}

	@Test public void testDuplicateMatchedImageFileBetween() throws IOException {
		reconciler.foundImageFile(IMAGE, contents(IMAGE), ANY_TIME);
		reconciler.importImage(Item, ITEM_ID, ITEM_NAME, IMAGE);
		reconciler.hasData();
		reconciler.foundImageFile(IMAGE, contents(IMAGE), ANY_TIME);

		Gwen.then(database).matchedImages(IMAGE);
		verifyNoMoreInteractions(db);
	}

	@Test public void testDuplicateReference() throws IOException {
		reconciler.importImage(Item, ITEM_ID, ITEM_NAME, IMAGE);

		Throwable thrown = exceptionThrownBy(() -> {
			reconciler.importImage(Item, OTHER_ITEM_ID, OTHER_ITEM_NAME, IMAGE);
		});

		assertDuplicate(IMAGE, thrown);
		Gwen.then(database).danglingImages(IMAGE);
		verifyNoMoreInteractions(db);
	}

	@Test public void testDuplicateMatchedReference() throws IOException {
		reconciler.foundImageFile(IMAGE, contents(IMAGE), ANY_TIME);
		reconciler.importImage(Item, ITEM_ID, ITEM_NAME, IMAGE);
		reconciler.importImage(Item, OTHER_ITEM_ID, OTHER_ITEM_NAME, IMAGE);
		reconciler.close();

		Gwen.then(database).matchedImages(IMAGE);
		verifyNoMoreInteractions(db);
		verify(progress).warning(and(contains(OTHER_ITEM_NAME), contains(IMAGE)));
	}

	private static void assertDuplicate(String image, Throwable thrown) {
		assertThat(thrown, hasMessage(both(containsStringIgnoringCase("duplicate")).and(containsString(image))));
	}
	private static InputStream contents(String file) throws IOException {
		return new ByteArrayInputStream(BackupZip.getContents(file));
	}
}