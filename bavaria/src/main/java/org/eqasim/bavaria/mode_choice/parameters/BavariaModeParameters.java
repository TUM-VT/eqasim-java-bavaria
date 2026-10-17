package org.eqasim.bavaria.mode_choice.parameters;

import org.eqasim.core.simulation.mode_choice.parameters.ModeParameters;

/**
 * Mode-choice parameters following Mueller/Engelhardt (EWGT2026), Table 2 -
 * the MNL specification WITHOUT trip purposes, perceived-cost column.
 *
 * All socio-demographic terms below are INTERACTIONS ON TIME (the paper's
 * b_tt_&lt;mode&gt;_&lt;attribute&gt; and b_waiting_&lt;mode&gt;_&lt;attribute&gt;), not standalone
 * constants. Their names say which time they modify and carry the same _u_min
 * unit as the base coefficient they are added to: the estimators sum them into
 * one coefficient and multiply by the respective time once.
 */
public class BavariaModeParameters extends ModeParameters {

	public class BavariaWalkParameters {
		public double betaTravelTime_highIncome_u_min;
		public double betaTravelTime_drivingPermit_u_min;
		public double betaTravelTime_ptSubscription_u_min;
		public double betaTravelTime_munichResident_u_min;
	}

	public class BavariaBicycleParameters {
		public double betaTravelTime_highIncome_u_min;
		public double betaTravelTime_munichResident_u_min;
	}

	public class BavariaCarParameters {
		public double betaTravelTime_highIncome_u_min;
		public double betaTravelTime_drivingPermit_u_min;
		public double betaTravelTime_ptSubscription_u_min;

		// Altstadtring closure measure (BavariaCarRegulatedUtilityEstimator)
		public double altstadtringPenalty_u;

		// Munich-boundary parking-search-time measure (BavariaCarRegulatedUtilityEstimator)
		public double munichParkingSearchPenalty_min;
	}

	/**
	 * Shapefile reference for a zone-membership check (e.g. Altstadtring, Munich
	 * city boundary). A blank {@code shapePath} means "not configured" and
	 * resolves to a {@code NullScenarioExtent} (always outside) at wiring time.
	 */
	public class ZoneShapeParameters {
		public String shapePath = "";
		public String shapeAttribute = "";
		public String shapeValue = "";
	}

	public class BavariaCarPassengerParameters {
		public double alpha_u;
		public double betaInVehicleTravelTime_u_min;
		public double betaInVehicleTravelTime_highIncome_u_min;
	}

	public class BavariaPtParameters {
		public double betaInVehicleTime_highIncome_u_min;

		public double betaWaitingTime_highIncome_u_min;
		public double betaWaitingTime_munichResident_u_min;
		public double betaWaitingTime_ptSubscription_u_min;
		public double betaWaitingTime_carAvailable_u_min;
	}

	public class BavariaDrtParameters {
		public double alpha_u;
		public double betaInVehicleTravelTime_u_min;
		public double betaWaitingTime_u_min;

		public double betaInVehicleTravelTime_highIncome_u_min;
		public double betaInVehicleTravelTime_ptSubscription_u_min;
	}

	public final BavariaWalkParameters bavariaWalk = new BavariaWalkParameters();

	public final BavariaBicycleParameters bavariaBicycle = new BavariaBicycleParameters();

	public final BavariaCarParameters bavariaCar = new BavariaCarParameters();

	public final BavariaCarPassengerParameters bavariaCarPassenger = new BavariaCarPassengerParameters();

	public final BavariaPtParameters bavariaPt = new BavariaPtParameters();

	public final BavariaDrtParameters bavariaDrt = new BavariaDrtParameters();

	// Zones used by BavariaCarRegulatedUtilityEstimator / BavariaCarRegulatedCostModel
	public final ZoneShapeParameters altstadtringZone = new ZoneShapeParameters();
	public final ZoneShapeParameters munichBoundaryZone = new ZoneShapeParameters();


	public double betaAccessTime_u_min;

	public static BavariaModeParameters buildDefault() {
		BavariaModeParameters parameters = new BavariaModeParameters();
		parameters.applyDefaults();
		return parameters;
	}

