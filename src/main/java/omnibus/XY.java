package omnibus;

public class XY {
    public final double x, y;

    public XY(double x, double y) {
        this.x = x;
        this.y = y;
    }

    @Override public int hashCode() { return Double.hashCode(x) * 31 + Double.hashCode(y); }

    @Override public boolean equals(Object o) {
        if (!(o instanceof XY))
            return false;
        XY xy = (XY) o;
        return xy.x == x && xy.y == y;
    }

    @Override public String toString() { return x + "," + y; }
}