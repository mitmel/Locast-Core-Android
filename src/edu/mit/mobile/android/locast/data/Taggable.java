package edu.mit.mobile.android.locast.data;

import android.net.Uri;
/*
 * Copyright (C) 2010  MIT Mobile Experience Lab
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
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

/**
 * DB entry for an item that can be tagged.
 *
 * @author stevep
 *
 */
public abstract class Taggable {

    public static final String PATH = "tags";

    public interface Columns {
        // no columns needed
    }

    public static Uri getTagPath(Uri item) {
        return item.buildUpon().appendPath(PATH).build();
    }
}
