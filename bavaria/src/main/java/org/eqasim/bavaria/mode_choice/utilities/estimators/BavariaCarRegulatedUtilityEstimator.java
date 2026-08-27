package org.eqasim.bavaria.mode_choice.utilities.estimators;

import java.util.List;

import org.eqasim.bavaria.mode_choice.parameters.BavariaModeParameters;
import org.eqasim.bavaria.mode_choice.utilities.predictors.BavariaPersonPredictor;
import org.eqasim.core.scenario.cutter.extent.ScenarioExtent;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.CarPredictor;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.candidates.TripCandidate;

import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Adds Munich car-regulation measures on top of {@link BavariaCarUtilityEstimator}:
 * a soft ban (large utility penalty) for car trips touching the Altstadtring, and a
 * parking-search-time penalty for car trips into the Munich city boundary. Both
 * measures default to inert (no shapefile configured, zero-valued constants) so this
 * class only has an effect once explicitly enabled via mode-choice parameters and the
 * "car" mode's estimator being pointed at this class in the scenario config.
 */
public class BavariaCarRegulatedUtilityEstimator extends BavariaCarUtilityEstimator {
	private final BavariaModeParameters parameters;
	private final ScenarioExtent altstadtringExtent;
	private final ScenarioExtent munichBoundaryExtent;

	@Inject
	public BavariaCarRegulatedUtilityEstimator(BavariaModeParameters parameters, CarPredictor predictor,
			BavariaPersonPredictor personPredictor, @Named("altstadtringExtent") ScenarioExtent altstadtringExtent,
			@Named("munichBoundaryExtent") ScenarioExtent munichBoundaryExtent) {
		super(parameters, predictor, personPredictor);

		this.parameters = parameters;
		this.altstadtringExtent = altstadtringExtent;
		this.munichBoundaryExtent = munichBoundaryExtent;
	}

	protected double estimateAltstadtringPenaltyUtility(DiscreteModeChoiceTrip trip) {
		Coord originCoord = trip.getOriginActivity().getCoord();
		Coord destinationCoord = trip.getDestinationActivity().getCoord();

		boolean touchesAltstadtring = (originCoord != null && altstadtringExtent.isInside(originCoord))
				|| (destinationCoord != null && altstadtringExtent.isInside(destinationCoord));

		return touchesAltstadtring ? parameters.bavariaCar.altstadtringPenalty_u : 0.0;
	}

	protected double estimateMunichParkingSearchPenaltyUtility(DiscreteModeChoiceTrip trip) {
		Coord destinationCoord = trip.getDestinationActivity().getCoord();

		if (destinationCoord != null && munichBoundaryExtent.isInside(destinationCoord)) {
			return parameters.car.betaTravelTime_u_min * parameters.bavariaCar.munichParkingSearchPenalty_min;
		}

		return 0.0;
	}

	@Override
	public double estimateUtility(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements,
			List<TripCandidate> previousTrips) {
		double utility = super.estimateUtility(person, trip, elements, previousTrips);

		utility += estimateAltstadtringPenaltyUtility(trip);
		utility += estimateMunichParkingSearchPenaltyUtility(trip);

		return utility;
	}
}
