#!/bin/bash
#
# Run EQASim + FleetPy in parallel within one Slurm job.
#
#SBATCH -J sc_1_50
#SBATCH --output=sim_sc1_50_minga_%j.log
#SBATCH --error=sim_sc1_50_minga_%j.log
#SBATCH --nodes=1
#SBATCH --ntasks-per-node=1
#SBATCH --cpus-per-task=48
#SBATCH --mem=600GB
#SBATCH --time=48:00:00
#SBATCH --account=pn39mu
#SBATCH --partition=fat
#SBATCH --mail-type=BEGIN,END,FAIL
#SBATCH --mail-user=elena.natterer@tum.de
#SBATCH --get-user-env
#SBATCH --export=none

set -euo pipefail

# SuperMUC-NG: initialize module environment.
module load slurm_setup
# module load openjdk/21.0.3_9
# module load miniconda/3

#export HOME=/dss/dsshome1/06/ge49wav3
export FONTCONFIG_PATH=$HOME/test_java_font_4u/fonts
export FONTCONFIG_FILE=$HOME/test_java_font_4u/fonts/fonts.conf
export FC_FONT_PATH=$HOME/test_java_font_4u/fonts

# FleetPy python (prefer direct env python to avoid conda.sh issues).
FLEETPY_ENV="/dss/dsshome1/06/ge49wav3/.conda/envs/fleetpy_new"
FLEETPY_PYTHON="${FLEETPY_ENV}/bin/python"
if [ ! -x "${FLEETPY_PYTHON}" ]; then
  echo "ERROR: FleetPy python not found at ${FLEETPY_PYTHON}" >&2
  exit 1
fi

EQASIM_DIR="${SLURM_SUBMIT_DIR}"
# Use the shared filesystem path on the cluster for FleetPy.
FLEETPY_DIR="/hppfs/work/pn39mu/ge49wav3/mount_point_work_dir/MINGA_project/FleetPy"

PORT=6650
MATSim_CPUS=44
FLEETPY_CPUS=4
JAVA_LOG="${EQASIM_DIR}/output_config_sc1_50_drt.log"
FLEETPY_LOG="${EQASIM_DIR}/output_config_sc1_50_drt.log"

CONFIG_PATH="${EQASIM_DIR}/populations/muenchen_2024_10pct/synthetic_population_output/munich_config_with_drt_sbatch_50.xml"
OUTPUT_DIR="${EQASIM_DIR}/simulation_output/config_drt_sc1_50"
NETWORK_PATH="//hppfs/work/pn39mu/ge49wav3/mount_point_work_dir/MINGA_project/eqasim-java-bavaria/populations/muenchen_2024_10pct/synthetic_population_output/munich_network.xml.gz"
FLEETPY_CONFIG="${FLEETPY_DIR}/studies/scenarios/constant_config_minga_sc1_10pct_50.yaml"

# Run both processes inside a single Slurm step to avoid step scheduling issues.
srun --cpus-per-task=48 /bin/bash -lc "
  cd \"${EQASIM_DIR}\" &&
  echo \"[$(date -Is)] Starting MATSim\" > \"${JAVA_LOG}\" &&
  java \
    -Xms100g -Xmx500g \
    -Xlog:gc*,os=info,memops=info \
    -Djava.awt.headless=true \
    -cp bavaria/target/bavaria-2.0.0.jar \
    org.eqasim.bavaria.RunSimulationWithFleetPy \
    --config-path \"${CONFIG_PATH}\" \
    --config:global.numberOfThreads ${MATSim_CPUS} \
    --config:controller.outputDirectory \"${OUTPUT_DIR}\" \
    --use-vdf True \
    --remote-port ${PORT} \
    >> \"${JAVA_LOG}\" 2>&1 &
  # Keep MATSim running in background; we wait for all jobs below.

  # Wait until the socket is reachable before starting FleetPy.
  # Default: enforce an 8-minute minimum wait, then wait up to 15 minutes total.
  MIN_WAIT_SECONDS=480
  WAIT_INTERVAL_SECONDS=5
  MAX_WAIT_SECONDS=900
  if [ \"\${MIN_WAIT_SECONDS}\" -gt 0 ]; then
    echo \"[$(date -Is)] Waiting \${MIN_WAIT_SECONDS}s before checking MATSim socket...\" >&2
    sleep \"\${MIN_WAIT_SECONDS}\"
  fi
  elapsed=0
  while [ \"\${elapsed}\" -lt \"\${MAX_WAIT_SECONDS}\" ]; do
    if (echo > /dev/tcp/127.0.0.1/${PORT}) >/dev/null 2>&1; then
      break
    fi
    if [ \"\$((elapsed % 60))\" -eq 0 ]; then
      echo \"[$(date -Is)] Waiting for MATSim socket on port ${PORT} (\${elapsed}s elapsed)...\" >&2
    fi
    sleep \"\${WAIT_INTERVAL_SECONDS}\"
    elapsed=\$((elapsed + WAIT_INTERVAL_SECONDS))
  done

  if ! (echo > /dev/tcp/127.0.0.1/${PORT}) >/dev/null 2>&1; then
    echo \"WARNING: MATSim socket not reachable on port ${PORT} after \${MAX_WAIT_SECONDS}s. Starting FleetPy anyway...\" >&2
  fi

  if [ ! -d \"${FLEETPY_DIR}\" ]; then
    echo \"ERROR: FleetPy directory not found: ${FLEETPY_DIR}\" >&2
    exit 1
  fi
  if [ ! -f \"${FLEETPY_CONFIG}\" ]; then
    echo \"ERROR: FleetPy config not found: ${FLEETPY_CONFIG}\" >&2
    exit 1
  fi
  if [ ! -x \"${FLEETPY_PYTHON}\" ]; then
    echo \"ERROR: FleetPy python not executable: ${FLEETPY_PYTHON}\" >&2
    exit 1
  fi

  cd \"${FLEETPY_DIR}\" &&
  echo \"[$(date -Is)] Launching FleetPy\" >&2 &&
  echo \"[$(date -Is)] Starting FleetPy\" > \"${FLEETPY_LOG}\" &&
  export OMP_NUM_THREADS=${FLEETPY_CPUS} &&
  export OPENBLAS_NUM_THREADS=${FLEETPY_CPUS} &&
  export MKL_NUM_THREADS=${FLEETPY_CPUS} &&
  \"${FLEETPY_PYTHON}\" src/coupling/MATSimEqasim/MATSimSocket.py \
    \"${FLEETPY_CONFIG}\" \
    \"${NETWORK_PATH}\" \
    ${PORT} \
    >> \"${FLEETPY_LOG}\" 2>&1 &
  # Wait for all background jobs in this shell.
  wait
"
