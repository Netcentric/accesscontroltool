package biz.netcentric.cq.tools.actool.configmodel;

import java.security.SecureRandom;
import java.util.Random;

public class RandomPassword {

    static final char[] ALLOWED_CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGJKLMNPRSTUVWXYZ0123456789".toCharArray();

    private RandomPassword() {
    }

    public static char[] generate(int length) {
        Random random = new SecureRandom();
        char[] password = new char[length];
        for (int i = 0; i < length; i++) {
            password[i] = ALLOWED_CHARACTERS[random.nextInt(ALLOWED_CHARACTERS.length)];
        }
        return password;
    }

}

