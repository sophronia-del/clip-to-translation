package indi.sophronia.tools.cache;

public interface RecycleBin {
    void recycleEntry(String key, Object value);

    static RecycleBin empty() {
        return EmptyRecycleBin.INSTANCE;
    }

    enum EmptyRecycleBin implements RecycleBin {
        INSTANCE;

        @Override
        public void recycleEntry(String key, Object value) {
        }
    }
}
