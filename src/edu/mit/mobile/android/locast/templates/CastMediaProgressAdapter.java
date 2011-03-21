package edu.mit.mobile.android.locast.templates;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.data.CastMedia;
import edu.mit.mobile.android.locast.data.ShotList;
import edu.mit.mobile.android.widget.RelativeSizeListAdapter;

public class CastMediaProgressAdapter extends CursorAdapter implements RelativeSizeListAdapter {
	@SuppressWarnings("unused")
	private static final String TAG = CastMediaProgressAdapter.class.getSimpleName();
	/**
	 * length of any recorded shots. 0 if not yet recorded.
	 */
	private int[] progress;
	/**
	 * length of a shot, defined in the shot list
	 */
	private int[] shotLength;
	/**
	 * If the length of time is mandatory or just the max.
	 */
	private boolean[] hardLimit;


	private final int durationCol, videoCol;
	private final int shotListDurationCol, hardLimitCol;
	private final Cursor shotListCursor;

	public CastMediaProgressAdapter(Context context, Cursor castMedia, Cursor shotList) {
		super(context, castMedia);

		durationCol = castMedia.getColumnIndex(CastMedia._DURATION);
		videoCol = castMedia.getColumnIndex(CastMedia._LOCAL_URI);

		this.shotListCursor = shotList;
		shotListDurationCol = shotList.getColumnIndex(ShotList._DURATION);
		hardLimitCol = shotList.getColumnIndex(ShotList._HARD_DURATION);

		loadProgress();
	}

	private void loadProgress(){
		final Cursor c = getCursor();
		if (c.isClosed() || shotListCursor.isClosed()){
			return;
		}
		final int count = getCount();
		progress = new int[count];
		shotLength = new int[count];
		hardLimit = new boolean[count];

		for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()){
			final int position = c.getPosition();
			progress[position] = !c.isNull(videoCol) ? c.getInt(durationCol) : 0;

			if (shotListCursor.moveToPosition(position)){
				hardLimit[position] = shotListCursor.isNull(hardLimitCol) ? false : shotListCursor.getInt(hardLimitCol) != 0;
				shotLength[position] = shotListCursor.getInt(shotListDurationCol) * 1000;
			}else{
				shotLength[position] = 0; // if there isn't a shot list, assume unlimited shots.
				hardLimit[position] = false;
			}
			// XXX hack to test
			hardLimit[position] = shotLength[position] != 0 && shotLength[position] <= 5000;
		}
	}

	public int getShotLength(int index){
		return shotLength[index];
	}

	public boolean getHardLimit(int index){
		return hardLimit[index];
	}

	public int getProgress(int index){
		return progress[index];
	}

	@Override
	protected void onContentChanged() {
		super.onContentChanged();

		loadProgress();
	}

	/**
	 * @param position the number of the cast media
	 * @param progress progress in milliseconds.
	 */
	public void updateProgress(int position, int progress){
		this.progress[position] = progress;
		notifyDataSetChanged();
	}

	@Override
	public float getRelativeSize(int position) {
		float len = shotLength[position];
		if (len == 0){
			len = Math.max(3000, progress[position]); // XXX this should really be in the presentation layer.
		}
		return len;
	}

	@Override
	public void bindView(View view, Context context, Cursor cursor) {
		final ProgressBar pb = (ProgressBar) view.findViewById(R.id.progress_segment);
		final int shot = cursor.getPosition();
		final int shotLen = shotLength[shot];
		final int shotProgress = progress[shot];
		//  is not infinite or is already recorded
		if (shotLen > 0 || !cursor.isNull(videoCol)){
			pb.setIndeterminate(false);
			pb.setMax(Math.max(shotLen, shotProgress));
			pb.setProgress(shotProgress);
		// is infinite and not yet recorded
		}else{
			if (shotProgress > 0){
				pb.setIndeterminate(true);
			}else{
				pb.setProgress(0);
				pb.setIndeterminate(false);
			}
		}

		final TextView segmentText = (TextView)view.findViewById(R.id.progress_segment_text);

		final CharSequence number = shotLen == 0 ? context.getString(R.string.infinite) : Integer.toString(shotLen / 1000);

		segmentText.setText(
				shotLen == 0
				? number
				: context.getString(
				((hardLimit[shot]) ? R.string.template_progress_hard_limit : R.string.template_progress_no_hard_limit),
				number));
	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		return LayoutInflater.from(context).inflate(R.layout.template_progress_item, parent, false);
	}
}
