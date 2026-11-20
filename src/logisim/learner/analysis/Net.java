package logisim.learner.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Location;

/**
 * 一个电气网络：由导线、搭接点和落在导线上的端口合并而成。
 */
public final class Net {
    private final int id;
    private final Set<Location> points = new LinkedHashSet<Location>();
    private final List<End> ends = new ArrayList<End>();

    Net(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public Set<Location> getPoints() {
        return Collections.unmodifiableSet(points);
    }

    public List<End> getEnds() {
        return Collections.unmodifiableList(ends);
    }

    void addPoint(Location loc) {
        points.add(loc);
    }

    void addEnd(End end) {
        ends.add(end);
    }

    public List<End> drivers() {
        List<End> ret = new ArrayList<End>();
        for (End e : ends) {
            if (e.drives()) ret.add(e);
        }
        return ret;
    }

    public List<End> readers() {
        List<End> ret = new ArrayList<End>();
        for (End e : ends) {
            if (e.reads()) ret.add(e);
        }
        return ret;
    }

    /** 网络中出现的不同位宽集合。 */
    public Set<BitWidth> widths() {
        Set<BitWidth> ret = new LinkedHashSet<BitWidth>();
        for (End e : ends) {
            BitWidth w = e.getWidth();
            if (w != null) ret.add(w);
        }
        return ret;
    }
}
