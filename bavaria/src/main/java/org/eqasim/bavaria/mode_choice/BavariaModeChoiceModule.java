package org.eqasim.bavaria.mode_choice;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.eqasim.bavaria.mode_choice.constraints.FeederDrtServiceAreaConstraint;
import org.eqasim.bavaria.mode_choice.costs.BavariaAutonomDrtCostModel;
import org.eqasim.bavaria.mode_choice.costs.BavariaCarCostModel;
import org.eqasim.bavaria.mode_choice.costs.BavariaFeederDrtCostModel;
import org.eqasim.bavaria.mode_choice.costs.BavariaFreeFeederDrtCostModel;
import org.eqasim.bavaria.mode_choice.costs.BavariaDrtCostModel;
import org.eqasim.bavaria.mode_choice.costs.BavariaPtCostModel;
import org.eqasim.bavaria.mode_choice.parameters.BavariaCostParameters;
import org.eqasim.bavaria.mode_choice.parameters.BavariaModeParameters;
import org.eqasim.bavaria.mode_choice.utilities.estimators.BavariaBicycleUtilityEstimator;
import org.eqasim.bavaria.mode_choice.utilities.estimators.BavariaCarPassengerUtilityEstimator;
import org.eqasim.bavaria.mode_choice.utilities.estimators.BavariaCarUtilityEstimator;
import org.eqasim.bavaria.mode_choice.utilities.estimators.BavariaDrtUtilityEstimator;
import org.eqasim.bavaria.mode_choice.utilities.estimators.BavariaPtUtilityEstimator;
import org.eqasim.bavaria.mode_choice.utilities.estimators.BavariaWalkUtilityEstimator;
import org.eqasim.bavaria.mode_choice.utilities.predictors.BavariaCarPassengerPredictor;
import org.eqasim.bavaria.mode_choice.utilities.predictors.BavariaPersonPredictor;
import org.eqasim.bavaria.mode_choice.utilities.predictors.BavariaPtPredictor;
import org.eqasim.core.components.config.EqasimConfigGroup;
import org.eqasim.core.simulation.mode_choice.AbstractEqasimExtension;
import org.eqasim.core.simulation.mode_choice.ParameterDefinition;
import org.eqasim.core.simulation.mode_choice.cost.CostModel;
import org.eqasim.core.simulation.mode_choice.parameters.ModeParameters;
import org.eqasim.core.simulation.mode_choice.tour_finder.ActivityTourFinderWithExcludedActivities;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contribs.discrete_mode_choice.components.tour_finder.ActivityTourFinder;
import org.matsim.contribs.discrete_mode_choice.modules.config.ActivityTourFinderConfigGroup;
import org.matsim.contribs.discrete_mode_choice.modules.config.DiscreteModeChoiceConfigGroup;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.CommandLine.ConfigurationException;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;

import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

public class BavariaModeChoiceModule extends AbstractEqasimExtension {
	private final CommandLine commandLine;

	public static final String MODE_AVAILABILITY_NAME = "BavariaModeAvailability";

	public static final String CAR_COST_MODEL_NAME = "BavariaCarCostModel";
	public static final String PT_COST_MODEL_NAME = "MunichPtCostModel";
	public static final String DRT_COST_MODEL_NAME = "BavariaDrtCostModel";
	public static final String FEEDER_DRT_COST_MODEL_NAME = "BavariaFeederDrtCostModel";
	public static final String AUTONOM_DRT_COST_MODEL_NAME = "BavariaAutonomDrtCostModel";
	public static final String FREE_FEEDER_DRT_COST_MODEL_NAME = "BavariaFreeFeederDrtCostModel";

	public static final String CAR_ESTIMATOR_NAME = "BavariaCarUtilityEstimator";
	public static final String CAR_PASSENGER_ESTIMATOR_NAME = "BavariaCarPassengerUtilityEstimator";
	public static final String BICYCLE_ESTIMATOR_NAME = "BavariaBicycleUtilityEstimator";
	public static final String PT_ESTIMATOR_NAME = "BavariaPtUtilityEstimator";
	public static final String WALK_ESTIMATOR_NAME = "BavariaWalkUtilityEstimator";
	public static final String DRT_ESTIMATOR_NAME = "BavariaDrtUtilityEstimator";

	static public final String CAR_PASSENGER = "car_passenger";
	static public final String BICYCLE = "bicycle";

	public static final String ISOLATED_OUTSIDE_TOUR_FINDER_NAME = "IsolatedOutsideTrips";

	public BavariaModeChoiceModule(CommandLine commandLine) {
		this.commandLine = commandLine;
	}

