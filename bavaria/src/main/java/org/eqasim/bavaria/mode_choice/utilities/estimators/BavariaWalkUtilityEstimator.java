package org.eqasim.bavaria.mode_choice.utilities.estimators;

import java.util.List;

import org.eqasim.bavaria.mode_choice.parameters.BavariaModeParameters;
import org.eqasim.bavaria.mode_choice.utilities.predictors.BavariaPersonPredictor;
import org.eqasim.bavaria.mode_choice.utilities.variables.BavariaPersonVariables;
import org.eqasim.core.simulation.mode_choice.utilities.UtilityEstimator;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.WalkPredictor;
import org.eqasim.core.simulation.mode_choice.utilities.variables.WalkVariables;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

import com.google.inject.Inject;

public class BavariaWalkUtilityEstimator implements UtilityEstimator {
	private final BavariaModeParameters parameters;
	private final BavariaPersonPredictor personPredictor;
	private final WalkPredictor predictor;

	@Inject
	public BavariaWalkUtilityEstimator(BavariaModeParameters parameters, BavariaPersonPredictor personPredictor,
			WalkPredictor predictor) {
		this.parameters = parameters;
		this.personPredictor = personPredictor;
		this.predictor = predictor;
	}

	protected double estimateConstantUtility() {
		return parameters.walk.alpha_u;
	}

	protected double estimateTravelTimeUtility(WalkVariables variables, BavariaPersonVariables personVariables) {
		double beta = parameters.walk.betaTravelTime_u_min;

		if (personVariables.isHighIncome) {
			beta += parameters.bavariaWalk.betaTravelTime_highIncome_u_min;
		}
		if (personVariables.hasDrivingPermit) {
			beta += parameters.bavariaWalk.betaTravelTime_drivingPermit_u_min;
		}
		if (personVariables.hasSubscription) {
			beta += parameters.bavariaWalk.betaTravelTime_ptSubscription_u_min;
		}
		if (personVariables.isMunichResident) {
			beta += parameters.bavariaWalk.betaTravelTime_munichResident_u_min;
		}

		return beta * variables.travelTime_min;
	}

	@Override
	public double estimateUtility(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
		WalkVariables variables = predictor.predictVariables(person, trip, elements);
		BavariaPersonVariables personVariables = personPredictor.predictVariables(person, trip, elements);

		double utility = 0.0;

		utility += estimateConstantUtility();
		utility += estimateTravelTimeUtility(variables, personVariables);

		return utility;
	}
}
