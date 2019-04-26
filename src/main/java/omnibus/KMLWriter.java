package omnibus;

import java.io.PrintStream;
import java.util.List;

public class KMLWriter {
    public static void writeJourney(PrintStream out, List<KMLSegment> segments, List<KMLPoint> points) {
        writeHeader(out);
        writeSegments(out, segments, points);
        writeFooter(out);
    }

    private static void writeSegments(PrintStream out, List<KMLSegment> segments, List<KMLPoint> points) {
        for (var segment : segments)
            writeLine(out, segment.name, segment.type, segment.segment);
        for (var point : points)
            writePoint(out, point.name, point.type, point.xy);
    }

    private static void writeHeader(PrintStream out) {
        out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        out.println("<kml xmlns=\"http://www.opengis.net/kml/2.2\">");
        out.println("<Document><name>Omnibus</name>");
        out.println("<description>Omnibus</description>");
        out.println("<Style id=\"Walk\">");
        out.println("<LineStyle><color>FF007FFF</color><width>3</width></LineStyle>");
        out.println("</Style>");
        out.println("<Style id=\"Bus\">");
        out.println("<LineStyle><color>FF0000FF</color><width>3</width></LineStyle>");
        out.println("</Style>");
        out.println();
        out.println("<Style id=\"Stop\">");
        out.println("<IconStyle><Icon><href>");
        out.println("https://sites.google.com/site/highwayskml/kml/icons/redmarker-bordered8.png");
        out.println("</href></Icon></IconStyle>");
        out.println("</Style>");
        out.println();
        out.println("<Style id=\"Change\">");
        out.println("<IconStyle><Icon><href>");
        out.println("https://sites.google.com/site/highwayskml/kml/icons/yellowmarker-bordered8.png");
        out.println("</href></Icon></IconStyle>");
        out.println("</Style>");
        out.println();
    }

    private static void writeFooter(PrintStream out) {
        out.println("</Document>");
        out.println("</kml>");
    }

    private static void writePoint(PrintStream out, String name, String style, XY xy) {
        out.printf("<Placemark><name>%s</name>\n", name);
        out.printf("<description></description>\n");
        out.printf("<styleUrl>#%s</styleUrl>\n", style);
        out.printf("<Point><coordinates>\n");
        out.printf("%.6f,%.6f,0.0\n", xy.x, xy.y);
        out.printf("</coordinates></Point>\n");
        out.printf("</Placemark>\n");
        out.printf("\n");
    }

    private static void writeLine(PrintStream out, String name, String style, List<XY> segment) {
        out.printf("<Placemark><name>%s</name>\n", name);
        out.printf("<description></description>\n");
        out.printf("<styleUrl>#%s</styleUrl>\n", style);
        out.printf("<LineString>\n");
        out.printf("<tessellate>1</tessellate>\n");
        out.printf("<altitudeMode>clampToGround</altitudeMode>\n");
        out.printf("<coordinates>\n");

        for (XY xy : segment)
            out.printf("%.6f,%.6f,0.0\n", xy.x, xy.y);

        out.printf("</coordinates>\n");
        out.printf("</LineString>\n");
        out.printf("</Placemark>\n");
        out.printf("\n");
    }
}