package cpw.mods.forge.serverpacklocator.utils;

import java.util.function.Consumer;

public class ObjectUtils {

    public static <T> T make(T t, Consumer<T> consumer) {
        consumer.accept(t);
        return t;
    }
}
