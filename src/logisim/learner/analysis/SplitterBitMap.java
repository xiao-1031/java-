package logisim.learner.analysis;

import java.util.ArrayList;
import java.util.List;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;

/**
 * 分线器（circuit.Splitter）的位映射，全部通过公开的
 * AttributeSet/Attribute API 读取：
 *   "fanout"  -> Integer 分出的端数
 *   "incoming"-> BitWidth 合并端位宽
 *   "bit"+i   -> Integer 第 i 位归属的端号（0 表示悬空，否则为端号+1）
 * 端 0 为合并端（位于元件锚点），端 1..fanout 为分出端。
 */
public final class SplitterBitMap {
    private final int fanout;
    private final BitWidth width;
    /** bitEnd[i] = 第 i 位归属的端号，0 表示未接。 */
    private final int[] bitEnd;

    private SplitterBitMap(int fanout, BitWidth width, int[] bitEnd) {
        this.fanout = fanout;
        this.width = width;
        this.bitEnd = bitEnd;
    }

    public static boolean isSplitter(Component comp) {
        return "Splitter".equals(comp.getFactory().getName());
    }

    /** 从公开属性解析；属性缺失时返回 null（调用方退化为端独立处理）。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static SplitterBitMap read(Component comp) {
        AttributeSet attrs = comp.getAttributeSet();
        Integer fanout = null;
        BitWidth width = null;
        List<Integer> bits = new ArrayList<Integer>();
        int maxBit = -1;
        for (Attribute attr : attrs.getAttributes()) {
            String name = attr.getName();
            Object value = attrs.getValue(attr);
            if ("fanout".equals(name) && value instanceof Integer) {
                fanout = (Integer) value;
            } else if ("incoming".equals(name) && value instanceof BitWidth) {
                width = (BitWidth) value;
            } else if (name.startsWith("bit") && value instanceof Integer) {
                int which;
                try {
                    which = Integer.parseInt(name.substring(3));
                } catch (NumberFormatException e) {
                    continue;
                }
                while (bits.size() <= which) bits.add(Integer.valueOf(0));
                bits.set(which, (Integer) value);
                if (which > maxBit) maxBit = which;
            }
        }
        if (fanout == null || width == null || maxBit < 0) return null;
        int[] bitEnd = new int[maxBit + 1];
        for (int i = 0; i <= maxBit; i++) {
            bitEnd[i] = bits.get(i).intValue();
        }
        return new SplitterBitMap(fanout.intValue(), width, bitEnd);
    }

    public int getFanout() {
        return fanout;
    }

    public BitWidth getWidth() {
        return width;
    }

    /** 分出端 j（1..fanout）与合并端 0 是否共享位（即 j 是否接了任何位）。 */
    public boolean endSharesWithSpine(int endIndex) {
        if (endIndex == 0) return bitEnd.length > 0;
        for (int b : bitEnd) {
            if (b == endIndex) return true;
        }
        return false;
    }

    /** 分出端 j 的位宽（分配到该端的位数）。 */
    public int endWidth(int endIndex) {
        if (endIndex == 0) return bitEnd.length;
        int n = 0;
        for (int b : bitEnd) {
            if (b == endIndex) n++;
        }
        return n;
    }
}
