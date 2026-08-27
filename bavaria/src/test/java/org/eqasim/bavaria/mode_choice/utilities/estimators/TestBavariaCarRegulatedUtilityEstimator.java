package org.eqasim.bavaria.mode_choice.utilities.estimators;

import static org.junit.Assert.assertEquals;

import java.util.Collections;

import org.eqasim.bavaria.mode_choice.parameters.BavariaModeParameters;
import org.eqasim.core.scenario.cutter.extent.NullScenarioExtent;
import org.eqasim.core.scenario.cutter.extent.ScenarioExtent;
import org.eqasim.core.scenario.cutter.extent.ShapeScenarioExtent;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.core.population.PopulationUtils;
import org.matsim.utils.objectattributes.attributable.AttributesImpl;

public class TestBavariaCarRegulatedUtilityEstimator {
	static private final double DELTA = 1e-9;

	static private ScenarioExtent buildSquareExtent() {
		GeometryFactory factory = new GeometryFactory();
		Polygon polygon = factory.createPolygon(new Coordinate[] { //
				new Coordinate(0, 0), new Coordinate(10, 0), new Coordinate(10, 10), new Coordinate(0, 10),
				new Coordinate(0, 0) });
		return new ShapeScenarioExtent(polygon);
	}

	static private DiscreteModeChoiceTrip buildTrip(Coord originCoord, Coord destinationCoord) {
		Activity origin = PopulationUtils.createActivityFromCoord("home", originCoord);
		Activity destination = PopulationUtils.createActivityFromCoord("work", destinationCoord);
		return new DiscreteModeChoiceTrip(origin, destination, "car", Collections.emptyList(), 0, 0, 0,
				new AttributesImpl());
	}

	static private BavariaCarRegulatedUtilityEstimator buildEstimator(BavariaModeParameters parameters,
			ScenarioExtent altstadtringExtent, ScenarioExtent munichBoundaryExtent) {
		return new BavariaCarRegulatedUtilityEstimator(parameters, null, null, altstadtringExtent,
				munichBoundaryExtent);
	}

	@Test
	public void testAltstadtringPenalty_originInside() {
		BavariaModeParameters parameters = BavariaModeParameters.buildDefault();
		parameters.bavariaCar.altstadtringPenalty_u = -10.0;

		BavariaCarRegulatedUtilityEstimator estimator = buildEstimator(parameters, buildSquareExtent(),
				new NullScenarioExtent());

		DiscreteModeChoiceTrip trip = buildTrip(new Coord(5.0, 5.0), new Coord(100.0, 100.0));
		assertEquals(-10.0, estimator.estimateAltstadtringPenaltyUtility(trip), DELTA);
	}

	@Test
	public void testAltstadtringPenalty_destinationInside() {
		BavariaModeParameters parameters = BavariaModeParameters.buildDefault();
		parameters.bavariaCar.altstadtringPenalty_u = -10.0;

		BavariaCarRegulatedUtilityEstimator estimator = buildEstimator(parameters, buildSquareExtent(),
				new NullScenarioExtent());

		DiscreteModeChoiceTrip trip = buildTrip(new Coord(100.0, 100.0), new Coord(5.0, 5.0));
		assertEquals(-10.0, estimator.estimateAltstadtringPenaltyUtility(trip), DELTA);
	}

	@Test
	public void testAltstadtringPenalty_bothOutside() {
		BavariaModeParameters parameters = BavariaModeParameters.buildDefault();
		parameters.bavariaCar.altstadtringPenalty_u = -10.0;

		BavariaCarRegulatedUtilityEstimator estimator = buildEstimator(parameters, buildSquareExtent(),
				new NullScenarioExtent());

		DiscreteModeChoiceTrip trip = buildTrip(new Coord(100.0, 100.0), new Coord(200.0, 200.0));
		assertEquals(0.0, estimator.estimateAltstadtringPenaltyUtility(trip), DELTA);
	}

	@Test
	public void testAltstadtringPenalty_offByNullExtent() {
		BavariaModeParameters parameters = BavariaModeParameters.buildDefault();
		parameters.bavariaCar.altstadtringPenalty_u = -10.0;

		BavariaCarRegulatedUtilityEstimator estimator = buildEstimator(parameters, new NullScenarioExtent(),
				new NullScenarioExtent());

		DiscreteModeChoiceTrip trip = buildTrip(new Coord(5.0, 5.0), new Coord(5.0, 5.0));
		assertEquals(0.0, estimator.estimateAltstadtringPenaltyUtility(trip), DELTA);
	}

	@Test
	public void testAltstadtringPenalty_offByZeroMagnitude() {
		BavariaModeParameters parameters = BavariaModeParameters.buildDefault();
		parameters.bavariaCar.altstadtringPenalty_u = 0.0;

		BavariaCarRegulatedUtilityEstimator estimator = buildEstimator(parameters, buildSquareExtent(),
				new NullScenarioExtent());

		DiscreteModeChoiceTrip trip = buildTrip(new Coord(5.0, 5.0), new Coord(5.0, 5.0));
		assertEquals(0.0, estimator.estimateAltstadtringPenaltyUtility(trip), DELTA);
	}

	@Test
	public void testMunichParkingSearchPenalty_destinationInside() {
		BavariaModeParameters parameters = BavariaModeParameters.buildDefault();
		parameters.bavariaCar.munichParkingSearchPenalty_min = 5.0;

		BavariaCarRegulatedUtilityEstimator estimator = buildEstimator(parameters, new NullScenarioExtent(),
				buildSquareExtent());

		DiscreteModeChoiceTrip trip = buildTrip(new Coord(100.0, 100.0), new Coord(5.0, 5.0));

		double expected = parameters.car.betaTravelTime_u_min * 5.0;
		assertEquals(expected, estimator.estimateMunichParkingSearchPenaltyUtility(trip), DELTA);
	}

	@Test
	public void testMunichParkingSearchPenalty_destinationOutside() {
		BavariaModeParameters parameters = BavariaModeParameters.buildDefault();
		parameters.bavariaCar.munichParkingSearchPenalty_min = 5.0;

		BavariaCarRegulatedUtilityEstimator estimator = buildEstimator(parameters, new NullScenarioExtent(),
				buildSquareExtent());

		DiscreteModeChoiceTrip trip = buildTrip(new Coord(5.0, 5.0), new Coord(100.0, 100.0));
		assertEquals(0.0, estimator.estimateMunichParkingSearchPenaltyUtility(trip), DELTA);
	}

	@Test
	public void testMunichParkingSearchPenalty_offByNullExtent() {
		BavariaModeParameters parameters = BavariaModeParameters.buildDefault();
		parameters.bavariaCar.munichParkingSearchPenalty_min = 5.0;

		BavariaCarRegulatedUtilityEstimator estimator = buildEstimator(parameters, new NullScenarioExtent(),
				new NullScenarioExtent());

		DiscreteModeChoiceTrip trip = buildTrip(new Coord(5.0, 5.0), new Coord(5.0, 5.0));
		assertEquals(0.0, estimator.estimateMunichParkingSearchPenaltyUtility(trip), DELTA);
	}
}
