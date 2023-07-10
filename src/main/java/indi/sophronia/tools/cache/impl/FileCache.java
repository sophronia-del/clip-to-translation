package indi.sophronia.tools.cache.impl;

import indi.sophronia.tools.cache.CacheFacade;
import indi.sophronia.tools.util.MD5;
import indi.sophronia.tools.util.Rethrow;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FileCache implements CacheFacade {
    public FileCache(String fileName) {
        this.fileName = fileName;
    }

    private final Map<String, Object> md5Digests = new ConcurrentHashMap<>();

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final String fileName;

    @Override
    public Set<String> keys(String pattern) {
        return Collections.emptySet();
    }

    @Override
    public <T> void save(String key, T value, long expireMillis) {
        savePersist(key, value);
    }

    @Override
    public <T> void savePersist(String key, T value) {
        saveFileContent(key, value.toString());
        md5Digests.put(MD5.digest(key), "");
    }

    @Override
    public <T> void saveBatch(Map<String, T> data, long expireMillis) {
        saveBatchPersist(data);
    }

    @Override
    public <T> void saveBatchPersist(Map<String, T> data) {
        data.forEach(this::savePersist);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T load(String key) {
        String digest = MD5.digest(key);
        if (md5Digests.containsKey(digest)) {
            return (T) readFileContent(key);
        }
        return null;
    }

    @Override
    public void invalidate(String key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void invalidateBatch(Collection<String> keys) {
        throw new UnsupportedOperationException();
    }


    /**
     * File IO
     */
    private static class DataChunk {
        static final int fileHeaderSize = 4;

        static final int keyHeaderSize = 4;
        static final int valueHeaderSize = 4;
        static final int maxKeyLength = 1024;
        static final int maxValueLength = 1024;
        static final long chunkSize =
                keyHeaderSize + valueHeaderSize +
                        maxKeyLength + maxValueLength;
    }

    private String readFileContent(String key) {
        try {
            lock.readLock().lock();
            try (FileChannel fileChannel = FileChannel.open(Path.of(fileName), StandardOpenOption.READ)) {
                int count = countOfKeys(fileChannel);

                long keyOffset = offsetOfKey(fileChannel, count, key);
                if (keyOffset < 0) {
                    return null;
                }

                fileChannel.position(keyOffset + DataChunk.keyHeaderSize + DataChunk.maxKeyLength);
                byte[] valueHeader = new byte[DataChunk.valueHeaderSize];
                byte[] valueContainer = new byte[DataChunk.maxValueLength];
                fileChannel.read(new ByteBuffer[]{ByteBuffer.wrap(valueHeader), ByteBuffer.wrap(valueContainer)});
                int valueLength = bytesToInt(valueHeader);
                return new String(valueContainer, 0, valueLength);
            }
        } catch (IOException e) {
            throw Rethrow.rethrow(e);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void saveFileContent(String key, String value) {
        if (key.length() > 512 || value.length() > 512) {
            return;
        }

        try {
            lock.writeLock().lock();

            try (FileChannel fileChannel = FileChannel.open(
                    Path.of(fileName),
                    Set.of(StandardOpenOption.APPEND, StandardOpenOption.READ)
            )) {
                int count = countOfKeys(fileChannel);
                long keyOffset = offsetOfKey(fileChannel, count, key);
                if (keyOffset >= 0) {
                    byte[] valueHeader = new byte[DataChunk.valueHeaderSize];
                    byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);

                    intToBytes(valueBytes.length, valueHeader);
                    long offset = keyOffset + DataChunk.keyHeaderSize + DataChunk.maxKeyLength;
                    fileChannel.write(ByteBuffer.wrap(valueHeader), offset);

                    offset += DataChunk.valueHeaderSize;
                    fileChannel.write(ByteBuffer.wrap(valueBytes), offset);
                    return;
                }


                byte[] fileHeader = new byte[DataChunk.fileHeaderSize];
                intToBytes(count + 1, fileHeader);
                fileChannel.write(ByteBuffer.wrap(fileHeader), 0);

                byte[] keyHeader = new byte[DataChunk.keyHeaderSize];
                byte[] keyContainer = key.getBytes(StandardCharsets.UTF_8);
                byte[] valueHeader = new byte[DataChunk.valueHeaderSize];
                byte[] valueContainer = value.getBytes(StandardCharsets.UTF_8);

                intToBytes(keyContainer.length, keyHeader);
                intToBytes(valueContainer.length, valueHeader);

                long tail = DataChunk.fileHeaderSize + count * DataChunk.chunkSize;
                fileChannel.position(tail);
                fileChannel.write(new ByteBuffer[]{
                        ByteBuffer.wrap(keyHeader),
                        ByteBuffer.wrap(keyContainer),
                        ByteBuffer.wrap(valueHeader),
                        ByteBuffer.wrap(valueContainer)
                });

                // todo data swap
                long nextOffset = offsetOfNextKey(fileChannel, count, key);
            }
        } catch (IOException e) {
            throw Rethrow.rethrow(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private static int countOfKeys(FileChannel fileChannel) throws IOException {
        byte[] fileHeader = new byte[DataChunk.fileHeaderSize];
        int n = fileChannel.read(ByteBuffer.wrap(fileHeader));
        if (n < DataChunk.fileHeaderSize) {
            return 0;
        }

        return bytesToInt(fileHeader);
    }

    /**
     * @return starting position of the row which the key belongs to. -1 if not found
     */
    private static long offsetOfKey(FileChannel fileChannel, int totalCount, String key) throws IOException {
        byte[] keyHeader = new byte[DataChunk.keyHeaderSize];
        byte[] keyContainer = new byte[DataChunk.maxKeyLength];

        for (int low = 0, high = totalCount - 1, mid = (low + high) / 2;
             low < high; mid = (low + high) / 2) {
            long offset = DataChunk.fileHeaderSize + mid * DataChunk.chunkSize;
            int n = fileChannel.read(ByteBuffer.wrap(keyHeader), offset);
            if (n != DataChunk.keyHeaderSize) {
                throw new IOException("fail to read content of the cache file");
            }

            offset += DataChunk.keyHeaderSize;
            int keyLength = bytesToInt(keyHeader);
            fileChannel.read(ByteBuffer.wrap(keyContainer), offset);

            String contentKey = new String(keyContainer, 0, keyLength);
            int cmp = contentKey.compareTo(key);
            if (cmp == 0) {
                return DataChunk.fileHeaderSize + mid * DataChunk.chunkSize;
            }

            if (cmp > 0) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }

        return -1;
    }

    private static long offsetOfNextKey(FileChannel fileChannel, int totalCount, String key) throws IOException {
        byte[] keyHeader = new byte[DataChunk.keyHeaderSize];
        byte[] keyContainer = new byte[DataChunk.maxKeyLength];

        for (int low = 0, high = totalCount - 1, mid = (low + high) / 2;
             low < high; mid = (low + high) / 2) {
            long offset = DataChunk.fileHeaderSize + mid * DataChunk.chunkSize;
            int n = fileChannel.read(ByteBuffer.wrap(keyHeader), offset);
            if (n != DataChunk.keyHeaderSize) {
                throw new IOException("fail to read content of the cache file");
            }

            offset += DataChunk.keyHeaderSize;
            int keyLength = bytesToInt(keyHeader);
            fileChannel.read(ByteBuffer.wrap(keyContainer), offset);

            String contentKey = new String(keyContainer, 0, keyLength);
            int cmp = contentKey.compareTo(key);
            if (cmp == 0) {
                return DataChunk.fileHeaderSize + mid * DataChunk.chunkSize;
            }

            if (cmp > 0) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }

        return DataChunk.keyHeaderSize;
    }

    private static int bytesToInt(byte[] bytes) {
        int i = 0;
        for (byte b : bytes) {
            i <<= Byte.SIZE;
            i |= b;
        }
        return i;
    }

    private static void intToBytes(int v, byte[] bytes) {
        for (int i = 0; i < 4; i++) {
            bytes[3 - i] = (byte) (v & 0xFF);
            v >>= 8;
        }
    }
}
