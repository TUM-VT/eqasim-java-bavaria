package org.eqasim.bavaria.mode_choice.costs;

import java.util.List;

import org.eqasim.bavaria.mode_choice.utilities.predictors.BavariaPersonPredictor;
import org.eqasim.bavaria.mode_choice.utilities.variables.BavariaPersonVariables;
import org.eqasim.core.simulation.mode_choice.cost.CostModel;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

import com.google.inject.Inject;

public class BavariaDrtCostModel implements CostModel {

    private final String mode;
    private final BavariaPersonPredictor personPredictor;

    @Inject
	protected BavariaDrtCostModel(String mode, BavariaPersonPredictor personPredictor) {
		this.mode = mode;
        this.personPredictor = personPredictor;
	}

    @Override
    public double calculateCost_MU(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
        // values from mija study by city of munich (moia + TUM)

        BavariaPersonVariables personVariables = personPredictor.predictVariables(person, trip, elements);

        double basePrice_h = 3.70; // 2.00 (autonom)
        double pricePerKm_h = 0.70; // 0.45 (autonom)

        double distance_km = getInVehicleDistance_km(elements);

        double cost_EUR = basePrice_h + pricePerKm_h * distance_km;
		if (personVariables.hasSubscription) {
			cost_EUR -= 2.0;    // flat discount for subscription holders
		}

        double taxi_price = 5.5 + 2.5 * distance_km;    // simple taxi price model as maximum cap
        if (cost_EUR > taxi_price) {
            cost_EUR = taxi_price;
        }

        return cost_EUR;
    }

	protected double getInVehicleDistance_km(List<? extends PlanElement> elements) {
		double distance_km = 0.0;

		for (PlanElement element : elements) {
			if (element instanceof Leg) {
				Leg leg = (Leg) element;

				if (leg.getMode().contentEquals(mode)) {
					distance_km += leg.getRoute().getDistance() * 1e-3;
				}
			}
		}

		return distance_km;
	}
}
