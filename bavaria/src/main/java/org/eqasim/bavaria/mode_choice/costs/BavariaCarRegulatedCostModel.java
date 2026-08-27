package org.eqasim.bavaria.mode_choice.costs;

import java.util.List;

import org.eqasim.bavaria.mode_choice.parameters.BavariaCostParameters;
import org.eqasim.core.scenario.cutter.extent.ScenarioExtent;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Adds a Munich parking surcharge on top of {@link BavariaCarCostModel}: a flat fee
 * for car trips whose destination is not "home" and lies inside the Munich city
 * boundary. Defaults to inert (no shapefile configured, zero-valued surcharge) so
 * this class only has an effect once explicitly enabled via cost parameters and the
 * "car" mode's cost model being pointed at this class in the scenario config.
 */
public class BavariaCarRegulatedCostModel extends BavariaCarCostModel {
	private final BavariaCostParameters costParameters;
	private final ScenarioExtent munichBoundaryExtent;

	@Inject
	public BavariaCarRegulatedCostModel(BavariaCostParameters costParameters,
			@Named("munichBoundaryExtent") ScenarioExtent munichBoundaryExtent) {
		super(costParameters);

		this.costParameters = costParameters;
		this.munichBoundaryExtent = munichBoundaryExtent;
	}

	private double calculateMunichParkingSurcharge_EUR(DiscreteModeChoiceTrip trip) {
		Activity destination = trip.getDestinationActivity();
		Coord destinationCoord = destination.getCoord();

		boolean isNonHome = !destination.getType().equals("home");
		boolean isInsideMunich = destinationCoord != null && munichBoundaryExtent.isInside(destinationCoord);

		return (isNonHome && isInsideMunich) ? costParameters.munichParkingSurcharge_EUR : 0.0;
	}

	@Override
	public double calculateCost_MU(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
		return super.calculateCost_MU(person, trip, elements) + calculateMunichParkingSurcharge_EUR(trip);
	}
}
