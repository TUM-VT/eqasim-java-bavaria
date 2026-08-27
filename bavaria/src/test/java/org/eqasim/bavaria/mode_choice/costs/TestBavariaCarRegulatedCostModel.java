package org.eqasim.bavaria.mode_choice.costs;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.List;

import org.eqasim.bavaria.mode_choice.parameters.BavariaCostParameters;
import org.eqasim.core.scenario.cutter.extent.NullScenarioExtent;
import org.eqasim.core.scenario.cutter.extent.ScenarioExtent;
import org.eqasim.core.scenario.cutter.extent.ShapeScenarioExtent;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.utils.objectattributes.attributable.AttributesImpl;

public class TestBavariaCarRegulatedCostModel {
	static private final double DELTA = 1e-9;

	static private ScenarioExtent buildSquareExtent() {
		GeometryFactory factory = new GeometryFactory();
		Polygon polygon = factory.createPolygon(new Coordinate[] { //
				new Coordinate(0, 0), new Coordinate(10, 0), new Coordinate(10, 10), new Coordinate(0, 10),
				new Coordinate(0, 0) });
		return new ShapeScenarioExtent(polygon);
	}

	static private DiscreteModeChoiceTrip buildTrip(String destinationType, Coord destinationCoord) {
		Activity origin = PopulationUtils.createActivityFromCoord("home", new Coord(-100.0, -100.0));
		Activity destination = PopulationUtils.createActivityFromCoord(destinationType, destinationCoord);
		return new DiscreteModeChoiceTrip(origin, destination, "car", Collections.emptyList(), 0, 0, 0,
				new AttributesImpl());
	}

	static private List<PlanElement> buildCarLegElements(double distance_km) {
		Leg leg = PopulationUtils.createLeg("car");
		leg.setRoute(RouteUtils.createGenericRouteImpl(Id.create("a", Link.class), Id.create("b", Link.class)));
		leg.getRoute().setDistance(distance_km * 1000.0);
		return List.of(leg);
	}

	@Test
	public void testSurcharge_nonHomeInsideBoundary() {
		BavariaCostParameters costParameters = BavariaCostParameters.buildDefault();
		costParameters.carCost_EUR_km = 0.0;
		costParameters.munichParkingSurcharge_EUR = 5.0;

		BavariaCarRegulatedCostModel costModel = new BavariaCarRegulatedCostModel(costParameters, buildSquareExtent());
		DiscreteModeChoiceTrip trip = buildTrip("work", new Coord(5.0, 5.0));

		assertEquals(5.0, costModel.calculateCost_MU(null, trip, Collections.emptyList()), DELTA);
	}

	@Test
	public void testSurcharge_homeInsideBoundary() {
		BavariaCostParameters costParameters = BavariaCostParameters.buildDefault();
		costParameters.carCost_EUR_km = 0.0;
		costParameters.munichParkingSurcharge_EUR = 5.0;

		BavariaCarRegulatedCostModel costModel = new BavariaCarRegulatedCostModel(costParameters, buildSquareExtent());
		DiscreteModeChoiceTrip trip = buildTrip("home", new Coord(5.0, 5.0));

		assertEquals(0.0, costModel.calculateCost_MU(null, trip, Collections.emptyList()), DELTA);
	}

	@Test
	public void testSurcharge_nonHomeOutsideBoundary() {
		BavariaCostParameters costParameters = BavariaCostParameters.buildDefault();
		costParameters.carCost_EUR_km = 0.0;
		costParameters.munichParkingSurcharge_EUR = 5.0;

		BavariaCarRegulatedCostModel costModel = new BavariaCarRegulatedCostModel(costParameters, buildSquareExtent());
		DiscreteModeChoiceTrip trip = buildTrip("work", new Coord(100.0, 100.0));

		assertEquals(0.0, costModel.calculateCost_MU(null, trip, Collections.emptyList()), DELTA);
	}

	@Test
	public void testSurcharge_offByNullExtent() {
		BavariaCostParameters costParameters = BavariaCostParameters.buildDefault();
		costParameters.carCost_EUR_km = 0.0;
		costParameters.munichParkingSurcharge_EUR = 5.0;

		BavariaCarRegulatedCostModel costModel = new BavariaCarRegulatedCostModel(costParameters,
				new NullScenarioExtent());
		DiscreteModeChoiceTrip trip = buildTrip("work", new Coord(5.0, 5.0));

		assertEquals(0.0, costModel.calculateCost_MU(null, trip, Collections.emptyList()), DELTA);
	}

	@Test
	public void testSurcharge_offByZeroMagnitude() {
		BavariaCostParameters costParameters = BavariaCostParameters.buildDefault();
		costParameters.carCost_EUR_km = 0.0;
		costParameters.munichParkingSurcharge_EUR = 0.0;

		BavariaCarRegulatedCostModel costModel = new BavariaCarRegulatedCostModel(costParameters, buildSquareExtent());
		DiscreteModeChoiceTrip trip = buildTrip("work", new Coord(5.0, 5.0));

		assertEquals(0.0, costModel.calculateCost_MU(null, trip, Collections.emptyList()), DELTA);
	}

	@Test
	public void testBaseCostStillApplied() {
		BavariaCostParameters costParameters = BavariaCostParameters.buildDefault();
		costParameters.carCost_EUR_km = 0.2;
		costParameters.munichParkingSurcharge_EUR = 5.0;

		BavariaCarRegulatedCostModel costModel = new BavariaCarRegulatedCostModel(costParameters, buildSquareExtent());
		DiscreteModeChoiceTrip trip = buildTrip("work", new Coord(5.0, 5.0));

		double cost = costModel.calculateCost_MU(null, trip, buildCarLegElements(10.0));
		assertEquals(0.2 * 10.0 + 5.0, cost, DELTA);
	}
}
