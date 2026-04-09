package org.eqasim.bavaria.mode_choice.costs;

import java.util.List;

import org.eqasim.bavaria.mode_choice.utilities.predictors.BavariaPersonPredictor;
import org.eqasim.bavaria.mode_choice.utilities.variables.BavariaPersonVariables;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

import com.google.inject.Inject;

/**
 * Cost model for the DRT leg within a feeder_drt trip.
 *
 * Applies a flat feeder discount for passengers without a PT subscription.
 * Subscription holders already receive the same discount via the base class,
 * so no additional reduction is applied to avoid double-counting.
 */
public class BavariaFeederDrtCostModel extends BavariaDrtCostModel {

    /** Flat reduction applied to feeder DRT legs for non-subscription holders (EUR). */
    private static final double FEEDER_DISCOUNT_EUR = 2.00;

    @Inject
    public BavariaFeederDrtCostModel(BavariaPersonPredictor personPredictor) {
        super(personPredictor);
    }

    @Override
    public double calculateCost_MU(Person person, DiscreteModeChoiceTrip trip,
            List<? extends PlanElement> elements) {
        double cost = super.calculateCost_MU(person, trip, elements);

        BavariaPersonVariables personVariables = personPredictor.predictVariables(person, trip, elements);
        if (!personVariables.hasSubscription) {
            cost = Math.max(0.0, cost - FEEDER_DISCOUNT_EUR);
        }

        return cost;
    }
}
