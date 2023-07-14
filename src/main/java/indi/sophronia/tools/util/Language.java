package indi.sophronia.tools.util;

public enum Language {
    CHINESE(
            new int[]{0x4E00},
            new int[]{0x9FBF}
    ),
    JAPANESE(
            new int[]{0x3040, 0x31F0},
            new int[]{0x30FF, 0x31FF}
    ),
    KOREAN(
            new int[]{0x1100, 0x3130, 0xAC00},
            new int[]{0x11FF, 0x318F, 0xD7AF}
    ),
    ENGLISH(
            new int[]{'a', 'A'},
            new int[]{'z', 'Z'}
    ),


    UNKNOWN(
            new int[0],
            new int[0]
    )
    ;

    final int[] rangesBegin;
    final int[] rangesEnd;

    Language(int[] rangesBegin, int[] rangesEnd) {
        this.rangesBegin = rangesBegin;
        this.rangesEnd = rangesEnd;
    }
}
