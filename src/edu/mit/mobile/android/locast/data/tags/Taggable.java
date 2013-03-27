package edu.mit.mobile.android.locast.data.tags;

import edu.mit.mobile.android.content.m2m.M2MManager;

public interface Taggable {
    public static final M2MManager TAGS = new M2MManager(Tag.class);
}