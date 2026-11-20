package logisim.learner.testing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class TemporalInputGenerator {
    public List<TestVector> generate(List<TestSignal> inputs, int maxCases,
            int ticks, boolean resetBefore, long seed) {
        List<TestVector> vectors = new ArrayList<TestVector>();
        if (maxCases <= 0) {
            return vectors;
        }
        int[] previous = null;
        previous = add(vectors, inputs, previous, constant(inputs, 0),
                ticks, resetBefore, maxCases);
        previous = add(vectors, inputs, previous, constant(inputs, -1),
                ticks, resetBefore, maxCases);
        previous = add(vectors, inputs, previous, constant(inputs, 0),
                ticks, resetBefore, maxCases);
        for (int i = 0; i < inputs.size() && vectors.size() < maxCases; i++) {
            int width = inputs.get(i).getWidth();
            previous = add(vectors, inputs, previous, single(inputs, i, 1),
                    ticks, resetBefore, maxCases);
            previous = add(vectors, inputs, previous,
                    single(inputs, i, mask(width, -1)), ticks, resetBefore, maxCases);
            previous = add(vectors, inputs, previous,
                    single(inputs, i, mask(width, -2)), ticks, resetBefore, maxCases);
            previous = add(vectors, inputs, previous, constant(inputs, 0),
                    ticks, resetBefore, maxCases);
        }

        Random random = new Random(seed);
        while (vectors.size() < maxCases) {
            int[] candidate = new int[inputs.size()];
            for (int i = 0; i < inputs.size(); i++) {
                candidate[i] = mask(inputs.get(i).getWidth(), random.nextInt());
            }
            if (Arrays.equals(candidate, previous) && inputs.size() > 0) {
                int width = inputs.get(0).getWidth();
                candidate[0] = mask(width, candidate[0] + 1);
            }
            previous = add(vectors, inputs, previous, candidate,
                    ticks, resetBefore, maxCases);
        }
        return vectors;
    }

    private static int[] constant(List<TestSignal> inputs, int value) {
        int[] values = new int[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) {
            values[i] = mask(inputs.get(i).getWidth(), value);
        }
        return values;
    }

    private static int[] single(List<TestSignal> inputs, int index, int value) {
        int[] values = constant(inputs, 0);
        values[index] = value;
        return values;
    }

    private static int[] add(List<TestVector> vectors, List<TestSignal> inputs,
            int[] previous, int[] values, int ticks, boolean resetBefore, int maxCases) {
        if (vectors.size() >= maxCases) {
            return previous;
        }
        Map<String, Integer> map = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < inputs.size(); i++) {
            map.put(inputs.get(i).getName(), values[i]);
        }
        vectors.add(new TestVector(map, new LinkedHashMap<String, Integer>(), ticks,
                resetBefore && vectors.isEmpty()));
        return values.clone();
    }

    private static int mask(int width, int value) {
        return width >= 32 ? value : value & ((1 << width) - 1);
    }
}
