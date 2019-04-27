package omnibus;

import uk.me.jstott.jcoord.LatLng;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Collectors;

public class JourneyExporter {
    private static ArrayList<Omnibus.QueueState> getJourney(Omnibus.QueueState state) {
        var journey = new ArrayList<Omnibus.QueueState>();
        for (var s = state; s != null; s = s.parent)
            journey.add(s);

        Collections.reverse(journey);

        return journey;
    }

    public static void export(NetworkData data, Omnibus.QueueState state) {
        var journey = getJourney(state);

        var segments = new ArrayList<KMLSegment>();
        var points = new ArrayList<KMLPoint>();

        points.add(new KMLPoint("Start (#1)", "Change", new XY(journey.get(0).segment.from.lng, journey.get(0).segment.from.lat)));

        for (int i = 0, routeCounter = 2; i < journey.size(); ++i, ++routeCounter) {
            if (i > 0 && journey.get(i-1).segment.to.nr != journey.get(i).segment.from.nr) {
                // walk segment
                var s1 = journey.get(i-1).segment;
                var s2 = journey.get(i).segment;
                LatLng ll1 = new LatLng(s1.to.lat, s1.to.lng);
                LatLng ll2 = new LatLng(s2.from.lat, s2.from.lng);
                double dist = ll1.distance(ll2) * 1000;
                int walkingTime = (int)(Omnibus.STOP_CHANGE_TIME_SECONDS + dist / Omnibus.WALKING_SPEED_METERS_PER_SECOND + 0.5);
                double avgSpeedToDeparture = dist / (s2.timeFrom - s1.timeTo);
                int t0 = s1.timeTo;
                String distStr = String.format("%.0f m", dist);
                String walkingModeStr = avgSpeedToDeparture >= 0.4 ? "Run" : "Walk";
                String str = String.format("%s  %s  %-8s%-9s%s - %s", Utils.formatTime(t0), Utils.formatTime(t0 + walkingTime), walkingModeStr, distStr, s1.to.name, s2.from.name);
                System.out.println(str);

                var line = Arrays.asList(new XY(ll1.getLng(), ll1.getLat()), new XY(ll2.getLng(), ll2.getLat()));
                segments.add(new KMLSegment(str, "Walk", line));
                points.remove(points.size() - 1); // remove previous "change"
                points.add(new KMLPoint("Walk from here (#" + (routeCounter-1) + ")", "Change", new XY(s1.to.lng, s1.to.lat)));
                points.add(new KMLPoint("Walk to here (#" + (routeCounter-1) + ")", "Change", new XY(s2.from.lng, s2.from.lat)));
            }

            var seg1 = journey.get(i).segment;
            String routeStr = "Bus " + data.routeNrMapping[seg1.routeNr];
            int stopCount = 1;
            String stopsStr = seg1.from.name + " - ";
            var lines = new ArrayList<XY>();
            for (; i < journey.size()-1 && seg1.routeNr == journey.get(i+1).segment.routeNr; ++i, ++stopCount) {
                stopsStr += journey.get(i+1).segment.from.name + " - ";

                var segm = journey.get(i).segment;
                lines.addAll(data.shapes.get(NetworkData.getShapeKey(segm.from.nr, segm.to.nr, segm.routeNr)));
                points.add(new KMLPoint("Stop (#" + (routeCounter - 1) + ")", "Stop", new XY(segm.to.lng, segm.to.lat)));
            }
            String stopCountStr = stopCount + " stop" + (stopCount == 1 ? "" : "s");
            var seg2 = journey.get(i).segment;
            stopsStr += seg2.to.name;
            String str = String.format("%s  %s  %-8s%-9s%s", Utils.formatTime(seg1.timeFrom), Utils.formatTime(seg2.timeTo), routeStr, stopCountStr, stopsStr);
            System.out.println(str);

            lines.addAll(data.shapes.get(NetworkData.getShapeKey(seg2.from.nr, seg2.to.nr, seg2.routeNr)));
            String pointName = i == journey.size() - 1 ? "Finish (#" + (routeCounter - 1) + ")" : "Change (#" + routeCounter + ")";
            points.add(new KMLPoint(pointName, "Change", new XY(seg2.to.lng, seg2.to.lat)));
            segments.add(new KMLSegment(str, "Bus", lines));
        }

        int routeCount = Integer.bitCount(state.routesUsed);
        try {
            Files.createDirectories(Paths.get("kml"));
            File f = Paths.get("kml", "journey-" + routeCount + ".kml").toFile();
            KMLWriter.writeJourney(new PrintStream(f), segments, points);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    /** Export all routes and stops in KML format. */
    public static void exportAllRoutes(NetworkData data) {
        var segments = new ArrayList<KMLSegment>();
        var stops = new HashSet<XY>();

        for (var shape : data.shapes.values()) {
            stops.add(shape.get(0));
            stops.add(shape.get(shape.size()-1));
            segments.add(new KMLSegment("", "Bus", shape));
        }

        var points = stops.stream().map(s -> new KMLPoint("", "Change", s)).collect(Collectors.toList());

        try {
            Files.createDirectories(Paths.get("kml"));
            File f = Paths.get("kml", "all-routes.kml").toFile();
            KMLWriter.writeJourney(new PrintStream(f), segments, points);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    static class Pair<P, Q> {
        public final P p;
        public final Q q;

        public Pair(P p, Q q) {
            this.p = p;
            this.q = q;
        }
    }

    private static final int[] COLOR = {
            0xe25241, 0xd73964, 0x9036aa, 0x6040af, 0x4053ae,
            0x4396eb, 0x45a8ed, 0x4fb8d0, 0x3e9388, 0x66ab5b,
            0x97bf5c, 0xcfd959, 0xfce960, 0xf6c144, 0xf29c38,
            0xed6337, 0x74564a, 0x9e9e9e, 0x657c89, 0x455a64, 0x757575};

    /** Export the entire route graph in GraphML format, for visualization with e.g. Gephi. */
    public static void exportRouteGraph() {
        try {
            var nodeRouteNr = new ArrayList<Integer>();
            var links = new ArrayList<Pair<Integer, Integer>>();

            int[] idx = {0};
            Files.lines(Path.of(Omnibus.class.getResource("segments.txt").getFile())).forEach(line -> {
                String[] parts = line.split(",");
                int routeNr = Integer.parseInt(parts[0]) - 1;
                nodeRouteNr.add(routeNr);
                if (parts.length > 5) {
                    String[] connections = parts[5].split(";");
                    for (String c : connections)
                        links.add(new Pair(idx[0], Integer.parseInt(c)));
                }
                ++idx[0];
            });


            File f = Paths.get("route-graph.graphml").toFile();
            var out = new PrintStream(f);
            out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            out.println("<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd\">");
            out.println("<key attr.name=\"r\" attr.type=\"int\" for=\"node\" id=\"r\"/>");
            out.println("<key attr.name=\"g\" attr.type=\"int\" for=\"node\" id=\"g\"/>");
            out.println("<key attr.name=\"b\" attr.type=\"int\" for=\"node\" id=\"b\"/>");
            out.println("<graph id=\"G\" edgedefault=\"directed\">");
            for (int n = 0; n < nodeRouteNr.size(); ++n) {
                out.println("<node id=\"n" + n + "\">");
                out.println("<data key=\"r\">" + (COLOR[nodeRouteNr.get(n)]>>16) + "</data>");
                out.println("<data key=\"g\">" + ((COLOR[nodeRouteNr.get(n)]>>8)&0xff) + "</data>");
                out.println("<data key=\"b\">" + (COLOR[nodeRouteNr.get(n)]&0xff) + "</data>");
                out.println("</node>");
            }
            int e = 0;
            for (var link : links)
                out.println("<edge id=\"e" + (e++) + "\" source=\"n" + link.p + "\" target=\"n" + link.q + "\"/>");
            out.println("</graph>");
            out.println("</graphml>");
            out.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
