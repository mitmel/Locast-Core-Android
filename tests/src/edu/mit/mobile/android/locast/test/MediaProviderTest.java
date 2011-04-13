package edu.mit.mobile.android.locast.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.test.ProviderTestCase2;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.Comment;
import edu.mit.mobile.android.locast.data.Itinerary;
import edu.mit.mobile.android.locast.data.JsonSyncableItem;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.data.Project;
import edu.mit.mobile.android.locast.data.Tag;
import edu.mit.mobile.android.locast.data.TaggableItem;

public class MediaProviderTest extends ProviderTestCase2<MediaProvider> {
	public MediaProviderTest() {
		super(MediaProvider.class, MediaProvider.AUTHORITY);

	}

	private Context mContext;
	private ContentResolver mCr;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		mContext = getMockContext();
		mCr = getMockContentResolver();
	}

	public void testContentTypes(){
		// casts
		assertEquals(MediaProvider.TYPE_CAST_DIR, mCr.getType(Cast.CONTENT_URI));
		assertEquals(MediaProvider.TYPE_CAST_ITEM, mCr.getType(ContentUris.withAppendedId(Cast.CONTENT_URI, 1)));

		// comments
		assertEquals(MediaProvider.TYPE_COMMENT_DIR, mCr.getType(Comment.CONTENT_URI));
		assertEquals(MediaProvider.TYPE_COMMENT_ITEM, mCr.getType(ContentUris.withAppendedId(Comment.CONTENT_URI, 1)));

		final List<Class<? extends JsonSyncableItem>> commentableItems = new ArrayList<Class<? extends JsonSyncableItem>>();
		commentableItems.add(Project.class);
		commentableItems.add(Cast.class);
		commentableItems.add(Itinerary.class);

		for (final Class<? extends JsonSyncableItem> commentable: commentableItems){
			Uri contentUri;
			try {
				contentUri = (Uri) commentable.getField("CONTENT_URI").get(null);
				contentUri = ContentUris.withAppendedId(contentUri, 1);
				final Uri commentUri = Uri.withAppendedPath(contentUri, Comment.PATH);
				assertEquals(commentable.getSimpleName() + ": " + contentUri, MediaProvider.TYPE_COMMENT_DIR, mCr.getType(commentUri));
				assertEquals(commentable.getSimpleName() + ": " + contentUri, MediaProvider.TYPE_COMMENT_ITEM, mCr.getType(ContentUris.withAppendedId(commentUri, 1)));
			} catch (final Exception e) {
				fail(e.getLocalizedMessage());
			}
		}

		// projects
		assertEquals(MediaProvider.TYPE_PROJECT_DIR, mCr.getType(Project.CONTENT_URI));
		assertEquals(MediaProvider.TYPE_PROJECT_ITEM, mCr.getType(ContentUris.withAppendedId(Project.CONTENT_URI, 1)));

		// casts of projects
		final Uri projectItem = ContentUris.withAppendedId(Project.CONTENT_URI, 1);
		assertEquals(MediaProvider.TYPE_PROJECT_CAST_DIR, mCr.getType(Uri.withAppendedPath(projectItem, Cast.PATH)));
		assertEquals(MediaProvider.TYPE_PROJECT_CAST_ITEM, mCr.getType(Uri.withAppendedPath(projectItem, Cast.PATH + "/1")));

		// itineraries
		assertEquals(MediaProvider.TYPE_ITINERARY_DIR, mCr.getType(Itinerary.CONTENT_URI));
		assertEquals(MediaProvider.TYPE_ITINERARY_ITEM, mCr.getType(ContentUris.withAppendedId(Itinerary.CONTENT_URI, 1)));

		// casts in itineraries
		final Uri itineraryItem = ContentUris.withAppendedId(Itinerary.CONTENT_URI, 1);
		assertEquals(MediaProvider.TYPE_CAST_DIR, mCr.getType(Uri.withAppendedPath(itineraryItem, Cast.PATH)));
		assertEquals(MediaProvider.TYPE_CAST_ITEM, mCr.getType(Uri.withAppendedPath(itineraryItem, Cast.PATH + "/1")));
	}

	private static final String
		T_TITLE = "title 1",
		T_TITLE2 = "title 2",
		T_DESCRIPTION = "description 1",
		T_DESCRIPTION2 = "description 2",
		T_AUTHOR = "author1",
		T_AUTHOR2 = "author2";

	private final Set<String> T_TAGS = new HashSet<String>(Arrays.asList(new String[]{"robots", "kittens"}));
	private final Set<String> T_TAGS_AFTER_ADD1 = new HashSet<String>(Arrays.asList(new String[]{"robots", "kittens", "lasers"}));

	private final String
		T_TAG_TO_ADD1 = "lasers";


	/**
	 * Create a cast using the provided URI.
	 *
	 * @param uri
	 * @return
	 */
	private Uri createCast(Uri uri){
		final ContentValues cv = new ContentValues();
		cv.put(Cast._TITLE, T_TITLE);
		cv.put(Cast._DESCRIPTION, T_DESCRIPTION);
		cv.put(Cast._AUTHOR, T_AUTHOR);
		cv.put(Cast._DRAFT, true);

		TaggableItem.putList(Tag.PATH, cv, T_TAGS);
		final Uri cast = mCr.insert(uri, cv);
		return cast;

	}

	private void testCastCRUD(Uri castUri){
		// create
		final Uri cast = createCast(castUri);

		assertNotNull(cast);

		// read
		final Cursor c = mCr.query(cast, Cast.PROJECTION, null, null, null);

		assertNotNull(c);
		assertTrue(c.moveToFirst());

		assertEquals(T_TITLE, c.getString(c.getColumnIndex(Cast._TITLE)));
		assertEquals(T_DESCRIPTION, c.getString(c.getColumnIndex(Cast._DESCRIPTION)));
		assertEquals(T_AUTHOR, c.getString(c.getColumnIndex(Cast._AUTHOR)));
		assertTrue(c.getInt(c.getColumnIndex(Cast._DRAFT)) != 0);
		assertTagsEqual(T_TAGS, TaggableItem.getTags(mCr, cast));
		// update

		assertEquals(1,mCr.delete(cast, null, null));
	}

	public void testCastCRUD(){
		testCastCRUD(Cast.CONTENT_URI);
	}

	private void assertTagsEqual(Set<String> expected, Set<String> actual){
		assertEquals(expected.size(), actual.size());

		for (final String tag : expected){
			if (!actual.contains(tag)){
				fail("'"+ tag + "' expected in set, but not found in "+ actual);
			}
		}
	}

	private Uri createItinerary(){
		final ContentValues cv = new ContentValues();
		cv.put(Cast._TITLE, T_TITLE);
		cv.put(Cast._DESCRIPTION, T_DESCRIPTION);
		cv.put(Cast._AUTHOR, T_AUTHOR);
		cv.put(Cast._DRAFT, true);

		TaggableItem.putList(Tag.PATH, cv, T_TAGS);
		return mCr.insert(Itinerary.CONTENT_URI, cv);
	}

	public void testItineraryCRUD(){
		// create
		final Uri itinerary = createItinerary();

		assertNotNull(itinerary);

		// read
		final Cursor c = mCr.query(itinerary, Itinerary.PROJECTION, null, null, null);

		assertNotNull(c);

		assertTrue(c.moveToFirst());

		assertEquals(T_TITLE, c.getString(c.getColumnIndex(Itinerary._TITLE)));
		assertEquals(T_DESCRIPTION, c.getString(c.getColumnIndex(Itinerary._DESCRIPTION)));
		assertEquals(T_AUTHOR, c.getString(c.getColumnIndex(Itinerary._AUTHOR)));
		assertTrue(c.getInt(c.getColumnIndex(Itinerary._DRAFT)) != 0);
		assertTagsEqual(T_TAGS, TaggableItem.getTags(mCr, itinerary));

		assertEquals(1, mCr.delete(itinerary, null, null));

	}

	public void testItineraryCast(){
		final Uri itinerary = createItinerary();

		testCastCRUD(Itinerary.getCastsUri(itinerary));

		assertEquals(1, mCr.delete(itinerary, null, null));
	}
}
