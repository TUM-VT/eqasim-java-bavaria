package org.eqasim.bavaria;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
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

		// Temporary diagnostics: compare drt vs. feeder_drt candidate volume per iteration.
		// Written directly to a plain file (not via log4j) so it doesn't depend on logging configuration.
		// Header is written lazily on the first iteration-end call, since the Controler wipes the
		// output directory on startup (overwriteFiles=deleteDirectoryIfExists) - writing it any
		// earlier would risk it being deleted again before the run actually starts.
		controller.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				addControlerListenerBinding().toInstance(new IterationEndsListener() {
					private boolean headerWritten = false;

					@Override
					public void notifyIterationEnds(IterationEndsEvent event) {
						Path diagnosticsPath = Path.of(config.controller().getOutputDirectory(),
								"candidate_diagnostics.csv");

						CandidateCounter.Snapshot snapshot = CandidateCounter.snapshotAndReset();
						int plainDrtCandidates = snapshot.drtCalls() - snapshot.feederInternalDrtSegments();
						// drtCalls covers both top-level drt candidates and feeder-internal drt segments,
						// so the average below is across all drt-leg evaluations, not just plain drt trips
						double avgDrtTravelTimeMin = snapshot.drtCalls() == 0 ? 0.0
								: snapshot.drtTotalTravelTimeMin() / snapshot.drtCalls();
						double avgDrtDistanceKm = snapshot.drtCalls() == 0 ? 0.0
								: snapshot.drtTotalDistanceKm() / snapshot.drtCalls();
						double feederDrtRoutingSeconds = snapshot.feederDrtRoutingNanos() / 1e9;

						String line = String.format("%d;%d;%d;%d;%.3f;%.3f;%.3f", event.getIteration(),
								plainDrtCandidates, snapshot.feederDrtTrips(), snapshot.feederInternalDrtSegments(),
								avgDrtTravelTimeMin, avgDrtDistanceKm, feederDrtRoutingSeconds);
						log.info("[Diagnostics] " + line);

						try (PrintWriter writer = new PrintWriter(new FileWriter(diagnosticsPath.toFile(), true))) {
							if (!headerWritten) {
								writer.println(
										"iteration;drt_candidates;feeder_drt_candidates;feeder_internal_drt_segments;drt_avg_travel_time_min;drt_avg_distance_km;feeder_drt_routing_seconds");
								headerWritten = true;
							}
							writer.println(line);
						} catch (IOException e) {
							log.error("Could not write candidate diagnostics", e);
						}
					}
				});
			}
		});

		controller.run();
	}
}
