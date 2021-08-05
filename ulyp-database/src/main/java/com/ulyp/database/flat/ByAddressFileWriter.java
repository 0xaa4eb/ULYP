package com.ulyp.database.flat;

import java.io.*;

public class ByAddressFileWriter implements AutoCloseable {

    private final File file;
    private final OutputStream outputStream;
    private final RandomAccessFile randomAccessFile;

    public ByAddressFileWriter(File file) throws IOException {
        this.file = file;
        this.outputStream = new BufferedOutputStream(new FileOutputStream(file, false));
        this.randomAccessFile = new RandomAccessFile(file, "rw");
    }

    public void writeAt(long addr, long value) throws IOException {
        randomAccessFile.seek(addr);
        randomAccessFile.writeLong(value);
    }

    public void close() throws IOException {
        try {
            outputStream.close();
        } finally {
            randomAccessFile.close();
        }
    }
}
