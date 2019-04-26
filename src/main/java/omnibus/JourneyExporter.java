package omnibus;

import uk.me.jstott.jcoord.LatLng;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class JourneyExporter {
    static ArrayList<Omnibus.QueueState> getJourney(Omnibus.QueueState state) {
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
}