	@Override
	protected void installEqasimExtension() {
		bindModeAvailability(MODE_AVAILABILITY_NAME).to(BavariaModeAvailability.class);

		bind(BavariaPersonPredictor.class);
		bind(BavariaCarPassengerPredictor.class);
		bind(BavariaPtPredictor.class);

		bindCostModel(CAR_COST_MODEL_NAME).to(BavariaCarCostModel.class);
		bindCostModel(PT_COST_MODEL_NAME).to(BavariaPtCostModel.class);
		bindCostModel(DRT_COST_MODEL_NAME).to(BavariaDrtCostModel.class);
		bindCostModel(FEEDER_DRT_COST_MODEL_NAME).to(BavariaFeederDrtCostModel.class);
		bindCostModel(AUTONOM_DRT_COST_MODEL_NAME).to(BavariaAutonomDrtCostModel.class);
		bindCostModel(FREE_FEEDER_DRT_COST_MODEL_NAME).to(BavariaFreeFeederDrtCostModel.class);

		bindUtilityEstimator(CAR_ESTIMATOR_NAME).to(BavariaCarUtilityEstimator.class);
		bindUtilityEstimator(BICYCLE_ESTIMATOR_NAME).to(BavariaBicycleUtilityEstimator.class);
		bindUtilityEstimator(CAR_PASSENGER_ESTIMATOR_NAME).to(BavariaCarPassengerUtilityEstimator.class);
		bindUtilityEstimator(PT_ESTIMATOR_NAME).to(BavariaPtUtilityEstimator.class);
		bindUtilityEstimator(WALK_ESTIMATOR_NAME).to(BavariaWalkUtilityEstimator.class);
		bindUtilityEstimator(DRT_ESTIMATOR_NAME).to(BavariaDrtUtilityEstimator.class);

		bind(ModeParameters.class).to(BavariaModeParameters.class);

		bindTourFinder(ISOLATED_OUTSIDE_TOUR_FINDER_NAME).to(ActivityTourFinderWithExcludedActivities.class);

		bindTripConstraintFactory(FeederDrtServiceAreaConstraint.NAME).to(FeederDrtServiceAreaConstraint.Factory.class);
	}

	@Provides
	@Named("drt.serviceAreaShapeFile")
	public String provideFeederDrtServiceAreaShapeFile(Config config) {
		MultiModeDrtConfigGroup multiModeDrtConfig = (MultiModeDrtConfigGroup) config.getModules()
				.get(MultiModeDrtConfigGroup.GROUP_NAME);
		DrtConfigGroup drtConfigGroup = multiModeDrtConfig.getModalElements().stream()
				.filter(dcg -> dcg.getMode().equals("drt"))
				.findFirst()
				.orElseThrow(() -> new RuntimeException("No DRT config group found for mode 'drt'"));
		return ConfigGroup.getInputFileURL(config.getContext(), drtConfigGroup.getDrtServiceAreaShapeFile()).getPath();
	}

	@Provides
	@Named("drt")
	public CostModel provideDrtCostModel(Map<String, Provider<CostModel>> factory, EqasimConfigGroup config) {
		return getCostModel(factory, config, "drt");
	}

	@Provides
	@Singleton
	public BavariaModeAvailability provideModeAvailability(EqasimConfigGroup config) {
		return new BavariaModeAvailability(config.getAdditionalAvailableModes());
	}

	@Provides
	@Singleton
	public BavariaModeParameters provideModeChoiceParameters(EqasimConfigGroup config)
			throws IOException, ConfigurationException {
		BavariaModeParameters parameters = BavariaModeParameters.buildDefault();

		if (config.getModeParametersPath() != null) {
			ParameterDefinition.applyFile(new File(config.getModeParametersPath()), parameters);
		}

		ParameterDefinition.applyCommandLine("mode-choice-parameter", commandLine, parameters);
		return parameters;
	}

	@Provides
	@Singleton
	public BavariaCostParameters provideCostParameters(EqasimConfigGroup config) {
		BavariaCostParameters parameters = BavariaCostParameters.buildDefault();

		if (config.getCostParametersPath() != null) {
			ParameterDefinition.applyFile(new File(config.getCostParametersPath()), parameters);
		}

		ParameterDefinition.applyCommandLine("cost-parameter", commandLine, parameters);
		return parameters;
	}

	@Provides
	@Singleton
	public ActivityTourFinderWithExcludedActivities provideActivityTourFinderWithExcludedActivities(
			DiscreteModeChoiceConfigGroup dmcConfig) {
		ActivityTourFinderConfigGroup config = dmcConfig.getActivityTourFinderConfigGroup();
		return new ActivityTourFinderWithExcludedActivities(List.of("outside"),
				new ActivityTourFinder(config.getActivityTypes()));
	}
}
