package omnibus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Stop {
    final int nr;
    final String name;
    final double lat;
    final double lng;
    Map<Integer, List<SegmentWithConnections>> connectionsByRouteNr = new HashMap<>();

    Stop(int nr, String name, double lat, double lng) {
        this.nr = nr;
        this.name = name;
        this.lat = lat;
        this.lng = lng;
    }
}
