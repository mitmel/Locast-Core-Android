package edu.mit.mobile.android.locast.test;

import java.util.Arrays;
import java.util.HashSet;
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

	private static final Uri
		U_CAST_ITEM = ContentUris.withAppendedId(Cast.CONTENT_URI, 1),
		U_COMMENT_ITEM = ContentUris.withAppendedId(Comment.CONTENT_URI, 1),

		U_ITINERARY_ITEM = ContentUris.withAppendedId(Itinerary.CONTENT_URI, 1),
		U_ITINERARY_CAST_DIR = Uri.withAppendedPath(U_ITINERARY_ITEM, Cast.PATH),
		U_ITINERARY_CAST_ITEM = ContentUris.withAppendedId(U_ITINERARY_CAST_DIR, 1),

		U_PROJECT_ITEM = ContentUris.withAppendedId(Project.CONTENT_URI, 1),
		U_PROJECT_CAST_DIR = Uri.withAppendedPath(U_PROJECT_ITEM, Cast.PATH),
		U_PROJECT_CAST_ITEM = ContentUris.withAppendedId(U_PROJECT_CAST_DIR, 1);

	private static final Uri[] U_ALL = {
		Cast.CONTENT_URI,
		U_CAST_ITEM,

		Comment.CONTENT_URI,
		U_COMMENT_ITEM,

		Itinerary.CONTENT_URI,
		U_ITINERARY_ITEM,
		U_ITINERARY_CAST_DIR,
		U_ITINERARY_CAST_ITEM,

		Project.CONTENT_URI,
		U_PROJECT_ITEM,
		U_PROJECT_CAST_DIR,
		U_PROJECT_CAST_ITEM,
	};

	public void testContentTypes(){
		// casts
		assertEquals(MediaProvider.TYPE_CAST_DIR, mCr.getType(Cast.CONTENT_URI));
		assertEquals(MediaProvider.TYPE_CAST_ITEM, mCr.getType(U_CAST_ITEM));

		// comments
		assertEquals(MediaProvider.TYPE_COMMENT_DIR, mCr.getType(Comment.CONTENT_URI));
		assertEquals(MediaProvider.TYPE_COMMENT_ITEM, mCr.getType(U_COMMENT_ITEM));

		//
		testAllContentTypes(new ContentUriTest() {
			@Override
			public void runUriTest(Uri contentUri) {
				contentUri = ContentUris.withAppendedId(contentUri, 1);
				final Uri commentUri = Uri.withAppendedPath(contentUri, Comment.PATH);
				assertEquals(contentUri.toString(), MediaProvider.TYPE_COMMENT_DIR, mCr.getType(commentUri));
				assertEquals(contentUri.toString(), MediaProvider.TYPE_COMMENT_ITEM, mCr.getType(ContentUris.withAppendedId(commentUri, 1)));

			}
		// these should all be indexes to things that are commentable.
		}, Project.CONTENT_URI, Cast.CONTENT_URI, Itinerary.CONTENT_URI, U_PROJECT_CAST_DIR, U_ITINERARY_CAST_DIR);

		// projects
		assertEquals(MediaProvider.TYPE_PROJECT_DIR, mCr.getType(Project.CONTENT_URI));
		assertEquals(MediaProvider.TYPE_PROJECT_ITEM, mCr.getType(U_PROJECT_ITEM));

		// casts of projects
		assertEquals(MediaProvider.TYPE_PROJECT_CAST_DIR, mCr.getType(U_PROJECT_CAST_DIR));
		assertEquals(MediaProvider.TYPE_PROJECT_CAST_ITEM, mCr.getType(U_PROJECT_CAST_ITEM));

		// itineraries
		assertEquals(MediaProvider.TYPE_ITINERARY_DIR, mCr.getType(Itinerary.CONTENT_URI));
		assertEquals(MediaProvider.TYPE_ITINERARY_ITEM, mCr.getType(U_ITINERARY_ITEM));

		// casts in itineraries
		assertEquals(MediaProvider.TYPE_CAST_DIR, mCr.getType(U_ITINERARY_CAST_DIR));
		assertEquals(MediaProvider.TYPE_CAST_ITEM, mCr.getType(U_ITINERARY_CAST_ITEM));
	}

	public void testSyncability(){
		testAllContentTypes(new ContentUriTest(){
			@Override
			public void runUriTest(Uri contentUri) {
				assertNotNull(MediaProvider.canSync(contentUri));
			}

		}, U_ALL);
	}

	public void testQueries(){
		testAllContentTypes(new ContentUriTest(){
			@Override
			public void runUriTest(Uri contentUri) {
				assertNotNull(contentUri.toString(), mCr.query(contentUri, null, null, null, null));
			}

		}, U_ALL);
	}

	private void testAllContentTypes(ContentUriTest test, Uri ... types){
		for (final Uri contentUri: types){
			test.runUriTest(contentUri);
		}
	}

	private interface ContentUriTest{
		public void runUriTest(Uri contentUri);
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
