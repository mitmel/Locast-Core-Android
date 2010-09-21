/**
 *
 */
package edu.mit.mel.locast.mobile.templates;

import java.text.NumberFormat;
import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import edu.mit.mel.locast.mobile.R;

public class TemplateAdapter extends ArrayAdapter<CastMediaInProgress> {
	/**
	 *
	 */
	private final NumberFormat timeFormat  = NumberFormat.getInstance();
	private final int mItemLayout;
	private final OnClickListener buttonListener;

	public TemplateAdapter(Context context, OnClickListener buttonListener, List<CastMediaInProgress> array, int itemLayout) {
		super(context, R.layout.template_item, array);
		this.mItemLayout = itemLayout;
		this.buttonListener = buttonListener;

		timeFormat.setMinimumFractionDigits(1);
		timeFormat.setMaximumFractionDigits(1);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		final CastMediaInProgress item = getItem(position);

		if (convertView == null){
			convertView = LayoutInflater.from(getContext()).inflate(mItemLayout, parent, false);
		}

		((TextView)(convertView.findViewById(R.id.template_item_numeral))).setText(item.index + 1 + ".");
		((TextView)(convertView.findViewById(android.R.id.text1))).setText(item.direction);
		final int shownSeconds = Math.abs(item.duration - item.elapsedDuration);
		final String secondsString = (shownSeconds == 0 && item.duration == 0) ? "∞" :  Integer.toString(shownSeconds)+"s";

		((TextView)(convertView.findViewById(R.id.time_remaining))).setText(secondsString);

		if (mItemLayout == R.layout.template_item_full){
			final Button delete = (Button)convertView.findViewById(R.id.delete);
			delete.setTag(position);
			delete.setOnClickListener(buttonListener);
			delete.setVisibility(item.localUri != null ? View.VISIBLE : View.GONE);
		}
		return convertView;
	}

	public int getTotalTime(int position){
		final CastMediaInProgress item = getItem(position);

		return item.duration * 1000;
	}
}