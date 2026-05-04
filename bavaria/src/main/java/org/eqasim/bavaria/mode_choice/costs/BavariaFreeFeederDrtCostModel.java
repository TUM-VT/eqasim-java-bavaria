package org.eqasim.bavaria.mode_choice.costs;

import java.util.List;

import org.eqasim.core.simulation.mode_choice.cost.CostModel;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

/**
 * Cost model for feeder DRT trips that are included in the PT ticket (free of charge).
 */
public class BavariaFreeFeederDrtCostModel implements CostModel {

    @Override
    public double calculateCost_MU(Person person, DiscreteModeChoiceTrip trip,
            List<? extends PlanElement> elements) {
        return 0.0;
    }
}
