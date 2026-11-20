package logisim.learner.testing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CsvTestVectorLoader {

    private CsvTestVectorLoader() {
    }

    public static TestSuite load(File file, List<TestSignal> signals,
            int defaultTicks, boolean defaultReset) {
        if (defaultTicks < 0) { throw new IllegalArgumentException("默认 ticks 不能为负数"); }
        List<String[]> rows = new ArrayList<String[]>();
        List<Integer> lineNumbers = new ArrayList<Integer>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), "UTF-8"));
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (lineNo == 1 && line.startsWith("﻿")) {
                    line = line.substring(1);
                }
                if (line.trim().isEmpty()) {
                    continue;
                }
                rows.add(parseLine(line, lineNo));
                lineNumbers.add(lineNo);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "无法读取 CSV 文件 " + file.getName() + "：" + e.getMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "CSV 文件为空：" + file.getName());
        }

        String[] header = rows.get(0);
        Map<String, TestSignal> inputsByName = new HashMap<String, TestSignal>();
        Map<String, TestSignal> outputsByName = new HashMap<String, TestSignal>();
        for (TestSignal signal : signals) {
            if (signal.isInput()) {
                inputsByName.put(signal.getName(), signal);
            } else {
                outputsByName.put(signal.getName(), signal);
            }
        }

        int columnCount = header.length;
        int[] inputColumn = new int[columnCount];
        TestSignal[] inputSignal = new TestSignal[columnCount];
        TestSignal[] expectedSignal = new TestSignal[columnCount];
        int ticksColumn = -1;
        int resetColumn = -1;
        Set<String> seenHeaders = new HashSet<String>();
        Set<String> coveredInputs = new HashSet<String>();
        for (int c = 0; c < columnCount; c++) {
            String title = header[c].trim();
            if (title.isEmpty()) {
                throw new IllegalArgumentException(
                        "第 1 行：第 " + (c + 1) + " 列的表头为空");
            }
            if (!seenHeaders.add(title)) {
                throw new IllegalArgumentException(
                        "第 1 行：表头重复：" + title);
            }
            if (title.equals("ticks")) {
                ticksColumn = c;
            } else if (title.equals("reset")) {
                resetColumn = c;
            } else if (title.startsWith("expected:")) {
                String outputName = title.substring("expected:".length()).trim();
                TestSignal output = outputsByName.get(outputName);
                if (output == null) {
                    throw new IllegalArgumentException(
                            "第 1 行：未知输出引脚：" + outputName);
                }
                expectedSignal[c] = output;
            } else {
                TestSignal input = inputsByName.get(title);
                if (input == null) {
                    if (outputsByName.containsKey(title)) {
                        throw new IllegalArgumentException(
                                "第 1 行：" + title + " 是输出引脚，请使用 expected:"
                                + title + " 作为表头");
                    }
                    throw new IllegalArgumentException(
                            "第 1 行：未知输入引脚：" + title);
                }
                inputColumn[c] = 1;
                inputSignal[c] = input;
                coveredInputs.add(title);
            }
        }
        for (TestSignal signal : signals) {
            if (signal.isInput() && !coveredInputs.contains(signal.getName())) {
                throw new IllegalArgumentException(
                        "第 1 行：缺少输入引脚对应的列：" + signal.getName());
            }
        }

        List<TestVector> vectors = new ArrayList<TestVector>();
        for (int r = 1; r < rows.size(); r++) {
            int lineNo = lineNumbers.get(r).intValue();
            String[] cells = rows.get(r);
            if (cells.length != columnCount) {
                throw new IllegalArgumentException("第 " + lineNo + " 行：列数 "
                        + cells.length + " 与表头列数 " + columnCount + " 不一致");
            }
            Map<String, Integer> inputs = new LinkedHashMap<String, Integer>();
            Map<String, Integer> expected = new LinkedHashMap<String, Integer>();
            int ticks = defaultTicks;
            boolean reset = defaultReset && vectors.isEmpty();
            for (int c = 0; c < columnCount; c++) {
                String cell = cells[c].trim();
                if (c == ticksColumn) {
                    if (!cell.isEmpty()) {
                        ticks = (int) parseNumber(cell, lineNo, 31, "ticks");
                    }
                } else if (c == resetColumn) {
                    if (!cell.isEmpty()) {
                        reset = parseReset(cell, lineNo);
                    }
                } else if (inputColumn[c] == 1) {
                    if (cell.isEmpty()) {
                        throw new IllegalArgumentException("第 " + lineNo
                                + " 行：输入 " + inputSignal[c].getName() + " 的值为空");
                    }
                    inputs.put(inputSignal[c].getName(), parseNumber(cell,
                            lineNo, inputSignal[c].getWidth(),
                            inputSignal[c].getName()));
                } else if (expectedSignal[c] != null) {
                    if (!cell.isEmpty()) {
                        expected.put(expectedSignal[c].getName(), parseNumber(cell,
                                lineNo, expectedSignal[c].getWidth(),
                                expectedSignal[c].getName()));
                    }
                }
            }
            vectors.add(new TestVector(inputs, expected, ticks, reset));
        }
        if (vectors.isEmpty()) {
            throw new IllegalArgumentException(
                    "CSV 文件只有表头，没有任何测试向量：" + file.getName());
        }
        String description = "来自 CSV 文件：" + file.getName()
                + "（共 " + vectors.size() + " 条测试向量）";
        return new TestSuite(signals, vectors, description);
    }

    private static int parseNumber(String text, int lineNo, int width, String name) {
        long value;
        try {
            if (text.startsWith("0x") || text.startsWith("0X")) {
                value = Long.parseLong(text.substring(2), 16);
            } else {
                value = Long.parseLong(text);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("第 " + lineNo + " 行：" + name
                    + " 的值不是合法的十进制或 0x 十六进制数：" + text);
        }
        if (width < 1 || width > 32 || value < 0 || value > 0xFFFFFFFFL || (width < 32 && value >= (1L << width))) {
            throw new IllegalArgumentException("第 " + lineNo + " 行：" + name
                    + " 的值 " + text + " 超出 " + width + " 位位宽范围");
        }
        return (int) (value & 0xFFFFFFFFL);
    }

    private static boolean parseReset(String text, int lineNo) {
        if (text.equalsIgnoreCase("true") || text.equals("1")) {
            return true;
        }
        if (text.equalsIgnoreCase("false") || text.equals("0")) {
            return false;
        }
        throw new IllegalArgumentException("第 " + lineNo
                + " 行：reset 列只支持 true/false/1/0，实际为：" + text);
    }

    private static String[] parseLine(String line, int lineNo) {
        List<String> cells = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = false;
                        closedQuote = true;
                    }
                } else {
                    current.append(ch);
                }
            } else if (closedQuote && ch != ',') {
                throw new IllegalArgumentException("第 " + lineNo + " 行：闭合引号后只能是逗号或行尾");
            } else if (ch == '"' && current.length() == 0) {
                quoted = true;
            } else if (ch == ',') {
                cells.add(current.toString());
                current.setLength(0);
                closedQuote = false;
            } else {
                current.append(ch);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException(
                    "第 " + lineNo + " 行：引号未闭合");
        }
        cells.add(current.toString());
        return cells.toArray(new String[cells.size()]);
    }
}



