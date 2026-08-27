package org.eqasim.bavaria.mode_choice.parameters;

import org.eqasim.core.simulation.mode_choice.ParameterDefinition;

public class BavariaCostParameters implements ParameterDefinition {
	public double carCost_EUR_km = 0.0;

	// Munich-boundary parking surcharge measure (BavariaCarRegulatedCostModel), off by default
	public double munichParkingSurcharge_EUR = 0.0;

	public static BavariaCostParameters buildDefault() {
		BavariaCostParameters parameters = new BavariaCostParameters();

		parameters.carCost_EUR_km = 0.2;
		parameters.munichParkingSurcharge_EUR = 0.0;

		return parameters;
	}
}
