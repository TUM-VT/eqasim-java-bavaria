package org.eqasim.bavaria.mode_choice.parameters;

/**
 * Mode-choice parameters for the "sc6_autonomous_push" scenario: reuses the full
 * calibrated Bavaria baseline (via {@link #applyDefaults()}) and additionally turns
 * on the Altstadtring closure and Munich parking-search-time measures
 * (BavariaCarRegulatedUtilityEstimator).
 *
 * Shapefile paths are given as bare filenames, resolved relative to the scenario's
 * config file directory (same convention as MATSim's own inputNetworkFile /
 * drtServiceAreaShapeFile) - so they only need to sit next to the config.xml, no
 * absolute, machine-specific path required.
 */
public class Sc6PushModeParameters extends BavariaModeParameters {
	public static Sc6PushModeParameters buildDefault() {
		Sc6PushModeParameters parameters = new Sc6PushModeParameters();
		parameters.applyDefaults();

		parameters.bavariaCar.altstadtringPenalty_u = -10.0; // soft car ban in the Altstadtring
		parameters.bavariaCar.munichParkingSearchPenalty_min = 5.0; // +5min parking search in Munich

		parameters.altstadtringZone.shapePath = "Altstadtring_innen.shp";
		parameters.munichBoundaryZone.shapePath = "munich_city_exterior_only_100m.shp";

		return parameters;
	}
}
