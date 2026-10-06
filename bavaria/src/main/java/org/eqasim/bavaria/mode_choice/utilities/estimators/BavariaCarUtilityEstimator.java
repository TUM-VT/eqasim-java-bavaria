package org.eqasim.bavaria.mode_choice.utilities.estimators;

import java.util.List;

import org.eqasim.bavaria.mode_choice.parameters.BavariaModeParameters;
import org.eqasim.bavaria.mode_choice.utilities.predictors.BavariaPersonPredictor;
import org.eqasim.bavaria.mode_choice.utilities.variables.BavariaPersonVariables;
import org.eqasim.core.simulation.mode_choice.utilities.estimators.CarUtilityEstimator;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.CarPredictor;
import org.eqasim.core.simulation.mode_choice.utilities.variables.CarVariables;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.candidates.TripCandidate;

import com.google.inject.Inject;

public class BavariaCarUtilityEstimator extends CarUtilityEstimator {
	private final BavariaModeParameters parameters;
	private final CarPredictor predictor;
	private final BavariaPersonPredictor personPredictor;

	@Inject
	public BavariaCarUtilityEstimator(BavariaModeParameters parameters, CarPredictor predictor, BavariaPersonPredictor personPredictor) {
		super(parameters, predictor);

		this.parameters = parameters;
		this.predictor = predictor;
		this.personPredictor = personPredictor;
	}

	protected double estimateAccessEgressTimeUtility(CarVariables variables) {
		return parameters.betaAccessTime_u_min * variables.accessEgressTime_min;
	}

	protected double estimateTravelTimeUtility(CarVariables variables, BavariaPersonVariables personVariables) {
		double beta = parameters.car.betaTravelTime_u_min;

		if (personVariables.isHighIncome) {
			beta += parameters.bavariaCar.isHighIncome;
		}
		if (personVariables.hasDrivingPermit) {
			beta += parameters.bavariaCar.hasDrivingPermit;
		}
		if (personVariables.hasSubscription) {
			beta += parameters.bavariaCar.hasPtSubscription;
		}

		return beta * variables.travelTime_min;
	}

	@Override
	public double estimateUtility(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements,
			List<TripCandidate> previousTrips) {
		CarVariables variables = predictor.predictVariables(person, trip, elements, previousTrips);
		BavariaPersonVariables personVariables = personPredictor.predictVariables(person, trip, elements);

		double utility = 0.0;

		utility += estimateConstantUtility();
		utility += estimateTravelTimeUtility(variables, personVariables);
		utility += estimateAccessEgressTimeUtility(variables);
		utility += estimateMonetaryCostUtility(variables);

		return utility;
	}
}
