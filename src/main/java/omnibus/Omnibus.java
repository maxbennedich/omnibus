package omnibus;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Best-first search with pruning based on minimum time per stop. Given a fixed start time,
 * and a 100 m stop change radius, it finds the optimal solution with 21 routes for a fixed
 * initial stop in a couple of minutes, and across all initial stops in approximately 40
 * minutes. The pruning strategy can however not be used if the start time is not fixed.
 */
public class Omnibus {
    NetworkData data = new NetworkData();

    /** Minimum connection time between two different routes. Needed to allow for delays. */
    static final int MIN_CONNECTION_TIME_SECONDS = 4 * 60;

    /**
     * Add this amount to the minimum connection time if the connection is from
     * a different stop, in addition to the walking time.
     */
    static final int STOP_CHANGE_TIME_SECONDS = 30;

    static final double WALKING_SPEED_METERS_PER_SECOND = 1.0;

    class QueueState implements Comparable<QueueState> {
        final SegmentWithConnections segment;
        final QueueState parent;
        final int routesUsed;
        final int earliestCompletionTime;
        final int startTime;

        QueueState(SegmentWithConnections segment, QueueState parent, int routesUsed) {
            this.segment = segment;
            this.parent = parent;
            this.routesUsed = routesUsed;
            this.startTime = parent == null ? segment.timeFrom : parent.startTime;
            this.earliestCompletionTime = getEarliestCompletionTime(elapsedTime(), routesUsed);
        }

        int elapsedTime() {
            return segment.timeTo - startTime;
        }

        @Override public int compareTo(QueueState qs) {
            return earliestCompletionTime - qs.earliestCompletionTime;
        }
    }

    class BestTimes {
        /** earliest time for each combination of stop and routes used; used for pruning */
        ExpandableArray[] times; // bits 0-31: routes used; bits 32-36: last route nr; bits 37-63: time

        BestTimes() {
            // earliest time for each combination of stop and routes used; used for pruning
            times = new ExpandableArray[data.nrStops];
            for (int s = 0; s < data.nrStops; ++s)
                times[s] = new ExpandableArray();
        }

        public void add(QueueState state) {
            int lastRouteNr = state.segment.routeNr;
            int stopNr = state.segment.to.nr;
            long bitState = state.routesUsed + ((long)lastRouteNr << 32) + ((long)state.elapsedTime() << 37);
            times[stopNr].add(bitState);
        }
    }

    void printRoutesUsed(int routesUsed) {
        var routes = new ArrayList<Integer>();
        for (int b = 0; b < data.nrRoutes; ++b)
            if ((routesUsed & (1 << b)) != 0)
                routes.add(data.routeNrMapping[b]);
        System.out.printf("%d routes: %s\n", routes.size(), routes.stream().map(r -> r+"").collect(Collectors.joining(", ")));
    }

    int getEarliestCompletionTime(int time, int routesUsed) {
        int remainingNrRoutes = data.nrRoutes - Integer.bitCount(routesUsed);
        return time + MIN_CONNECTION_TIME_SECONDS * remainingNrRoutes;
    }

    List<SegmentWithConnections> getSegments(Stop stop, int initialTime) {
        var segments = new ArrayList<SegmentWithConnections>();
        for (var connections : stop.connectionsByRouteNr.values()) {
            int idx = Collections.binarySearch(connections.stream().map(c -> c.timeFrom).collect(Collectors.toList()), initialTime);
            if (idx < 0)
                idx = -idx-1;
            if (idx < connections.size())
                segments.add(connections.get(idx));
        }
        return segments;
    }

    /** Return true if we've been at the same stop, with the same or more routes used, at a faster time. */
    boolean prune(QueueState state, BestTimes bestTimes) {
        var stop = state.segment.to;
        var bestTimesList = bestTimes.times[stop.nr];

        for (int ind = bestTimesList.size-1; ind >= 0; --ind) {
            long bestTime = bestTimesList.array[ind];
            int routesUsed = (int)bestTime;
            if ((routesUsed | state.routesUsed) != routesUsed)
                continue;

            int pruneTime = (int)(bestTime >> 37);
            int lastRouteNr = (int)((bestTime >> 32) & 0x1f);
            if (lastRouteNr != state.segment.routeNr)
                pruneTime += MIN_CONNECTION_TIME_SECONDS;
            if (pruneTime <= state.elapsedTime())
                return true;
        }

        return false;
    }

    void search(int initialTime, int maxTime) {
        var pq = new PriorityQueue<QueueState>();

        for (var stop : data.stops)
            for (var segment : getSegments(stop, initialTime))
                pq.add(new QueueState(segment, null, 1 << segment.routeNr));
/*
        var matchingStops = data.stops.stream().filter(s -> s.name.equals("Dalagatan")).collect(Collectors.toList());
        for (var segment : getSegments(matchingStops.get(0), initialTime)) {
            System.out.println("got segment " + segment);
            int routesUsed = 1 << segment.routeNr;
            var state = new QueueState(segment, null, routesUsed);
            pq.add(state);
        }
*/

        var bestTimes = new BestTimes();

        int bestRouteCount = 0;
//        int maxTimeSeen = initialTime;

        long t0 = System.nanoTime();
        int statesPruned = 0;
        int statesVisited = 0;

        while (!pq.isEmpty()) {
            var state = pq.remove();

            if (prune(state, bestTimes)) {
                ++statesPruned;
                continue;
            }

            ++statesVisited;
            bestTimes.add(state);

/*            if (state.segment.timeTo > maxTimeSeen) {
                System.out.println(formatTime(state.segment.timeTo) + " / " + formatTime(state.earliestCompletionTime));
                maxTimeSeen = state.segment.timeTo;
            }*/

            int routeCount = Integer.bitCount(state.routesUsed);
            if (routeCount > bestRouteCount) {
                bestRouteCount = routeCount;
                System.out.printf("\n%.2f s; %d states visited; %d (%.2f %%) states pruned; %d states queued\n",
                        (System.nanoTime() - t0)/1e9, statesVisited, statesPruned, 100.0 * statesPruned / (statesVisited + statesPruned), pq.size());
                printRoutesUsed(state.routesUsed);
                if (routeCount >= 10)
                    JourneyExporter.export(data, state);
                if (routeCount >= data.nrRoutes)
                    break;
            }

            for (var segment : state.segment.connections) {
                int nextRoutesUsed = state.routesUsed | (1 << segment.routeNr);
                // don't allow using a previously used route number, except for when staying on the same line for multiple stops (i.e. same departure and arrival time)
                if ((nextRoutesUsed != state.routesUsed || state.segment.timeTo == segment.timeFrom)) {
                    var nextState = new QueueState(segment, state, nextRoutesUsed);
                    if (nextState.earliestCompletionTime < maxTime)
                        pq.add(nextState);
                }
            }
        }
    }

    void go(int initialTime, int maxTime) {
        search(initialTime, maxTime);
    }

    public static void main(String ... args) {
        new Omnibus().go(12 * 3600 + 15 * 60, 4 * 3600);
    }
}