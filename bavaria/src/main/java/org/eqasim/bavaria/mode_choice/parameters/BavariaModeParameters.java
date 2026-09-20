package org.eqasim.bavaria.mode_choice.parameters;

import org.eqasim.core.simulation.mode_choice.parameters.ModeParameters;

public class BavariaModeParameters extends ModeParameters {

	public class BavariaWalkParameters {
		public double isHighIncome;
		public double hasDrivingPermit;
		public double hasPtSubscription;
		public double isMunichResident;
	}

	public class BavariaBicycleParameters {
		public double isHighIncome;
	}

	public class BavariaCarParameters {
		public double isHighIncome;
		public double hasPtSubscription;
		public double isWorkTrip;
		public double isShoppingTrip;

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
		public double betaDrivingPermit_u;
		public double isHighIncome;
		public double isWorkTrip;
		public double isShoppingTrip;
	}

	public class BavariaPtParameters {
		public double betaDrivingPermit_u;
		public double onlyBus_u;
		public double isHighIncome;
		public double waitingTimeHighIncome;
		public double waitingTimeMunichResident;
		public double waitingTimeSubscription;
		public double waitingTimeShopping;
		public double isWorkTrip;
		public double isShoppingTrip;
	}

	public class BavariaDrtParameters {
		public double isHighIncome;
		public double isWorkTrip;
		public double alpha_u;
		public double betaInVehicleTravelTime_u_min;
		public double betaWaitingTime_u_min;
		public double waitingTimeDrtPtPass;

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
	 * Populates this instance with the calibrated Bavaria baseline values.
	 * Extracted as an instance method (rather than inlined in {@link #buildDefault()})
	 * so subclasses (e.g. scenario-specific profiles like Sc6PushModeParameters) can
	 * reuse the full baseline calibration and layer their own overrides on top,
	 * without duplicating it.
	 */
	protected void applyDefaults() {
		BavariaModeParameters parameters = this;

		// Access
		// not specifically estimated for Bavaria, using values from walk.betaTravelTime_u_min
		parameters.betaAccessTime_u_min = -0.0686; // IdF -0.031239; todo

		// Cost
		parameters.betaCost_u_MU = -0.1215; // IdF  -0.310998;
		parameters.lambdaCostEuclideanDistance = 0.0; // IdF -0.257501;
		parameters.referenceEuclideanDistance_km = 4.4;

		

		// Walk
		parameters.walk.alpha_u = -0.1; // uncalibrated 0.774228551231712; IdF 1.685152;
		parameters.walk.betaTravelTime_u_min = -0.0686; // IdF -0.162285;
		parameters.bavariaWalk.isHighIncome = -0.0216;
		parameters.bavariaWalk.hasDrivingPermit = -0.0318;
		parameters.bavariaWalk.hasPtSubscription = 0.0175;
		parameters.bavariaWalk.isMunichResident = 0.0105;


		// Bicycle
		parameters.bike.alpha_u = -1.1576; // -1.2; // uncalibrated 0.318089528187347; IdF -2.927596;
		parameters.bike.betaTravelTime_u_min = -0.1014; // -0.0845; // IdF -0.093485;
		parameters.bavariaBicycle.isHighIncome = -0.0253;

		
		// Car
		parameters.car.alpha_u = 0.0; // IdF -0.201465;
		parameters.car.betaTravelTime_u_min = -0.0658; // -0.0822; // IdF -0.042431;
		parameters.bavariaCar.isHighIncome = -0.0224;
		parameters.bavariaCar.hasPtSubscription = -0.0544;
		parameters.bavariaCar.isWorkTrip = 0.0438;
		parameters.bavariaCar.isShoppingTrip = 0.0678;
		parameters.bavariaCar.altstadtringPenalty_u = 0.0; // off by default, see BavariaCarRegulatedUtilityEstimator / Sc6PushModeParameters
		parameters.bavariaCar.munichParkingSearchPenalty_min = 0.0; // off by default

		// Car passenger
		parameters.bavariaCarPassenger.alpha_u = -1.6491; // -1.75; // uncalibrated -2.22497369171908; IdF -1.713201;
		parameters.bavariaCarPassenger.betaDrivingPermit_u = 0.0; // IdF -0.835542;
		parameters.bavariaCarPassenger.betaInVehicleTravelTime_u_min = -0.1000; // -0.0761; // uncalibrated -0.065198856305705; IdF -0.069976;
		parameters.bavariaCarPassenger.isHighIncome = -0.0404;
		parameters.bavariaCarPassenger.isWorkTrip = 0.0240;
		parameters.bavariaCarPassenger.isShoppingTrip = 0.0500;

		
		// PT
		parameters.pt.alpha_u = -0.1553; // -0.36; // uncalibrated -0.284650026405347; IdF 0.0;
		parameters.pt.betaLineSwitch_u = -0.6016; // IdF -0.417658;
		parameters.pt.betaInVehicleTime_u_min = -0.0379; // uncalibrated -0.0450421005231951; -IdF 0.025501;
		parameters.pt.betaWaitingTime_u_min = -0.3439; // IdF -0.021801;

		parameters.bavariaPt.betaDrivingPermit_u = 0.0; // IdF -0.531426;
		parameters.bavariaPt.onlyBus_u = 0.0; // IdF -1.416309;
		parameters.bavariaPt.isHighIncome = -0.0183;
		parameters.bavariaPt.isWorkTrip = 0.0419;
		parameters.bavariaPt.isShoppingTrip = 0.0456;
		parameters.bavariaPt.waitingTimeShopping = -0.1175;
		parameters.bavariaPt.waitingTimeHighIncome = -0.1483;
		parameters.bavariaPt.waitingTimeMunichResident = 0.0979;
		parameters.bavariaPt.waitingTimeSubscription = 0.3526;

		// DRT
		parameters.bavariaDrt.alpha_u = -0.3662; // -0.5969; 
		parameters.bavariaDrt.betaInVehicleTravelTime_u_min = -0.0287; 
		parameters.bavariaDrt.betaWaitingTime_u_min = -0.2749;
		parameters.bavariaDrt.waitingTimeDrtPtPass = 0.1942;
		parameters.bavariaDrt.isHighIncome = -0.0408;
		parameters.bavariaDrt.isWorkTrip = 0.0178;
	}
}
