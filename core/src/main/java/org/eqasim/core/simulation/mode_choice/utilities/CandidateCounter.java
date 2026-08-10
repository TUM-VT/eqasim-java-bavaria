package org.eqasim.core.simulation.mode_choice.utilities;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Temporary diagnostic counters to compare how many drt / feeder_drt candidates are
 * actually routed and estimated per DiscreteModeChoice iteration, and how expensive
 * they are (routed distance/time for drt, actual routing wall-time for feeder_drt).
 *
 * Note: EqasimUtilityEstimator's routing happens inside a *final* method of the
 * external AbstractTripRouterEstimator, before our per-mode UtilityEstimator is
 * called with the already-routed plan elements - so for "drt" we cannot time the
 * routing call itself without risky framework rebinding, and instead track the
 * routed distance/time that BavariaDrtUtilityEstimator already receives. For
 * "feeder_drt" the routing (FeederDrtRoutingModule) is our own code, so we can
 * time it directly.
 */
public class CandidateCounter {
	public static final AtomicInteger DRT_ESTIMATOR_CALLS = new AtomicInteger();
	public static final AtomicInteger FEEDER_DRT_TRIPS = new AtomicInteger();
	public static final AtomicInteger FEEDER_INTERNAL_DRT_SEGMENTS = new AtomicInteger();

	public static final DoubleAdder DRT_TOTAL_TRAVEL_TIME_MIN = new DoubleAdder();
	public static final DoubleAdder DRT_TOTAL_DISTANCE_KM = new DoubleAdder();
	public static final AtomicLong FEEDER_DRT_ROUTING_NANOS = new AtomicLong();

	public record Snapshot(int drtCalls, int feederDrtTrips, int feederInternalDrtSegments,
			double drtTotalTravelTimeMin, double drtTotalDistanceKm, long feederDrtRoutingNanos) {
	}

	public static Snapshot snapshotAndReset() {
		return new Snapshot(
				DRT_ESTIMATOR_CALLS.getAndSet(0),
				FEEDER_DRT_TRIPS.getAndSet(0),
				FEEDER_INTERNAL_DRT_SEGMENTS.getAndSet(0),
				DRT_TOTAL_TRAVEL_TIME_MIN.sumThenReset(),
				DRT_TOTAL_DISTANCE_KM.sumThenReset(),
				FEEDER_DRT_ROUTING_NANOS.getAndSet(0));
	}
}
