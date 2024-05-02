package ly.count.android.benchmark;

import java.util.Random;

public class RandomUtil {
    private final Random random;

    public RandomUtil() {
        random = new Random();
    }

    public String generateRandomString(int size) {
        int length = random.nextInt(size) + 1; // Random string length between 1 and 20
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            char randomChar = (char) (random.nextInt(26) + 'a'); // Random lowercase letter
            sb.append(randomChar);
        }
        return sb.toString();
    }

    public int generateRandomInt(int bound) {
        return random.nextInt(bound);
    }

    public double generateRandomDouble() {
        return random.nextDouble();
    }

    protected Object generateRandomImmutable() {
        int randomInt = random.nextInt(6);
        Object value;
        switch (randomInt) {
            case 0:
                value = random.nextInt();
                break;
            case 1:
                value = random.nextBoolean();
                break;
            case 2:
                value = random.nextLong();
                break;
            case 3:
                value = random.nextFloat();
                break;
            case 4:
                value = random.nextDouble();
                break;
            case 5:
                value = generateRandomString(20);
                break;
            default:
                value = "default";
                break;
        }

        return value;
    }
}
