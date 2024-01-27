package com.ulyp.core.mem;

import org.agrona.BitUtil;
import org.agrona.concurrent.UnsafeBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Single allocator - single deallocator (SASD) concurrency is supported. At any point of time, only a single thread
 * may borrow a memory region for allocating purposes. At the same time pages might be used for sending data
 * to a different thread which deallocates pages. The other thread should also return mem region to the pool after recording is
 * complete.
 */
public class Region {

    private final int id;
    private final int pagesCount;
    private final int pagesCountMask;
    private final UnsafeBuffer buffer;
    private final List<Page> pages;
    private final AtomicIntegerArray usedPages; // this adds some false sharing but should be rarely accessed
    private final AtomicInteger lastBorrowedPageId = new AtomicInteger(0);
    private volatile State state;

    public Region(int id, UnsafeBuffer buffer, int pagesCount) {
        if (!BitUtil.isPowerOfTwo(pagesCount)) {
            throw new IllegalArgumentException("Page count must be a power of two, but was " + pagesCount);
        }
        this.buffer = buffer;
        this.pagesCount = pagesCount;
        this.usedPages = new AtomicIntegerArray(pagesCount);
        this.pages = new ArrayList<>(pagesCount);
        this.pagesCountMask = pagesCount - 1;
        this.id = id;
        int pageSize = buffer.capacity() / pagesCount;
        for (int pageId = 0; pageId < pagesCount; pageId++) {
            UnsafeBuffer unsafeBuffer = new UnsafeBuffer();
            unsafeBuffer.wrap(buffer, pageId * pageSize, pageSize);
            this.pages.add(new Page(pageId, unsafeBuffer));
        }
    }

    enum State {
        BORROWED,
        FREE
    }

    public Page allocate() {
        int checkPageId = (lastBorrowedPageId.get() + 1) & pagesCountMask;
        int used = usedPages.get(checkPageId);
        if (used == 0) {
            usedPages.lazySet(checkPageId, 1); // single allocator, no CAS is required
            return pages.get(checkPageId);
        }
        // the next page is not returned yet, try some next
        for (int i = 0; i < pagesCount; i++) {
            int iShifted = (checkPageId + i) & pagesCountMask;
            if (usedPages.get(iShifted) == 0) {
                usedPages.lazySet(iShifted, 1);
                return pages.get(iShifted);
            }
        }
        return null;
    }

    public void deallocate(Page page) {
        usedPages.lazySet(page.getId(), 0);
    }

    public UnsafeBuffer getBuffer() {
        return buffer;
    }
}
