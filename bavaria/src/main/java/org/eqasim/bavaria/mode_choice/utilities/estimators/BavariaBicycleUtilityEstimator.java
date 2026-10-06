package org.eqasim.bavaria.mode_choice.utilities.estimators;

import java.util.List;

import org.eqasim.bavaria.mode_choice.parameters.BavariaModeParameters;
import org.eqasim.bavaria.mode_choice.utilities.predictors.BavariaPersonPredictor;
import org.eqasim.bavaria.mode_choice.utilities.variables.BavariaPersonVariables;
import org.eqasim.core.simulation.mode_choice.utilities.UtilityEstimator;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.BikePredictor;
import org.eqasim.core.simulation.mode_choice.utilities.variables.BikeVariables;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

import com.google.inject.Inject;

public class BavariaBicycleUtilityEstimator implements UtilityEstimator {
	private final BavariaModeParameters parameters;
	private final BavariaPersonPredictor personPredictor;
	private final BikePredictor predictor;

	@Inject
	public BavariaBicycleUtilityEstimator(BavariaModeParameters parameters, BavariaPersonPredictor personPredictor,
			BikePredictor predictor) {
		this.parameters = parameters;
		this.personPredictor = personPredictor;
		this.predictor = predictor;
	}

	protected double estimateConstantUtility() {
		return parameters.bike.alpha_u;
	}

	protected double estimateTravelTimeUtility(BikeVariables variables, BavariaPersonVariables personVariables) {
		double beta = parameters.bike.betaTravelTime_u_min;

		if (personVariables.isHighIncome) {
			beta += parameters.bavariaBicycle.betaTravelTime_highIncome_u_min;
		}
		if (personVariables.isMunichResident) {
			beta += parameters.bavariaBicycle.betaTravelTime_munichResident_u_min;
		}

		return beta * variables.travelTime_min;
	}

	@Override
	public double estimateUtility(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
		BikeVariables variables = predictor.predictVariables(person, trip, elements);
		BavariaPersonVariables personVariables = personPredictor.predictVariables(person, trip, elements);

		double utility = 0.0;

		utility += estimateConstantUtility();
		utility += estimateTravelTimeUtility(variables, personVariables);

		return utility;
	}
}
