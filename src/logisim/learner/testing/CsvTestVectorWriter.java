package logisim.learner.testing;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class CsvTestVectorWriter {
    private CsvTestVectorWriter() {
    }

    public static void write(File file, TestSuite suite) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write('\ufeff');
            for (TestSignal input : suite.getInputs()) {
                writer.write(csv(input.getName()));
                writer.write(',');
            }
            for (TestSignal output : suite.getOutputs()) {
                writer.write(csv("expected:" + output.getName()));
                writer.write(',');
            }
            writer.write("ticks,reset");
            writer.newLine();
            for (TestVector vector : suite.getVectors()) {
                for (TestSignal input : suite.getInputs()) {
                    writer.write(hex(vector.getInputs().get(input.getName())));
                    writer.write(',');
                }
                for (TestSignal output : suite.getOutputs()) {
                    Integer expected = vector.getExpectedOutputs().get(output.getName());
                    writer.write(expected == null ? "" : hex(expected));
                    writer.write(',');
                }
                writer.write(Integer.toString(vector.getTicks()));
                writer.write(',');
                writer.write(Boolean.toString(vector.isResetBefore()));
                writer.newLine();
            }
        }
    }

    private static String hex(Integer value) {
        if (value == null) {
            throw new IllegalArgumentException("脚本缺少输入值");
        }
        return "0x" + Integer.toHexString(value.intValue()).toUpperCase();
    }

    private static String csv(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
