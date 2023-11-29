package ly.count.android.benchmark;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Random;

public class RandomUtil {
    private final Random random;

    protected RandomUtil() {
        random = new Random();
    }

    protected String generateRandomString(int size) {
        int length = random.nextInt(size) + 1; // Random string length between 1 and 20
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            char randomChar = (char) (random.nextInt(26) + 'a'); // Random lowercase letter
            sb.append(randomChar);
        }
        return sb.toString();
    }

    protected int generateRandomInt(int bound) {
        return random.nextInt(bound);
    }

    protected double generateRandomDouble() {
        return random.nextDouble();
    }

    protected Map.Entry<String, Object> generateRandomKeyValuePair() {
        return new AbstractMap.SimpleEntry<>(generateRandomString(8), generateRandomObject());
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
        }

        return value;
    }

    protected Object generateRandomObject() {
        int randomInt = random.nextInt(8);
        Object value;

        //to give it a more chance to create a simple object rather than an array
        switch (randomInt) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
                value = generateRandomImmutable();
                break;
            case 7:
                value = generateRandomArray(this::generateRandomImmutable, random.nextInt(10) + 1);
                break;
            default:
                value = generateRandomObject();
        }

        return value;
    }

    protected Object[] generateRandomArray(Supplier valueSupplier, int times) {
        Object[] array = new Object[times];

        for (int i = 0; i < times; i++) {
            array[i] = valueSupplier.get();
        }

        return array;
    }

    protected interface Supplier {
        Object get();
    }
}
