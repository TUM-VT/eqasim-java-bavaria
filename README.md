
# How to run a MATSim simulation

## 🚀 Running the MATSim Simulation

This repo on the LRZ gitlab uses a pre-configured setup from eqasim's simulation repository.

## ✅ Prerequisites

1. Make sure you’ve completed all steps for generating the synthetic population. You find examples for the population synthesis output files on the internal drives: [\\10.152.34.30\Austausch\MATSim Populations]](\\10.152.34.30\Austausch\MATSim Populations)
2. Additional configuration details are available here:  
👉 [https://github.com/eqasim-org/bavaria/blob/development-minga/docs/simulation.md](https://github.com/eqasim-org/bavaria/blob/development-minga/docs/simulation.md)

---

### 1️⃣ Clone the Simulation Repository

Use this repository and branch:

- **Repository:**  
  🔗 [https://gitlab.lrz.de/tum-vt/minga-eqasim-simulation](https://gitlab.lrz.de/tum-vt/minga-eqasim-simulation)

- **Branch:** `bavaria-mode-choice`  
  🔗 [https://gitlab.lrz.de/tum-vt/minga-eqasim-simulation/tree/bavaria-mode-choice](https://gitlab.lrz.de/tum-vt/minga-eqasim-simulation/tree/bavaria-mode-choice)

Clone and check out the correct branch:

```bash
git clone https://gitlab.lrz.de/tum-vt/minga-eqasim-simulation.git
cd eqasim-java
git checkout bavaria-mode-choice
```

---

### 2️⃣ (Optional) Create Your Own Branch Based on `bavaria-mode-choice`

If you want to make changes or run your own experiments:

```bash
git checkout -b my-example-experiment
git push -u origin my-example-experiment
```

This creates your own branch based on `bavaria-mode-choice` and sets up tracking so that future pushes and pulls work seamlessly.

---

### 3️⃣ Prepare the `data` Folder

In the `bavaria` module, create a `data` folder, containing the folder `munich` and copy your synthetic population and network files there:

```bash
mkdir -p bavaria/data/munich
```
Copy into this folder all files created in the synthetic population, such as the config, network file, etc. Only xml files are required! csv files are outputs of the MATSim simulation and are not used as inputs for the simulation.

Ensure that you use a `config.xml` in which the eqasim:raptor parameters are set as follows:
```
	<module name="eqasim:raptor" >
		<param name="perTransfer_u" value="-0.5441109013512305" /> <!-- IdF: -0.5441109013512305 -->
		<param name="travelTimeBus_u_h" value="-2.162021" /> <!-- IdF: -2.835025304050246 -->
		<param name="travelTimeOther_u_h" value="-2.162021" /> <!-- IdF: -2.835025304050246 -->
		<param name="travelTimeRail_u_h" value="-2.162021" /> <!-- IdF: -1.4278139352278472 -->
		<param name="travelTimeSubway_u_h" value="-2.162021" /> <!-- IdF: -1.0 -->
		<param name="travelTimeTram_u_h" value="-2.162021" /> <!-- IdF:  -3.199594607188756 -->
		<param name="waitTime_u_h" value="-24.67436" /> <!-- IdF: -0.497984826174775 -->
		<param name="walkTime_u_h" value="-4.172657" /> <!-- IdF: -3.8494071051697385 -->
	</module>
```
You can use the config file that is used for calibration: `config_munich_calibrated.xml` from this repository.

---

### 4️⃣ Run the Simulation

First, build the project:
```bash
mvn clean package -Pstandalone --projects bavaria --also-make -DskipTests=true
```

This will generate a `bavaria-1.5.0.jar` file in the `bavaria/target` directory.

To run a single simulation, use:

```bash
nohup java -Xmx12G -cp bavaria/target/bavaria-1.5.0.jar org.eqasim.bavaria.RunSimulation --config-path bavaria/data/munich_0.1/munich_config.xml &> simulation_output.log &
```
If you are running the simulation on a cluster or machine with larger memory resources, consider increasing the Java heap space, e.g., by setting -Xmx120G.

If you want to run simulations for multiple random seeds, use the `RunSimulationsMultipleSeeds`, where you can specify the number of seeds, threads, and memory:
```bash
nohup java -cp bavaria/target/bavaria-1.5.0.jar org.eqasim.bavaria.RunSimulationsMultipleSeeds --seeds 3 --threads 12 --memory 60 > output.log 2>&1 &
``` 

This command works for all downsampled population sizes.  
There’s no need to change the command based on population size — just ensure the corresponding population file is correctly referenced in the config.

If you want to run MATSim on the LRZ cluster (e.g., cm4_tiny), you need to ask Peter for an account. After setting up your account, you can do the following steps to run the simulation:
1. Go to Windows PowerShell (or similar) and use the command `ssh <your_user_name>@cool.hpc.lrz.de` to login.
2. Use the command `sbatch slurm_matsim_run_example.txt`. This file is part of this repo and can be copied.
3. `squeue -M cm4` tells you if your job is queued.
There are some restrictions on the LRZ cluster like the maximum of submitted jobs (currently 4), number of kernels etc. Find details to the LRZ cluster here: [https://doku.lrz.de/linux-cluster-10745672.html](https://doku.lrz.de/linux-cluster-10745672.html).

In the `slurm_matsim_run_example.txt`file, there are some arguments for the mode choice parameter setting. These are **not** required since they are already correctly set in the `BavariaModeParameters.java` file.



# eqasim-java [from the original readme]

![eqasim](docs/top.png "eqasim")

The `eqasim` package is a collection of ready-to-run high-quality scenarios
for the agent- and activity-based transport simulation framework [MATSim](https://matsim.org/).
This repository contains the Java parts of the project which extend MATSim and
provide functionality to generate the simulation data.

Therefore, this repository has two functions: First, to provide the code to run
`eqasim` simulations (and extend them), second, to act as a dependency for the
various pipelines that generate runnable MATSim scenarios from raw data sets,
such as for:

- [Île-de-France and Paris](https://github.com/eqasim-org/ile-de-france)
- [California](https://github.com/eqasim-org/california)
- [Sao Paulo](https://github.com/eqasim-org/sao_paulo)

To understand how to set up a simulation and run it, please refer to the
respective repositories. 

Additional topics:
- How to [cut out smaller parts of existing simulations](docs/cutting.md).
- How to [run a simulation with on-demand mobility services](docs/on_demand_mobility.md) (as a main mode and as a transit feeder).
- How to [run the discrete mode choice model as a standalone](docs/standalone_mode_choice.md)
- How to [use Volume Delay Functions for the network simulation](docs/vdf.md)

## Main reference

The main research reference for the eqasim-java framework:
> Hörl, S. and M. Balac (2021) [Introducing the eqasim pipeline: From raw data to agent-based transport simulation](https://www.researchgate.net/publication/351676356_Introducing_the_eqasim_pipeline_From_raw_data_to_agent-based_transport_simulation), _Procedia Computer Science_, 184, 712-719.

## Versioning and Packging

[![Build Status](https://travis-ci.com/eqasim-org/eqasim-java.svg?branch=develop)](https://travis-ci.com/eqasim-org/eqasim-java)

The current version of `eqasim` is `1.5.0` and is based on MATSim `15.0`. You can access it through the `v1.5.0` tag. The
`develop` branch is kept at version `1.5.0` until the next release is prepared,
but may include additional developments since the last release.

The code is available as a Maven package. To use it, add the following repository
to your `pom.xml`:

```xml
<repository>
    <id>eqasim</id>
    <url>https://packagecloud.io/eth-ivt/eqasim/maven2</url>
</repository>
```

Afterwards, you can add various sub-packages to your project:

```xml
<dependency>
    <groupId>org.eqasim</groupId>
    <artifactId>core</artifactId>
    <version>1.5.0</version>
</dependency>
```

Besides the latest release based on MATSim 15.0, legacy versions `1.3.1`, `1.2.1`, `1.2.0`, `1.0.6`, and `1.0.5` are also available through packagecloud.

## Upstream branch

To keep scenario-based repositories up-to-date (for instance, [ile-de-france](https://github.com/eqasim-org/ile-de-france)), we provide the `upstream` branch, which contains a well-defined `develop` version of `eqasim-java` and is used in the `develop` version of the dependent repository. While this is useful for development purposes, their versioned releases will always depend on versioned releases of `eqasim-java`.
