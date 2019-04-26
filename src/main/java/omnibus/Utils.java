package omnibus;

public class Utils {
    public static String formatTime(int time) {
        if (time == Integer.MAX_VALUE)
            return "never";

        int hour = time / 3600;
        int minute = (time - hour * 3600) / 60;
        int second = time - hour * 3600 - minute * 60;
        return String.format("%02d:%02d:%02d", hour, minute, second);
    }

    public static String formatMinSec(int time) {
        if (time < 60)
            return time + " s";

        int min = time / 60;
        return min + " min " + (time - min*60) + " s";
    }
}
