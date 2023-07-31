package com.ulyp.storage.impl;

import com.ulyp.core.repository.Repository;
import com.ulyp.storage.StorageException;
import com.ulyp.core.util.BitUtil;
import com.ulyp.transport.BinaryRecordedCallStateDecoder;
import com.ulyp.transport.BinaryRecordedCallStateEncoder;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RocksdbIndex implements Repository<Long, RecordedCallState> {

    private final RocksDB db;
    private final WriteOptions writeOptions;
    private final ThreadLocal<MutableDirectBuffer> tempBuffer = ThreadLocal.withInitial(() -> new ExpandableDirectByteBuffer(64 * 1024));
    private final ThreadLocal<BinaryRecordedCallStateDecoder> decoder = ThreadLocal.withInitial(BinaryRecordedCallStateDecoder::new);
    private final ThreadLocal<BinaryRecordedCallStateEncoder> encoder = ThreadLocal.withInitial(BinaryRecordedCallStateEncoder::new);
    private final ThreadLocal<byte[]> keyBuffer = ThreadLocal.withInitial(() -> new byte[Long.BYTES]);
    private final ThreadLocal<byte[]> valueBuffer = ThreadLocal.withInitial(() -> new byte[64 * 1024]);

    public RocksdbIndex() throws StorageException {
        try {
            Path ulyp = Files.createTempDirectory("ulyp");
            ulyp.toFile().deleteOnExit();

            Options options = new Options();
            options.setCreateIfMissing(true);

            db = RocksDB.open(options, ulyp.toAbsolutePath().toString());

            writeOptions = new WriteOptions();
            writeOptions.setSync(false);
            writeOptions.setDisableWAL(true);
        } catch (RocksDBException | IOException ioException) {
            throw new StorageException(ioException);
        }
    }

    @Override
    public RecordedCallState get(Long id) {
        byte[] keyBytes = keyBuffer.get();
        BitUtil.longToBytes(id, keyBytes, 0);
        byte[] bytes;
        try {
            bytes = db.get(keyBytes);
        } catch (RocksDBException e) {
            throw new StorageException("Could not read", e);
        }
        if (bytes == null) {
            return null;
        }
        BinaryRecordedCallStateDecoder decoder = this.decoder.get();
        decoder.wrap(new UnsafeBuffer(bytes), 0, BinaryRecordedCallStateDecoder.BLOCK_LENGTH, 0);
        return RecordedCallState.deserialize(decoder);
    }

    @Override
    public void store(Long id, RecordedCallState value) {
        MutableDirectBuffer buffer = tempBuffer.get();
        BinaryRecordedCallStateEncoder encoder = this.encoder.get();
        encoder.wrap(buffer, 0);
        value.serialize(encoder);
        byte[] keyBytes = keyBuffer.get();
        BitUtil.longToBytes(id, keyBytes, 0);
        int valueEncoded = encoder.encodedLength();
        byte[] valueBytes = getValueBuffer(valueEncoded);
        buffer.getBytes(0, valueBytes);
        try {
            db.put(writeOptions, keyBytes, 0, keyBytes.length, valueBytes, 0, valueEncoded);
        } catch (RocksDBException e) {
            throw new StorageException("Could not write", e);
        }
    }

    private byte[] getValueBuffer(int requiredCapacity) {
        byte[] buf = valueBuffer.get();
        if (requiredCapacity > buf.length) {
            buf = new byte[requiredCapacity];
            valueBuffer.set(buf);
        }
        return buf;
    }
}
