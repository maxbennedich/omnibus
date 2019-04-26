package omnibus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class NetworkData {
    public final List<Stop> stops;
    public final int[] routeNrMapping;
    public final Map<Integer, List<XY>> shapes = new HashMap<>();

    public final int nrStops;
    public final int nrRoutes;

    public NetworkData() {
        try {
            routeNrMapping = Files.lines(Path.of(Omnibus.class.getResource("route_nr_mapping.txt").getFile())).mapToInt(s -> Integer.parseInt(s)).toArray();
            nrRoutes = routeNrMapping.length;

            List<SegmentWithConnections> allSegments = new ArrayList<>();

            int[] idx = {0};
            stops = Files.lines(Path.of(Omnibus.class.getResource("stops.txt").getFile())).map(line -> {
                String[] parts = line.split(",");
                Stop stop = new Stop(idx[0]++, parts[0], Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));

                if (parts.length > 3) {
                    String[] routes = parts[3].split(";");
                    for (String route : routes) {
                        String[] routeParts = route.split(":");
                        int routeNr = Integer.parseInt(routeParts[0]) - 1;
                        int segmentCount = Integer.parseInt(routeParts[1]);
                        var segments = new ArrayList<SegmentWithConnections>();
                        for (int n = 0; n < segmentCount; ++n)
                            segments.add(new SegmentWithConnections());
                        allSegments.addAll(segments);
                        stop.connectionsByRouteNr.put(routeNr, segments);
                    }
                }

                return stop;
            }).collect(Collectors.toList());
            nrStops = stops.size();

            idx[0] = 0;
            Files.lines(Path.of(Omnibus.class.getResource("segments.txt").getFile())).forEach(line -> {
                String[] parts = line.split(",");
                var segment = allSegments.get(idx[0]);
                segment.routeNr = Integer.parseInt(parts[0]) - 1;
                segment.from = stops.get(Integer.parseInt(parts[1]));
                segment.to = stops.get(Integer.parseInt(parts[2]));
                segment.timeFrom = Integer.parseInt(parts[3]);
                segment.timeTo = Integer.parseInt(parts[4]);

                if (parts.length > 5) {
                    String[] connections = parts[5].split(";");
                    for (String c : connections)
                        segment.connections.add(allSegments.get(Integer.parseInt(c)));
                }

                ++idx[0];
            });

            Files.lines(Path.of(Omnibus.class.getResource("shapes.txt").getFile())).forEach(line -> {
                String[] parts = line.split(",");
                int fromId = Integer.parseInt(parts[0]);
                int toId = Integer.parseInt(parts[1]);
                int routeNr = Integer.parseInt(parts[2]) - 1;

                String[] shape = parts[3].split(";");
                var segment = new ArrayList<XY>();
                for (String s : shape) {
                    String[] coords = s.split(":");
                    segment.add(new XY(Double.parseDouble(coords[1]), Double.parseDouble(coords[0])));
                }

                shapes.put(getShapeKey(fromId, toId, routeNr), segment);
            });
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    public static int getShapeKey(int fromId, int toId, int routeNr) {
        return fromId + (toId << 12) + (routeNr << 24);
    }
}
