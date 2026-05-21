package org.eqasim.bavaria.mode_choice.utilities.estimators;

import java.util.List;

import org.eqasim.bavaria.mode_choice.parameters.BavariaModeParameters;
import org.eqasim.bavaria.mode_choice.utilities.predictors.BavariaPersonPredictor;
import org.eqasim.bavaria.mode_choice.utilities.variables.BavariaPersonVariables;
import org.eqasim.core.simulation.mode_choice.cost.CostModel;
import org.eqasim.core.simulation.mode_choice.utilities.UtilityEstimator;
import org.eqasim.core.simulation.mode_choice.utilities.estimators.EstimatorUtils;
import org.eqasim.core.simulation.modes.drt.mode_choice.predictors.DrtPredictor;
import org.eqasim.core.simulation.modes.drt.mode_choice.variables.DrtVariables;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public class BavariaDrtUtilityEstimator implements UtilityEstimator {
	private final BavariaModeParameters parameters;
	private final BavariaPersonPredictor personPredictor;
	private final DrtPredictor drtPredictor;
	private final CostModel costModel;

	@Inject
	public BavariaDrtUtilityEstimator(BavariaModeParameters parameters, DrtPredictor drtPredictor,
			BavariaPersonPredictor personPredictor, @Named("drt") CostModel costModel) {
		this.personPredictor = personPredictor;
		this.drtPredictor = drtPredictor;
		this.parameters = parameters;
		this.costModel = costModel;
	}

    protected double estimateConstantUtility() {
		return this.parameters.bavariaDrt.alpha_u;
    }

    protected double estimateTravelTimeUtility(DrtVariables variables) {
		return this.parameters.bavariaDrt.betaInVehicleTravelTime_u_min * variables.travelTime_min;
    }

    protected double estimateWaitingTimeUtility(DrtVariables variables) {
        return this.parameters.bavariaDrt.betaWaitingTime_u_min * variables.waitingTime_min;
    }

	protected double estimateHighIncomeUtility(BavariaPersonVariables variables) {
		return variables.isHighIncome ? parameters.bavariaPt.isHighIncome : 0.0;
	}	

	protected double estimateWorkPurposeUtility(DiscreteModeChoiceTrip trip) {
		return trip.getDestinationActivity().getType().equals("work") ? parameters.bavariaDrt.isWorkTrip : 0.0;
	}

    protected double estimateMonetaryCostUtility(DrtVariables variables, double cost_MU) {
        return this.parameters.betaCost_u_MU * EstimatorUtils.interaction(variables.euclideanDistance_km,
                this.parameters.referenceEuclideanDistance_km, this.parameters.lambdaCostEuclideanDistance) * cost_MU;
    }

    protected double estimateAccessEgressTimeUtility(DrtVariables variables) {
        return this.parameters.walk.betaTravelTime_u_min * variables.accessEgressTime_min;
    }

    @Override
    public double estimateUtility(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
        DrtVariables variables = this.drtPredictor.predictVariables(person, trip, elements);

        double utility = 0.0;
        double cost_MU = costModel.calculateCost_MU(person, trip, elements);

        utility += estimateConstantUtility();
        utility += estimateTravelTimeUtility(variables);
        utility += estimateWaitingTimeUtility(variables);
        utility += estimateAccessEgressTimeUtility(variables);
        utility += estimateHighIncomeUtility(personPredictor.predictVariables(person, trip, elements));
        utility += estimateWorkPurposeUtility(trip);
        utility += estimateMonetaryCostUtility(variables, cost_MU);
        return utility;
    }
}
