package logisim.learner.analysis;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Location;

/**
 * 元件的一个端口（对 EndData 的封装），附带 NetlistBuilder
 * 解析后的驱动/读取角色。Pin 的 output=true 时其 EndData 已被
 * Logisim 声明为 INPUT（读取导线），这里无需特判驱动方向。
 */
public final class End {
    private final Component component;
    private final int index;
    private final EndData data;
    private final boolean splitterEnd;

    End(Component component, int index, EndData data, boolean splitterEnd) {
        this.component = component;
        this.index = index;
        this.data = data;
        this.splitterEnd = splitterEnd;
    }

    public Component getComponent() {
        return component;
    }

    public int getIndex() {
        return index;
    }

    public Location getLocation() {
        return data.getLocation();
    }

    public BitWidth getWidth() {
        return data.getWidth();
    }

    /** 该端是否向所在网络输出信号。 */
    public boolean drives() {
        return data.isOutput() || KnownQuirks.isPhantomInput(component, index);
    }

    /** 该端是否从所在网络读取信号。 */
    public boolean reads() {
        return data.isInput() && !KnownQuirks.isPhantomInput(component, index);
    }

    /** 独占输出（非三态）。 */
    public boolean isExclusive() {
        return data.isExclusive() || KnownQuirks.isPhantomInput(component, index);
    }

    /** 是否分线器端口（在网络上表现为中继而非独立驱动源）。 */
    public boolean isSplitterEnd() {
        return splitterEnd;
    }

    public String describe() {
        return component.getFactory().getDisplayName() + " 端口" + index
                + " (" + getLocation() + ")";
    }
}
