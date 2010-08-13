package edu.mit.mel.locast.mobile.templates;
/*
 * Copyright (C) 2010  MIT Mobile Experience Lab
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

class CastMediaInProgress implements Parcelable {
	public CastMediaInProgress(String direction, int duration, int index){
		this.duration = duration;
		this.direction = direction;
		this.index = index;
	}
	protected Uri localUri = null;
	protected int duration = 0;
	protected int elapsedDuration = 0;
	protected String direction = null;
	protected int index = 0;

	public int describeContents() {
		return 0;
	}

	public CastMediaInProgress(Parcel p){

		localUri = Uri.CREATOR.createFromParcel(p);
		duration = p.readInt();
		elapsedDuration = p.readInt();
		direction = p.readString();
		index = p.readInt();
	}
	public void writeToParcel(Parcel dest, int flags) {

		Uri.writeToParcel(dest, localUri);
		dest.writeInt(duration);
		dest.writeInt(elapsedDuration);
		dest.writeString(direction);
		dest.writeInt(index);
	}

	@SuppressWarnings("unused")
	public static final Parcelable.Creator<CastMediaInProgress> CREATOR
		= new Creator<CastMediaInProgress>() {

			public CastMediaInProgress[] newArray(int size) {
				return new CastMediaInProgress[size];
			}

			public CastMediaInProgress createFromParcel(Parcel source) {
				return new CastMediaInProgress(source);
			}
		};
}