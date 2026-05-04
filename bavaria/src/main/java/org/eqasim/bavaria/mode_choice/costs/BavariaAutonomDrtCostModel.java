package org.eqasim.bavaria.mode_choice.costs;

import org.eqasim.bavaria.mode_choice.utilities.predictors.BavariaPersonPredictor;

import com.google.inject.Inject;

public class BavariaAutonomDrtCostModel extends BavariaDrtCostModel {

    @Inject
    public BavariaAutonomDrtCostModel(BavariaPersonPredictor personPredictor) {
        super(personPredictor);
    }

    @Override
    protected double getBasePrice() {
        return 2.00;
    }

    @Override
    protected double getPricePerKm() {
        return 0.45;
    }
}
