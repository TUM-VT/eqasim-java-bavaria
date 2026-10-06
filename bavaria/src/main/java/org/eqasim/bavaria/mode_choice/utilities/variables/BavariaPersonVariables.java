package org.eqasim.bavaria.mode_choice.utilities.variables;

import org.eqasim.core.simulation.mode_choice.utilities.variables.BaseVariables;

public class BavariaPersonVariables implements BaseVariables {
	public final boolean hasSubscription;
	public final boolean hasDrivingPermit;
	public final boolean isHighIncome;
	public final boolean isMunichResident;
	public final boolean hasCarAvailability;

	public BavariaPersonVariables(boolean hasSubscription, boolean hasDrivingPermit, boolean isHighIncome,
			boolean isMunichResident, boolean hasCarAvailability) {
		this.hasSubscription = hasSubscription;
		this.hasDrivingPermit = hasDrivingPermit;
		this.isHighIncome = isHighIncome;
		this.isMunichResident = isMunichResident;
		this.hasCarAvailability = hasCarAvailability;
	}
}
