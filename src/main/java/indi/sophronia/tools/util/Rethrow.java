package indi.sophronia.tools.util;

public class Rethrow {
    @SuppressWarnings("unchecked")
    public static <T extends Throwable> RuntimeException rethrow(Throwable e) throws T {
        throw (T) e;
    }
}
