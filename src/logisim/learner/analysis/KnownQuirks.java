package logisim.learner.analysis;

import com.cburch.logisim.comp.Component;

/**
 * Logisim 2.7.1 的已知怪癖与教学场景中无害情形的过滤规则。
 */
final class KnownQuirks {
    private KnownQuirks() {
    }

    /**
     * 2.7.1 的 Adder 把第 5 个端口（cout，索引 4）误声明为 INPUT，
     * End 据此修正其真实的输出角色。
     */
    static boolean isPhantomInput(Component comp, int endIndex) {
        return "Adder".equals(comp.getFactory().getName()) && endIndex == 4;
    }

    static boolean isOptionalUnconnectedInput(Component comp, int endIndex) {
        String name = comp.getFactory().getName();
        if (("Adder".equals(name) || "Subtractor".equals(name)
                || "Multiplier".equals(name) || "Divider".equals(name)) && endIndex == 3) {
            return true;
        }
        if ("Multiplexer".equals(name) && endIndex == comp.getEnds().size() - 2) {
            Object enabled = attributeValue(comp, "enable");
            return Boolean.TRUE.equals(enabled);
        }
        if ("Register".equals(name)) {
            return endIndex == 3 || endIndex == 4;
        }
        if (name.endsWith("Flip-Flop")) {
            return endIndex >= comp.getEnds().size() - 3;
        }
        return false;
    }

    private static Object attributeValue(Component comp, String name) {
        for (com.cburch.logisim.data.Attribute<?> attribute
                : comp.getAttributeSet().getAttributes()) {
            if (name.equals(attribute.getName())) {
                return comp.getAttributeSet().getValue(attribute);
            }
        }
        return null;
    }

    /**
     * output 属性为 true 的 Pin：它是整个电路的输出端子，
     * 只读取导线而不驱动；其所在网无驱动时应表述为
     * "输出引脚未被驱动"，而非"输入悬空"。
     */
    static boolean isCircuitOutputPin(Component comp) {
        if (!"Pin".equals(comp.getFactory().getName())) return false;
        Object v = comp.getAttributeSet().getValue(
                com.cburch.logisim.std.wiring.Pin.ATTR_TYPE);
        return Boolean.TRUE.equals(v);
    }

    /**
     * 三态/非独占输出（如带三态的 Pin、缓冲器）：多个此类输出共网
     * 是合法的总线结构，只提示而非报错。
     */
    static boolean isNonExclusiveDriver(End end) {
        return end.drives() && !end.isExclusive();
    }
}
