package org.eqasim.bavaria.mode_choice;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eqasim.bavaria.mode_choice.constraints.FeederDrtServiceAreaConstraint;
import org.eqasim.bavaria.mode_choice.costs.BavariaAutonomDrtCostModel;
import org.eqasim.bavaria.mode_choice.costs.BavariaCarCostModel;
import org.eqasim.bavaria.mode_choice.costs.BavariaCarRegulatedCostModel;
import org.eqasim.bavaria.mode_choice.costs.BavariaFeederDrtCostModel;
import org.eqasim.bavaria.mode_choice.costs.BavariaFreeFeederDrtCostModel;
import org.eqasim.bavaria.mode_choice.costs.BavariaDrtCostModel;
import org.eqasim.bavaria.mode_choice.costs.BavariaPtCostModel;
import org.eqasim.bavaria.mode_choice.parameters.BavariaCostParameters;
import org.eqasim.bavaria.mode_choice.parameters.BavariaModeParameters;
import org.eqasim.bavaria.mode_choice.parameters.Sc6PushCostParameters;
import org.eqasim.bavaria.mode_choice.parameters.Sc6PushModeParameters;
import org.eqasim.bavaria.mode_choice.utilities.estimators.BavariaBicycleUtilityEstimator;
import org.eqasim.bavaria.mode_choice.utilities.estimators.BavariaCarPassengerUtilityEstimator;
import org.eqasim.bavaria.mode_choice.utilities.estimators.BavariaCarRegulatedUtilityEstimator;
import org.eqasim.bavaria.mode_choice.utilities.estimators.BavariaCarUtilityEstimator;
import org.eqasim.bavaria.mode_choice.utilities.estimators.BavariaDrtUtilityEstimator;
import org.eqasim.bavaria.mode_choice.utilities.estimators.BavariaPtUtilityEstimator;
import org.eqasim.bavaria.mode_choice.utilities.estimators.BavariaWalkUtilityEstimator;
import org.eqasim.bavaria.mode_choice.utilities.predictors.BavariaCarPassengerPredictor;
import org.eqasim.bavaria.mode_choice.utilities.predictors.BavariaPersonPredictor;
import org.eqasim.bavaria.mode_choice.utilities.predictors.BavariaPtPredictor;
import org.eqasim.core.components.config.EqasimConfigGroup;
import org.eqasim.core.scenario.cutter.extent.NullScenarioExtent;
import org.eqasim.core.scenario.cutter.extent.ScenarioExtent;
import org.eqasim.core.scenario.cutter.extent.ShapeScenarioExtent;
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
	public static final String CAR_REGULATED_ESTIMATOR_NAME = "BavariaCarRegulatedUtilityEstimator";
	public static final String CAR_REGULATED_COST_MODEL_NAME = "BavariaCarRegulatedCostModel";
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
		bindCostModel(CAR_REGULATED_COST_MODEL_NAME).to(BavariaCarRegulatedCostModel.class);
		bindCostModel(PT_COST_MODEL_NAME).to(BavariaPtCostModel.class);
		bindCostModel(DRT_COST_MODEL_NAME).to(BavariaDrtCostModel.class);
		bindCostModel(FEEDER_DRT_COST_MODEL_NAME).to(BavariaFeederDrtCostModel.class);
		bindCostModel(AUTONOM_DRT_COST_MODEL_NAME).to(BavariaAutonomDrtCostModel.class);
		bindCostModel(FREE_FEEDER_DRT_COST_MODEL_NAME).to(BavariaFreeFeederDrtCostModel.class);

		bindUtilityEstimator(CAR_ESTIMATOR_NAME).to(BavariaCarUtilityEstimator.class);
		bindUtilityEstimator(CAR_REGULATED_ESTIMATOR_NAME).to(BavariaCarRegulatedUtilityEstimator.class);
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
	@Singleton
	@Named("altstadtringExtent")
	public ScenarioExtent provideAltstadtringExtent(BavariaModeParameters parameters, Config config) throws IOException {
		return loadZoneExtent(parameters.altstadtringZone, config);
	}

	@Provides
	@Singleton
	@Named("munichBoundaryExtent")
	public ScenarioExtent provideMunichBoundaryExtent(BavariaModeParameters parameters, Config config) throws IOException {
		return loadZoneExtent(parameters.munichBoundaryZone, config);
	}

	private ScenarioExtent loadZoneExtent(BavariaModeParameters.ZoneShapeParameters zone, Config config) throws IOException {
		if (zone.shapePath == null || zone.shapePath.isBlank()) {
			return new NullScenarioExtent();
		}

		Optional<String> attribute = zone.shapeAttribute == null || zone.shapeAttribute.isBlank() ? Optional.empty()
				: Optional.of(zone.shapeAttribute);
		Optional<String> value = zone.shapeValue == null || zone.shapeValue.isBlank() ? Optional.empty()
				: Optional.of(zone.shapeValue);

		// Resolved relative to the config file's directory, exactly like MATSim's own
		// file params (inputNetworkFile, drtServiceAreaShapeFile, ...) - so a scenario
		// only needs the bare filename as long as the shapefile sits next to the config.
		File shapeFile = new File(ConfigGroup.getInputFileURL(config.getContext(), zone.shapePath).getPath());
		return new ShapeScenarioExtent.Builder(shapeFile, attribute, value).build();
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
		BavariaModeParameters parameters = buildModeParametersDefault(config.getModeParametersClass());

		if (config.getModeParametersPath() != null) {
			ParameterDefinition.applyFile(new File(config.getModeParametersPath()), parameters);
		}

		ParameterDefinition.applyCommandLine("mode-choice-parameter", commandLine, parameters);
		return parameters;
	}

	/**
	 * Selects which mode-choice-parameters "profile" to build from, based on the
	 * {@code eqasim.modeParametersClass} config value. Null/unset (the default for
	 * any existing scenario config) keeps the plain baseline - this only changes
	 * behavior for configs that explicitly opt into a named profile.
	 */
	private BavariaModeParameters buildModeParametersDefault(String modeParametersClass) {
		if (modeParametersClass == null || modeParametersClass.isBlank()
				|| modeParametersClass.equals("BavariaModeParameters")) {
			return BavariaModeParameters.buildDefault();
		} else if (modeParametersClass.equals("Sc6PushModeParameters")) {
			return Sc6PushModeParameters.buildDefault();
		} else {
			throw new IllegalStateException("Unknown modeParametersClass: " + modeParametersClass);
		}
	}

	@Provides
	@Singleton
	public BavariaCostParameters provideCostParameters(EqasimConfigGroup config) {
		BavariaCostParameters parameters = buildCostParametersDefault(config.getCostParametersClass());

		if (config.getCostParametersPath() != null) {
			ParameterDefinition.applyFile(new File(config.getCostParametersPath()), parameters);
		}

		ParameterDefinition.applyCommandLine("cost-parameter", commandLine, parameters);
		return parameters;
	}

	/**
	 * Selects which cost-parameters "profile" to build from, based on the
	 * {@code eqasim.costParametersClass} config value. Null/unset (the default for
	 * any existing scenario config) keeps the plain baseline - this only changes
	 * behavior for configs that explicitly opt into a named profile.
	 */
	private BavariaCostParameters buildCostParametersDefault(String costParametersClass) {
		if (costParametersClass == null || costParametersClass.isBlank()
				|| costParametersClass.equals("BavariaCostParameters")) {
			return BavariaCostParameters.buildDefault();
		} else if (costParametersClass.equals("Sc6PushCostParameters")) {
			return Sc6PushCostParameters.buildDefault();
		} else {
			throw new IllegalStateException("Unknown costParametersClass: " + costParametersClass);
		}
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
