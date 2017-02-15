package com.github.wrdlbrnft.gluemeister.utils;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Created with Android Studio<br>
 * User: Xaver<br>
 * Date: 15/02/2017
 */
public class EndlessIterator<T> implements Iterator<T> {

    private final List<T> mList;
    private int mIndex = 0;

    public EndlessIterator(List<T> list) {
        mList = Collections.unmodifiableList(list);
    }

    @Override
    public boolean hasNext() {
        return !mList.isEmpty();
    }

    public int getItemCount() {
        return mList.size();
    }

    public boolean isEmpty() {
        return mList.isEmpty();
    }

    @Override
    public T next() {
        return mList.get(mIndex++ % mList.size());
    }

    @Override
    public String toString() {
        return mList.toString();
    }
}
