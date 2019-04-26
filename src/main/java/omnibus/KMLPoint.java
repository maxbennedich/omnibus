package omnibus;

class KMLPoint {
    String name;
    String type;
    XY xy;

    KMLPoint(String name, String type, XY xy) {
        this.name = name;
        this.type = type;
        this.xy = xy;
    }
}
