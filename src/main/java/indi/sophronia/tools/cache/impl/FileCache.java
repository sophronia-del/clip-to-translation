package indi.sophronia.tools.cache.impl;

import indi.sophronia.tools.cache.CacheFacade;
import indi.sophronia.tools.util.StringHelper;
import indi.sophronia.tools.util.Rethrow;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// fixme index file
public class FileCache implements CacheFacade {
    public FileCache(String fileName) {
        this.indexFileName = fileName + ".index.kvdb";
        this.dataFileName = fileName + ".data.kvdb";
    }

    private final Map<String, Object> md5Digests = new ConcurrentHashMap<>();

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final String indexFileName;

    private final String dataFileName;

    @Override
    public Set<String> keys(String pattern) {
        Set<String> allKeys = new HashSet<>(List.of(readAllKeys()));
        StringHelper.filterKeysByPattern(allKeys, pattern);
        return allKeys;
    }

    @Override
    public <T> void save(String key, T value, long expireMillis) {
        savePersist(key, value);
    }

    @Override
    public <T> void savePersist(String key, T value) {
        saveFileContent(key, value.toString());
        md5Digests.put(StringHelper.digest(key), "");
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
        String digest = StringHelper.digest(key);
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
    private static class DataSize {
        static final int indexCountSize = Integer.BYTES;
        static final int indexFileHeaderSize = indexCountSize;


        // index row -> key_length int, key byte(512), offset long
        static final int indexKeyLength = Integer.BYTES;
        static final int indexMaxKeyLength = 512;
        static final int indexCursorSize = Long.BYTES;
        static final long indexRowLength = indexKeyLength + indexMaxKeyLength + indexCursorSize;

        // data row -> value_length int, value text(variable)
        static final int valueHeaderSize = Integer.BYTES;

        static final byte[][] swapBuffers = new byte[][]{
                new byte[(int) (indexRowLength)],
                new byte[(int) (2 * indexRowLength)],
                new byte[(int) (4 * indexRowLength)],
                new byte[(int) (8 * indexRowLength)],
                new byte[(int) (16 * indexRowLength)],
                new byte[(int) (32 * indexRowLength)],
        };
    }

    @Override
    public void init() {
        Path indexPath = Path.of(this.indexFileName);
        Path dataPath = Path.of(this.dataFileName);
        try {
            if (!Files.exists(indexPath)) {
                Files.createFile(indexPath);
            }
            if (!Files.exists(dataPath)) {
                Files.createFile(dataPath);
            }
        } catch (IOException e) {
            throw Rethrow.rethrow(e);
        }

        String[] keys = readAllKeys();
        for (String key : keys) {
            md5Digests.put(StringHelper.digest(key), "");
        }
    }

    @Override
    public void destroy() {
        CacheFacade.super.destroy();
    }

    private String[] readAllKeys() {
        try {
            lock.readLock().lock();

            try (FileChannel fileChannel = FileChannel.open(Path.of(indexFileName), StandardOpenOption.READ)) {
                int count = countOfKeys(fileChannel);
                if (count == 0) {
                    return new String[0];
                }

                String[] container = new String[count];

                byte[] keyHeader = new byte[DataSize.indexKeyLength];
                byte[] keyContainer = new byte[DataSize.indexMaxKeyLength];
                byte[] cursorContainer = new byte[DataSize.indexCursorSize];

                for (int i = 0; i < count; i++) {
                    fileChannel.read(new ByteBuffer[]{
                            ByteBuffer.wrap(keyHeader),
                            ByteBuffer.wrap(keyContainer),
                            ByteBuffer.wrap(cursorContainer)
                    });

                    int length = bytesToInt(keyHeader);
                    String key = new String(keyContainer, 0, length, StandardCharsets.UTF_8);
                    container[i] = key;
                }

                return container;
            }
        } catch (IOException e) {
            throw Rethrow.rethrow(e);
        } finally {
            lock.readLock().unlock();
        }
    }

    private String readFileContent(String key) {
        try {
            lock.readLock().lock();

            long dataCursor;
            try (FileChannel fileChannel = FileChannel.open(Path.of(indexFileName), StandardOpenOption.READ)) {
                int count = countOfKeys(fileChannel);

                dataCursor = indexOf(fileChannel, count, key);
                if (dataCursor < 0) {
                    return null;
                }
            }

            try (FileChannel fileChannel = FileChannel.open(Path.of(dataFileName), StandardOpenOption.READ)) {
                fileChannel.position(dataCursor);
                byte[] valueHeader = new byte[DataSize.valueHeaderSize];
                if (fileChannel.read(ByteBuffer.wrap(valueHeader)) < DataSize.valueHeaderSize) {
                    throw new IOException("fail to read cache file");
                }

                int valueLength = bytesToInt(valueHeader);
                byte[] valueContainer = new byte[valueLength];
                if (fileChannel.read(ByteBuffer.wrap(valueContainer)) < valueLength) {
                    throw new IOException("fail to read cache file");
                }

                return new String(valueContainer, StandardCharsets.UTF_8);
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

            int totalCount;
            int indexRowId;
            try (FileChannel indexChannel = FileChannel.open(Path.of(indexFileName), StandardOpenOption.READ)) {
                totalCount = countOfKeys(indexChannel);

                long dataCursor = indexOf(indexChannel, totalCount, key);
                if (dataCursor >= 0) {
                    return;
                }

                indexRowId = (int) -dataCursor - 1;
            }

            long cursor;
            try (FileChannel fileChannel = FileChannel.open(
                    Path.of(dataFileName),
                    StandardOpenOption.APPEND
            )) {
                cursor = fileChannel.position();

                byte[] valueHeader = new byte[DataSize.valueHeaderSize];
                byte[] valueContainer = value.getBytes(StandardCharsets.UTF_8);

                intToBytes(valueContainer.length, valueHeader);

                fileChannel.write(new ByteBuffer[]{
                        ByteBuffer.wrap(valueHeader),
                        ByteBuffer.wrap(valueContainer)
                });
            }

            try (FileChannel fileChannel = FileChannel.open(
                    Path.of(indexFileName),
                    Set.of(StandardOpenOption.WRITE, StandardOpenOption.READ)
            )) {
                byte[] fileHeader = new byte[DataSize.indexCountSize];
                intToBytes(totalCount + 1, fileHeader);
                fileChannel.write(ByteBuffer.wrap(fileHeader));

                fileChannel.position(fileChannel.size());
                fileChannel.write(ByteBuffer.wrap(DataSize.swapBuffers[0]));

                int diff = totalCount - indexRowId;
                if (diff > 0) {
                    int batchShift = Math.min(Integer.numberOfTrailingZeros(diff), 5);
                    int batchSize = 1 << batchShift;
                    int destinationRowId = totalCount - batchSize;

                    while (diff > 0) {
                        batchShift = Math.min(Integer.numberOfTrailingZeros(diff), 5);
                        batchSize = 1 << batchShift;

                        long sourcePosition = DataSize.indexFileHeaderSize +
                                destinationRowId * DataSize.indexRowLength;
                        fileChannel.position(sourcePosition);
                        fileChannel.read(ByteBuffer.wrap(DataSize.swapBuffers[batchShift]));
                        fileChannel.position(sourcePosition + DataSize.indexRowLength);
                        fileChannel.write(ByteBuffer.wrap(DataSize.swapBuffers[batchShift]));

                        diff -= batchSize;
                        destinationRowId -= batchSize;
                    }
                }

                byte[] keyHeader = new byte[DataSize.indexKeyLength];
                byte[] keyContainer = new byte[DataSize.indexMaxKeyLength];
                byte[] cursorContainer = new byte[DataSize.indexCursorSize];

                byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
                System.arraycopy(keyBytes, 0, keyContainer, 0, keyBytes.length);

                intToBytes(keyBytes.length, keyHeader);
                longToBytes(cursor, cursorContainer);

                fileChannel.position(DataSize.indexFileHeaderSize +
                        indexRowId * DataSize.indexRowLength);
                fileChannel.write(new ByteBuffer[]{
                        ByteBuffer.wrap(keyHeader),
                        ByteBuffer.wrap(keyContainer),
                        ByteBuffer.wrap(cursorContainer)
                });
            }
        } catch (IOException e) {
            throw Rethrow.rethrow(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private static int countOfKeys(FileChannel fileChannel) throws IOException {
        byte[] fileHeader = new byte[DataSize.indexCountSize];
        int n = fileChannel.read(ByteBuffer.wrap(fileHeader));
        if (n < DataSize.indexCountSize) {
            return 0;
        }

        return bytesToInt(fileHeader);
    }

    /**
     * if key exists in the index file, returns its cursor value;
     * otherwise returns negative value of its possible row id.
     * behavior is similar to {@link Arrays#binarySearch}
     */
    private static long indexOf(FileChannel fileChannel, int totalCount, String key) throws IOException {
        byte[] keyHeader = new byte[DataSize.indexKeyLength];
        byte[] keyContainer = new byte[DataSize.indexMaxKeyLength];
        byte[] cursorContainer = new byte[DataSize.indexCursorSize];

        int low = 0;
        int high = totalCount - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            long offset = DataSize.indexFileHeaderSize + mid * DataSize.indexRowLength;
            fileChannel.position(offset);
            fileChannel.read(new ByteBuffer[]{
                    ByteBuffer.wrap(keyHeader),
                    ByteBuffer.wrap(keyContainer),
                    ByteBuffer.wrap(cursorContainer)
            });

            int keyLength = bytesToInt(keyHeader);
            String indexKey = new String(
                    keyContainer, 0, keyLength,
                    StandardCharsets.UTF_8
            );

            int cmp = indexKey.compareTo(key);
            if (cmp == 0) {
                return bytesToLong(cursorContainer);
            }

            if (cmp < 0)
                low = mid + 1;
            else
                high = mid - 1;
        }

        return -(low + 1);
    }

    private static int bytesToInt(byte[] bytes) {
        int v = 0;
        for (int i = 0; i < Integer.BYTES; i++) {
            v <<= Byte.SIZE;
            v |= Byte.toUnsignedInt(bytes[i]);
        }
        return v;
    }

    private static void intToBytes(int v, byte[] bytes) {
        for (int i = 0; i < Integer.BYTES; i++) {
            bytes[Integer.BYTES - 1 - i] = (byte) (v & 0xFF);
            v >>= 8;
        }
    }

    private static long bytesToLong(byte[] bytes) {
        long v = 0;
        for (int i = 0; i < Long.BYTES; i++) {
            v <<= Byte.SIZE;
            v |= Byte.toUnsignedLong(bytes[i]);
        }
        return v;
    }

    private static void longToBytes(long v, byte[] bytes) {
        for (int i = 0; i < Long.BYTES; i++) {
            bytes[Long.BYTES - 1 - i] = (byte) (v & 0xFF);
            v >>= 8;
        }
    }
}
