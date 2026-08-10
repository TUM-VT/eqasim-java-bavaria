package org.eqasim.bavaria;

import java.util.Collections;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eqasim.core.scenario.validation.VehiclesValidator;
import org.eqasim.core.simulation.mode_choice.utilities.CandidateCounter;
import org.eqasim.core.simulation.vdf.VDFConfigGroup;
import org.eqasim.bavaria.mode_choice.BavariaModeChoiceModule;
import org.eqasim.core.components.config.EqasimConfigGroup;
import org.eqasim.core.simulation.vdf.engine.VDFEngineConfigGroup;
import org.irtx.matsim_fleetpy.bridge.FleetPyModule;
import org.irtx.matsim_fleetpy.bridge.FleetPyQSimModule;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.CommandLine.ConfigurationException;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.scenario.ScenarioUtils;

public class RunSimulationWithFleetPy {
	private static final Logger log = LogManager.getLogger(RunSimulationWithFleetPy.class);

	static public void main(String[] args) throws ConfigurationException {
		CommandLine cmd = new CommandLine.Builder(args) //
				.requireOptions("config-path", "remote-port") //
				.allowPrefixes("mode-choice-parameter", "cost-parameter", "use-vdf", "use-vdf-engine") //
				.build();

		BavariaConfigurator configurator = new BavariaConfigurator(cmd);
		Config config = ConfigUtils.loadConfig(cmd.getOptionStrict("config-path"));
		configurator.updateConfig(config);

		if (cmd.getOption("use-vdf").map(Boolean::parseBoolean).orElse(false)) {
			config.qsim().setFlowCapFactor(1e9);
			config.qsim().setStorageCapFactor(1e9);

			VDFConfigGroup vdfConfig = new VDFConfigGroup();
			config.addModule(vdfConfig);

			vdfConfig.setCapacityFactor(0.5);
			vdfConfig.setModes(Set.of("car", "car_passenger"));

			if (cmd.getOption("use-vdf-engine").map(Boolean::parseBoolean).orElse(false)) {
				VDFEngineConfigGroup engineConfig = new VDFEngineConfigGroup();
				engineConfig.setModes(Set.of("car", "car_passenger"));
				engineConfig.setGenerateNetworkEvents(false);
				config.addModule(engineConfig);

				config.qsim().setMainModes(Collections.emptySet());
			}
		}

		cmd.applyConfiguration(config);
		VehiclesValidator.validate(config);

		EqasimConfigGroup eqasimConfig = EqasimConfigGroup.get(config);
		if (!eqasimConfig.getEstimators().get("walk").equals(BavariaModeChoiceModule.WALK_ESTIMATOR_NAME)) {
			throw new IllegalArgumentException(
					"Config needs to use bavariaWalk for mode choice. Please define BavariaWalkUtilityEstimator in estimators for mode walk.");
		}

		Scenario scenario = ScenarioUtils.createScenario(config);
		configurator.configureScenario(scenario);
		ScenarioUtils.loadScenario(scenario);
		configurator.adjustScenario(scenario);

		Controler controller = new Controler(scenario);
		configurator.configureController(controller);

		// Integration of FleetPy
		int remotePort = Integer.parseInt(cmd.getOptionStrict("remote-port"));
		controller.addOverridingModule(new FleetPyModule("drt", remotePort));
		controller.addOverridingQSimModule(new FleetPyQSimModule("drt"));

		// Temporary diagnostics: compare drt vs. feeder_drt candidate volume per iteration
		controller.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				addControlerListenerBinding().toInstance(new IterationEndsListener() {
					@Override
					public void notifyIterationEnds(IterationEndsEvent event) {
						int[] counts = CandidateCounter.snapshotAndReset();
						int plainDrtCandidates = counts[0] - counts[2];
						log.info(String.format(
								"[Diagnostics] Iteration %d: drt candidates = %d, feeder_drt candidates = %d (feeder-internal drt segments = %d)",
								event.getIteration(), plainDrtCandidates, counts[1], counts[2]));
					}
				});
			}
		});

		controller.run();
	}
}
