package omnibus;

import java.util.List;

class KMLSegment {
    String name;
    String type;
    List<XY> segment;

    KMLSegment(String name, String type, List<XY> segment) {
        this.name = name;
        this.type = type;
        this.segment = segment;
    }
}