	/**
	 * Populates this instance with the Bavaria baseline values.
	 * Extracted as an instance method (rather than inlined in {@link #buildDefault()})
	 * so subclasses (e.g. scenario-specific profiles like Sc6PushModeParameters) can
	 * reuse the full baseline and layer their own overrides on top,
	 * without duplicating it.
	 *
	 * Values are from Mueller/Engelhardt (EWGT2026), Table 2, EXCEPT where marked
	 * "calibrated" - those were fitted against the MiD modal split and trip lengths
	 * for the munich_1pct base case. car stays pinned at 0 as the reference
	 * alternative, which is what makes the other ASCs identifiable.
	 *
	 * Calibration result: share_mae 0.956 pp, dist_mae 0.945 km (mvv shares within
	 * 0.08 pp on every mode). The remaining error is zonal - munich pt +3.50 pp,
	 * umland pt -2.24 pp - which four global constants cannot fix; that would need
	 * zone-specific ASCs.
	 */
	protected void applyDefaults() {
		// From Mueller/Engelhardt (EWGT2026), Table 2 - the MNL specification WITHOUT trip purposes, perceived-cost column.
		BavariaModeParameters parameters = this;

		// Access / egress is walking, so it uses the walk travel-time coefficient.
		parameters.betaAccessTime_u_min = -0.0786;

		// Cost
		parameters.betaCost_u_MU = -0.2177;
		parameters.lambdaCostEuclideanDistance = 0.0;
		parameters.referenceEuclideanDistance_km = 4.4;

		// Walk
		parameters.walk.alpha_u = 0.0267; // calibrated, estimated 0.8438
		parameters.walk.betaTravelTime_u_min = -0.0786;
		parameters.bavariaWalk.betaTravelTime_highIncome_u_min = -0.0210;
		parameters.bavariaWalk.betaTravelTime_drivingPermit_u_min = -0.0246;
		parameters.bavariaWalk.betaTravelTime_ptSubscription_u_min = 0.0187;
		parameters.bavariaWalk.betaTravelTime_munichResident_u_min = 0.0144;

		// Bicycle
		parameters.bike.alpha_u = -1.1293; // calibrated, estimated 0.4624
		// Left at the estimate deliberately: a stronger beta improved the median
		// length (3.11 km against a 2 km reference) but -0.11/-0.12 sit 1.3/2.6
		// standard errors out, and the share fit gained nothing.
		parameters.bike.betaTravelTime_u_min = -0.0995;
		parameters.bavariaBicycle.betaTravelTime_highIncome_u_min = -0.0248;
		parameters.bavariaBicycle.betaTravelTime_munichResident_u_min = 0.0176;

		// Car (reference alternative: alpha_u stays 0)
		parameters.car.alpha_u = 0.0;
		parameters.car.betaTravelTime_u_min = -0.1105;
		parameters.bavariaCar.betaTravelTime_highIncome_u_min = -0.0207;
		// Note: car is only available to licence holders (BavariaModeAvailability), so
		// this term applies to every simulated car trip - the effective car travel-time
		// coefficient is -0.1105 + 0.0708 = -0.0397.
		parameters.bavariaCar.betaTravelTime_drivingPermit_u_min = 0.0708;
		parameters.bavariaCar.betaTravelTime_ptSubscription_u_min = -0.0449;
		parameters.bavariaCar.altstadtringPenalty_u = 0.0; // off by default, see BavariaCarRegulatedUtilityEstimator / Sc6PushModeParameters
		parameters.bavariaCar.munichParkingSearchPenalty_min = 0.0; // off by default

		// Car passenger (perceived costs are 0.00 EUR/km, hence no cost term)
		parameters.bavariaCarPassenger.alpha_u = -1.3259; // calibrated, estimated -2.0608
		// Calibrated, estimated -0.0638 (SE 0.0083) - a large departure, ~8 standard
		// errors, but the estimate put the median trip at 15.26 km against a 6 km
		// reference, and -0.13 lands it at 6.08 km.
		parameters.bavariaCarPassenger.betaInVehicleTravelTime_u_min = -0.13;
		parameters.bavariaCarPassenger.betaInVehicleTravelTime_highIncome_u_min = -0.0374;

		// PT
		parameters.pt.alpha_u = 0.8452; // calibrated, estimated -0.1226
		// Calibrated, estimated -0.0126 (SE 0.0040) - ~12 standard errors out. The
		// estimate is an implausible 3.5 EUR/h and put the median trip at 13.47 km
		// against 8 km. Note the residual is zonally split (munich +2.33 km, umland
		// -3.29 km), so a single global value cannot satisfy both.
		parameters.pt.betaInVehicleTime_u_min = -0.06;
		parameters.pt.betaLineSwitch_u = -0.6149;
		parameters.pt.betaWaitingTime_u_min = -0.2054;
		parameters.bavariaPt.betaInVehicleTime_highIncome_u_min = -0.0148;
		parameters.bavariaPt.betaWaitingTime_highIncome_u_min = -0.1137;
		parameters.bavariaPt.betaWaitingTime_munichResident_u_min = 0.0978;
		parameters.bavariaPt.betaWaitingTime_ptSubscription_u_min = 0.2615;
		parameters.bavariaPt.betaWaitingTime_carAvailable_u_min = -0.1660;

		// DRT / AMOD
		parameters.bavariaDrt.alpha_u = -0.7274;
		parameters.bavariaDrt.betaInVehicleTravelTime_u_min = -0.0327;
		parameters.bavariaDrt.betaWaitingTime_u_min = -0.1117;
		parameters.bavariaDrt.betaInVehicleTravelTime_highIncome_u_min = -0.0381;
		parameters.bavariaDrt.betaInVehicleTravelTime_ptSubscription_u_min = 0.0277;
	}
}
