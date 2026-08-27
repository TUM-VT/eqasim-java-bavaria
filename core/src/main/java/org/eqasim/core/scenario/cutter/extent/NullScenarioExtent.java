package org.eqasim.core.scenario.cutter.extent;

import java.util.Collections;
import java.util.List;

import org.matsim.api.core.v01.Coord;

/**
 * Placeholder extent representing "no shapefile configured". Always reports
 * "not inside", so consumers can inject a {@link ScenarioExtent} unconditionally
 * without null-checking whether a zone was actually configured.
 */
public class NullScenarioExtent implements ScenarioExtent {
	@Override
	public boolean isInside(Coord coord) {
		return false;
	}

	@Override
	public List<Coord> computeEuclideanIntersections(Coord from, Coord to) {
		return Collections.emptyList();
	}

	@Override
	public Coord getInteriorPoint() {
		throw new UnsupportedOperationException("NullScenarioExtent has no interior point");
	}
}
