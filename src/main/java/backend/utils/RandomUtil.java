package backend.utils;

import java.security.SecureRandom;
import java.util.Random;

public class RandomUtil {
    public static byte[] randomBytes(int length) {
        Random r = new SecureRandom();
        byte[] buf = new byte[length];
        //Generates random bytes and places them into a user-supplied byte array.
        r.nextBytes(buf);
        return buf;
    }
}
