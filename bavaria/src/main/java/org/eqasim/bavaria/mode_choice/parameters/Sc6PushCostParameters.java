package org.eqasim.bavaria.mode_choice.parameters;

/**
 * Cost parameters for the "sc6_autonomous_push" scenario: same as
 * {@link BavariaCostParameters}, but with the Munich parking surcharge
 * (BavariaCarRegulatedCostModel) turned on.
 */
public class Sc6PushCostParameters extends BavariaCostParameters {
	public static Sc6PushCostParameters buildDefault() {
		Sc6PushCostParameters parameters = new Sc6PushCostParameters();

		parameters.carCost_EUR_km = 0.2;
		parameters.munichParkingSurcharge_EUR = 5.0; // sc6_autonomous_push: +5EUR parking surcharge in Munich

		return parameters;
	}
}
