package com.ulyp.core.mem;

import com.ulyp.core.AddressableItemIterator;
import com.ulyp.core.bytes.*;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.*;

public class BinaryListTest {

    private MemPageAllocator allocator() {
        return new MemPageAllocator() {

            @Override
            public MemPage allocate() {
                return new MemPage(0, new UnsafeBuffer(new byte[MemPool.PAGE_SIZE]));
            }

            @Override
            public void deallocate(MemPage page) {

            }
        };
    }

    @Test
    public void testBasicSize() {
        OutputBinaryList out = new OutputBinaryList(RecordedMethodCallList.WIRE_ID, new PagedMemBinaryOutput(allocator()));

        OutputBinaryList.Writer writer = out.writer();
        writer.write("AVBACAS");
        writer.commit();

        InputBinaryList inputList = out.flip();

        Assert.assertEquals(1, inputList.size());
    }

    @Test
    public void testWriteByUsingWriter() {
        OutputBinaryList out = new OutputBinaryList(RecordedMethodCallList.WIRE_ID, new PagedMemBinaryOutput(allocator()));

        OutputBinaryList.Writer writer = out.writer();
        writer.write("AVBACAS");
        writer.commit();

        InputBinaryList inputList = out.flip();

        BinaryInput in = inputList.iterator().next();
        Assert.assertEquals("AVBACAS", in.readString());
    }

    @Test
    public void testByAddressAccess() throws IOException {
        OutputBinaryList bytesOut = new OutputBinaryList(RecordedMethodCallList.WIRE_ID, new PagedMemBinaryOutput(allocator()));

        bytesOut.add(out -> {
            out.write(true);
            out.write(5454534534L);
        });
        bytesOut.add(out -> {
            out.write(false);
            out.write(9873434443L);
        });
        bytesOut.add(out -> {
            out.write(true);
            out.write(1233434734L);
        });

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bytesOut.writeTo(new BufferedOutputStream(outputStream));
        byte[] byteArray = outputStream.toByteArray();
        InputBinaryList inputList = bytesOut.flip();

        AddressableItemIterator<BinaryInput> it = inputList.iterator();

        it.next();

        it.next();
        long address2 = it.address();

        UnsafeBuffer unsafeBuffer = new UnsafeBuffer(byteArray, (int) address2, 12);
        BufferBinaryInput in = new BufferBinaryInput(unsafeBuffer);

        assertFalse(in.readBoolean());
        assertEquals(9873434443L, in.readLong());
    }

    @Test
    public void testSimpleReadWrite() {
        OutputBinaryList bytesOut = new OutputBinaryList(RecordedMethodCallList.WIRE_ID, new PagedMemBinaryOutput(allocator()));

        bytesOut.add(out -> {
            out.write('A');
        });
        bytesOut.add(out -> {
            out.write('B');
            out.write('C');
        });
        bytesOut.add(out -> {
            out.write('D');
        });

        InputBinaryList inputList = bytesOut.flip();

        assertEquals(3, inputList.size());

        AddressableItemIterator<BinaryInput> iterator = inputList.iterator();

        assertTrue(iterator.hasNext());

        BinaryInput next = iterator.next();
        assertEquals(2, next.available());
        assertEquals('A', next.readChar());

        assertEquals(OutputBinaryList.HEADER_LENGTH + 4, iterator.address());

        next = iterator.next();
        assertEquals(4, next.available());
        assertEquals('B', next.readChar());
        assertEquals('C', next.readChar());

        assertEquals(OutputBinaryList.HEADER_LENGTH + 10, iterator.address());

        next = iterator.next();
        assertEquals(2, next.available());
        assertEquals('D', next.readChar());

        assertEquals(OutputBinaryList.HEADER_LENGTH + 18, iterator.address());

        assertFalse(iterator.hasNext());
    }
}
