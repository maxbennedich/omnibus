package omnibus;

import java.util.ArrayList;
import java.util.List;

class SegmentWithConnections {
    int routeNr;
    Stop from;
    Stop to;
    int timeFrom;
    int timeTo;
    List<SegmentWithConnections> connections = new ArrayList<>();

    @Override public String toString() {
        return String.format("Bus #%d (internal) from %s at %s, arriving %s at %s",
                routeNr, from.name, Utils.formatTime(timeFrom), to.name, Utils.formatTime(timeTo));
    }
}
